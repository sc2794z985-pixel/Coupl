package com.auditlab.mod.analysis;

/**
 * Dense per-cell classification of one chunk column, built from a chunk snapshot. Three classes
 * are enough for geometry analysis: {@link #OPEN} (air or anything a player walks through, such as
 * torches), {@link #SOLID} and {@link #FLUID}. Y arguments are world coordinates; X/Z are local
 * (0..15).
 */
public final class ChunkVolume {
    public static final int SIZE = 16;
    public static final byte OPEN = 0;
    public static final byte SOLID = 1;
    public static final byte FLUID = 2;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final byte[] cells;
    private int[] surface;

    /** All cells start as {@link #OPEN}. */
    public ChunkVolume(int chunkX, int chunkZ, int minY, int height) {
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.height = height;
        this.cells = new byte[SIZE * SIZE * height];
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public int maxY() {
        return minY + height - 1;
    }

    public int originX() {
        return chunkX << 4;
    }

    public int originZ() {
        return chunkZ << 4;
    }

    public byte get(int localX, int worldY, int localZ) {
        return cells[index(localX, worldY - minY, localZ)];
    }

    public void set(int localX, int worldY, int localZ, byte cell) {
        cells[index(localX, worldY - minY, localZ)] = cell;
        surface = null;
    }

    /** Highest world Y in the column that is not {@link #OPEN}, or {@code minY - 1} if none. */
    public int surfaceY(int localX, int localZ) {
        if (surface == null) surface = computeSurface();
        return surface[localZ * SIZE + localX];
    }

    /** Raw access by local Y for the analyzers (0 = bottom of the volume). */
    byte getLocal(int localX, int localY, int localZ) {
        return cells[index(localX, localY, localZ)];
    }

    static int index(int localX, int localY, int localZ) {
        return (localY * SIZE + localZ) * SIZE + localX;
    }

    private int[] computeSurface() {
        int[] s = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = minY - 1;
                for (int y = height - 1; y >= 0; y--) {
                    if (cells[index(x, y, z)] != OPEN) {
                        top = minY + y;
                        break;
                    }
                }
                s[z * SIZE + x] = top;
            }
        }
        return s;
    }
}
