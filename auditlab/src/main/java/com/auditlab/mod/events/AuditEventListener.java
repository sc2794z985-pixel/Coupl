package com.auditlab.mod.events;

import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.analysis.model.ObservedEvent;
import com.auditlab.mod.config.AuditLabConfig;
import com.auditlab.mod.config.ConfigManager;
import com.auditlab.mod.export.AuditSession;
import com.auditlab.mod.export.SessionManager;
import com.auditlab.mod.safety.AccessPolicy;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Central listener. Registers every callback the mod uses and routes them into the running
 * {@link AuditSession}. Session lifecycle: started on join if {@link AccessPolicy} allows the
 * target, ended on disconnect. With no session, every callback is a no-op.
 */
public final class AuditEventListener {
    private final SessionManager sessions;
    private final ConfigManager configs;
    private final ChunkSnapshotter snapshotter;

    public AuditEventListener(SessionManager sessions, ConfigManager configs, ChunkSnapshotter snapshotter) {
        this.sessions = sessions;
        this.configs = configs;
        this.snapshotter = snapshotter;
    }

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(handler, client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sessions.end(System.currentTimeMillis()));

        ClientChunkEvents.CHUNK_LOAD.register(snapshotter::onChunkLoad);
        ClientTickEvents.END_CLIENT_TICK.register(snapshotter::tick);

        AuditEvents.BLOCK_CHANGED.register((world, pos, state) -> {
            if (record(world, EventType.BLOCK_CHANGE, pos, McIds.block(state.getBlock())) && world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                snapshotter.requestRescan(ChunkKey.ofBlock(McIds.dimension(world), pos.getX(), pos.getZ()));
            }
        });
        AuditEvents.BLOCK_ENTITY_DATA.register((world, pos, type) ->
            record(world, EventType.BLOCK_ENTITY_DATA, pos, McIds.blockEntity(type)));
        AuditEvents.BLOCK_EVENT.register((world, pos, block, type, data) ->
            record(world, EventType.BLOCK_EVENT, pos, McIds.block(block)));
        AuditEvents.WORLD_EVENT.register((world, pos, eventId, global) -> {
            if (!global) record(world, EventType.WORLD_EVENT, pos, "world_event:" + eventId);
        });
        AuditEvents.SOUND.register((world, x, y, z, soundId, category) -> {
            if (configs.get().trackedSoundCategories.contains(category.name())) {
                record(world, EventType.SOUND, x, y, z, soundId.toString());
            }
        });
        AuditEvents.PARTICLE.register((world, x, y, z, type) ->
            record(world, EventType.PARTICLE, x, y, z, McIds.particle(type)));
        AuditEvents.ENTITY_SPAWN.register((world, x, y, z, type) ->
            record(world, EventType.ENTITY_SPAWN, x, y, z, McIds.entity(type)));
    }

    private void onJoin(ClientPlayNetworkHandler handler, MinecraftClient client) {
        AuditLabConfig cfg = configs.get();
        boolean singleplayer = client.isInSingleplayer();
        ServerInfo info = handler.getServerInfo();
        String address = info == null ? null : info.address;
        String target = singleplayer ? "singleplayer" : String.valueOf(address);

        if (AccessPolicy.isAuthorized(singleplayer, address, cfg.allowedServers)) {
            sessions.start(target, cfg, System.currentTimeMillis());
            notify(client, Text.literal("[AuditLab] Passive audit session started on " + target + ".").formatted(Formatting.AQUA));
        } else {
            sessions.end(System.currentTimeMillis());
            notify(client, Text.literal("[AuditLab] Not collecting: " + target
                + " is not in allowedServers (config/auditlab.json).").formatted(Formatting.GRAY));
        }
    }

    private boolean record(ClientWorld world, EventType type, BlockPos pos, String subject) {
        return record(world, type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, subject);
    }

    /** @return whether the event was recorded */
    private boolean record(ClientWorld world, EventType type, double x, double y, double z, String subject) {
        AuditSession session = sessions.current();
        if (session == null) return false;
        ExposureContext context = ExposureClassifier.classify(world, x, y, z, configs.get());
        if (context == null) return false;
        ObservedEvent event = new ObservedEvent(type, context, MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z),
            System.currentTimeMillis(), subject);
        session.analyzer().recordEvent(McIds.dimension(world), event);
        return true;
    }

    private static void notify(MinecraftClient client, Text message) {
        if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(message);
    }
}
