package com.auditlab.mod.events;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Registry id strings for the analysis layer, which never sees Minecraft types. */
public final class McIds {
    private McIds() {
    }

    public static String dimension(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    public static String block(Block block) {
        return str(Registries.BLOCK.getId(block));
    }

    public static String blockEntity(BlockEntityType<?> type) {
        return str(Registries.BLOCK_ENTITY_TYPE.getId(type));
    }

    public static String entity(EntityType<?> type) {
        return str(Registries.ENTITY_TYPE.getId(type));
    }

    public static String particle(ParticleType<?> type) {
        return str(Registries.PARTICLE_TYPE.getId(type));
    }

    private static String str(Identifier id) {
        return id == null ? "unknown" : id.toString();
    }
}
