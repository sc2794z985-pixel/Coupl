package com.anticheatlab.model;

import java.util.Objects;

/**
 * Dimension-qualified chunk coordinate. Kept free of Minecraft types so the model,
 * scoring and storage layers can be unit-tested without a game runtime.
 *
 * @param dimension registry id of the dimension, e.g. {@code minecraft:overworld}
 */
public record ChunkCoord(String dimension, int x, int z) {
    public ChunkCoord {
        Objects.requireNonNull(dimension, "dimension");
    }

    public static ChunkCoord ofBlock(String dimension, int blockX, int blockZ) {
        return new ChunkCoord(dimension, blockX >> 4, blockZ >> 4);
    }

    public int minBlockX() {
        return x << 4;
    }

    public int minBlockZ() {
        return z << 4;
    }

    @Override
    public String toString() {
        return dimension + "[" + x + ", " + z + "]";
    }
}
