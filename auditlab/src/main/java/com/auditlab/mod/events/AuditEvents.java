package com.auditlab.mod.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Fabric events for inbound packets that Fabric API does not already expose. Fired by
 * {@code ClientPlayNetworkHandlerMixin} on the client thread, after vanilla has applied the
 * packet. Listeners must be read-only: they observe, never send or modify anything.
 */
public final class AuditEvents {
    public static final Event<BlockChanged> BLOCK_CHANGED = EventFactory.createArrayBacked(BlockChanged.class,
        listeners -> (world, pos, state) -> {
            for (BlockChanged l : listeners) l.onBlockChanged(world, pos, state);
        });

    public static final Event<BlockEntityData> BLOCK_ENTITY_DATA = EventFactory.createArrayBacked(BlockEntityData.class,
        listeners -> (world, pos, type) -> {
            for (BlockEntityData l : listeners) l.onBlockEntityData(world, pos, type);
        });

    public static final Event<BlockEventReceived> BLOCK_EVENT = EventFactory.createArrayBacked(BlockEventReceived.class,
        listeners -> (world, pos, block, type, data) -> {
            for (BlockEventReceived l : listeners) l.onBlockEvent(world, pos, block, type, data);
        });

    public static final Event<WorldEventReceived> WORLD_EVENT = EventFactory.createArrayBacked(WorldEventReceived.class,
        listeners -> (world, pos, eventId, global) -> {
            for (WorldEventReceived l : listeners) l.onWorldEvent(world, pos, eventId, global);
        });

    public static final Event<SoundPlayed> SOUND = EventFactory.createArrayBacked(SoundPlayed.class,
        listeners -> (world, x, y, z, soundId, category) -> {
            for (SoundPlayed l : listeners) l.onSound(world, x, y, z, soundId, category);
        });

    public static final Event<ParticleSpawned> PARTICLE = EventFactory.createArrayBacked(ParticleSpawned.class,
        listeners -> (world, x, y, z, type) -> {
            for (ParticleSpawned l : listeners) l.onParticle(world, x, y, z, type);
        });

    public static final Event<EntitySpawned> ENTITY_SPAWN = EventFactory.createArrayBacked(EntitySpawned.class,
        listeners -> (world, x, y, z, type) -> {
            for (EntitySpawned l : listeners) l.onEntitySpawn(world, x, y, z, type);
        });

    private AuditEvents() {
    }

    /** {@code BlockUpdateS2CPacket} and each entry of {@code ChunkDeltaUpdateS2CPacket}. */
    @FunctionalInterface
    public interface BlockChanged {
        void onBlockChanged(ClientWorld world, BlockPos pos, BlockState state);
    }

    /** {@code BlockEntityUpdateS2CPacket}. */
    @FunctionalInterface
    public interface BlockEntityData {
        void onBlockEntityData(ClientWorld world, BlockPos pos, BlockEntityType<?> type);
    }

    /** {@code BlockEventS2CPacket} (chest lids, pistons, note blocks, bells, ...). */
    @FunctionalInterface
    public interface BlockEventReceived {
        void onBlockEvent(ClientWorld world, BlockPos pos, Block block, int type, int data);
    }

    /** {@code WorldEventS2CPacket} (block break effects, doors, dispensers, ...). */
    @FunctionalInterface
    public interface WorldEventReceived {
        void onWorldEvent(ClientWorld world, BlockPos pos, int eventId, boolean global);
    }

    /** {@code PlaySoundS2CPacket} (positional sounds). */
    @FunctionalInterface
    public interface SoundPlayed {
        void onSound(ClientWorld world, double x, double y, double z, Identifier soundId, SoundCategory category);
    }

    /** {@code ParticleS2CPacket}. */
    @FunctionalInterface
    public interface ParticleSpawned {
        void onParticle(ClientWorld world, double x, double y, double z, ParticleType<?> type);
    }

    /** {@code EntitySpawnS2CPacket}. */
    @FunctionalInterface
    public interface EntitySpawned {
        void onEntitySpawn(ClientWorld world, double x, double y, double z, EntityType<?> type);
    }
}
