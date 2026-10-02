package com.anticheatlab.model;

import java.util.Objects;

/**
 * One piece of potentially leaked information, produced by a scanner from a packet the client
 * legitimately received. Immutable.
 *
 * @param detail free-form context for the breakdown view, e.g. the block or sound id
 */
public record LeakEvent(LeakType type, String dimension, int x, int y, int z, long timestampMillis, String detail) {
    public LeakEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dimension, "dimension");
        detail = detail == null ? "" : detail;
    }

    public static LeakEvent now(LeakType type, String dimension, int x, int y, int z, String detail) {
        return new LeakEvent(type, dimension, x, y, z, System.currentTimeMillis(), detail);
    }

    public ChunkCoord chunk() {
        return ChunkCoord.ofBlock(dimension, x, z);
    }

    /** Packs the block position into a long using the same 26/12/26-bit layout as vanilla. */
    public long packedPos() {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    @Override
    public String toString() {
        String base = type + " @ " + x + " " + y + " " + z;
        return detail.isEmpty() ? base : base + " (" + detail + ")";
    }
}
