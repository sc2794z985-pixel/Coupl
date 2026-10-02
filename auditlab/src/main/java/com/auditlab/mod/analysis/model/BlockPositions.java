package com.auditlab.mod.analysis.model;

/** Block position packing with the same 26/12/26-bit layout as vanilla {@code BlockPos.asLong}. */
public final class BlockPositions {
    private BlockPositions() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
