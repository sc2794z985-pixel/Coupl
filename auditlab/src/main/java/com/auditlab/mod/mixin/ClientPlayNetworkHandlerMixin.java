package com.auditlab.mod.mixin;

import com.auditlab.mod.events.AuditEvents;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only taps on inbound packet handlers.
 *
 * <p>Each vanilla handler starts with {@code NetworkThreadUtils.forceMainThread}, which throws on
 * the netty thread and re-queues the packet on the client thread. Injecting at {@code RETURN}
 * therefore runs exactly once, on the client thread, after vanilla has applied the packet. The
 * injections never cancel, modify or send anything.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Shadow
    public abstract ClientWorld getWorld();

    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    private void auditlab$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) AuditEvents.BLOCK_CHANGED.invoker().onBlockChanged(world, packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("RETURN"))
    private void auditlab$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world == null) return;
        // visitUpdates reuses one mutable BlockPos, so copy it for listeners.
        packet.visitUpdates((pos, state) -> AuditEvents.BLOCK_CHANGED.invoker().onBlockChanged(world, pos.toImmutable(), state));
    }

    @Inject(method = "onBlockEntityUpdate", at = @At("RETURN"))
    private void auditlab$onBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) AuditEvents.BLOCK_ENTITY_DATA.invoker().onBlockEntityData(world, packet.getPos(), packet.getBlockEntityType());
    }

    @Inject(method = "onBlockEvent", at = @At("RETURN"))
    private void auditlab$onBlockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) {
            AuditEvents.BLOCK_EVENT.invoker().onBlockEvent(world, packet.getPos(), packet.getBlock(), packet.getType(), packet.getData());
        }
    }

    @Inject(method = "onWorldEvent", at = @At("RETURN"))
    private void auditlab$onWorldEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) AuditEvents.WORLD_EVENT.invoker().onWorldEvent(world, packet.getPos(), packet.getEventId(), packet.isGlobal());
    }

    @Inject(method = "onPlaySound", at = @At("RETURN"))
    private void auditlab$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) {
            AuditEvents.SOUND.invoker().onSound(world, packet.getX(), packet.getY(), packet.getZ(),
                packet.getSound().value().id(), packet.getCategory());
        }
    }

    @Inject(method = "onParticle", at = @At("RETURN"))
    private void auditlab$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) {
            AuditEvents.PARTICLE.invoker().onParticle(world, packet.getX(), packet.getY(), packet.getZ(), packet.getParameters().getType());
        }
    }

    @Inject(method = "onEntitySpawn", at = @At("RETURN"))
    private void auditlab$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = getWorld();
        if (world != null) {
            AuditEvents.ENTITY_SPAWN.invoker().onEntitySpawn(world, packet.getX(), packet.getY(), packet.getZ(), packet.getEntityType());
        }
    }
}
