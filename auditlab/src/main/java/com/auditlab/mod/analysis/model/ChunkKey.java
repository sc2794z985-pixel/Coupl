package com.auditlab.mod.analysis.model;

import java.util.Objects;

/**
 * Dimension-qualified chunk coordinate. Equivalent to a vanilla {@code ChunkPos} plus the
 * dimension id; {@link #packedPos()} uses the same layout as {@code ChunkPos.toLong()}.
 * Kept free of Minecraft types so the analysis layer runs in plain unit tests.
 */
public record ChunkKey(String dimension, int x, int z) {
    public ChunkKey {
        Objects.requireNonNull(dimension, "dimension");
    }

    public static ChunkKey ofBlock(String dimension, int blockX, int blockZ) {
        return new ChunkKey(dimension, blockX >> 4, blockZ >> 4);
    }

    public long packedPos() {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    public int minBlockX() {
        return x << 4;
    }

    public int minBlockZ() {
        return z << 4;
    }

    /** Chebyshev distance in chunks; {@link Integer#MAX_VALUE} across dimensions. */
    public int chunkDistance(ChunkKey other) {
        if (!dimension.equals(other.dimension)) return Integer.MAX_VALUE;
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }

    @Override
    public String toString() {
        return dimension + "[" + x + ", " + z + "]";
    }
}
