package com.auditlab.mod;

import com.auditlab.mod.config.AuditCommand;
import com.auditlab.mod.config.AuditKeyBindings;
import com.auditlab.mod.config.ConfigManager;
import com.auditlab.mod.events.AuditEventListener;
import com.auditlab.mod.events.ChunkSnapshotter;
import com.auditlab.mod.export.SessionManager;
import com.auditlab.mod.render.WorldOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Wires config, session tracking, the event listener, the chunk snapshotter
 * and the overlay renderer.
 *
 * <p>AuditLab is passive. It reads packets the client already received and never sends packets,
 * moves the player or interacts with the world. Collection only runs in singleplayer and on
 * servers listed in {@code allowedServers}.
 */
public final class AuditLabMod implements ClientModInitializer {
    public static final String MOD_ID = "auditlab";
    public static final Logger LOG = LoggerFactory.getLogger("AuditLab");

    @Override
    public void onInitializeClient() {
        ConfigManager configs = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
        configs.load();
        if (configs.lastError() != null) {
            LOG.warn("Could not read {} ({}); using defaults and leaving the file untouched", configs.file(), configs.lastError());
        }

        SessionManager sessions = new SessionManager();
        ChunkSnapshotter snapshotter = new ChunkSnapshotter(sessions, configs::get);

        AuditEventListener listener = new AuditEventListener(sessions, configs, snapshotter);
        listener.register();
        new WorldOverlayRenderer(sessions, configs::get).register();
        AuditKeyBindings.register(configs, sessions);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            AuditCommand.register(dispatcher, configs, sessions, listener));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            sessions.end(System.currentTimeMillis());
            snapshotter.close();
        });

        LOG.info("AuditLab initialised (passive mode, {} allowed server(s))", configs.get().allowedServers.size());
    }
}
