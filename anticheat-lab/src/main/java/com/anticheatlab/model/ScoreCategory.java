package com.anticheatlab.model;

/**
 * Top-level buckets of the Chunk Suspicion Score. Default caps sum to exactly 100, so a
 * chunk that saturates every category reaches the maximum score.
 */
public enum ScoreCategory {
    SUSPICIOUS_BLOCKS("Suspicious blocks", 30),
    BLOCK_ENTITIES("Block entities", 25),
    CAVITY_GEOMETRY("Cavity geometry", 20),
    UPDATE_ANOMALIES("Update anomalies", 15),
    SOUNDS("Sounds", 10);

    private final String displayName;
    private final int defaultCap;

    ScoreCategory(String displayName, int defaultCap) {
        this.displayName = displayName;
        this.defaultCap = defaultCap;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultCap() {
        return defaultCap;
    }
}
