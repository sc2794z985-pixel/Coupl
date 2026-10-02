package com.auditlab.mod.analysis;

/** Test helpers for building chunk volumes. */
final class Volumes {
    static final int MIN_Y = -64;
    static final int HEIGHT = 384;

    private Volumes() {
    }

    /** Solid from the bottom of the world up to and including {@code surfaceY}, air above. */
    static ChunkVolume solidUpTo(int chunkX, int chunkZ, int surfaceY) {
        ChunkVolume v = new ChunkVolume(chunkX, chunkZ, MIN_Y, HEIGHT);
        for (int y = MIN_Y; y <= surfaceY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) v.set(x, y, z, ChunkVolume.SOLID);
            }
        }
        return v;
    }

    /** Sets an inclusive local-x/z, world-y box to {@code cell}. */
    static void fill(ChunkVolume v, int x1, int y1, int z1, int x2, int y2, int z2, byte cell) {
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) v.set(x, y, z, cell);
            }
        }
    }
}
