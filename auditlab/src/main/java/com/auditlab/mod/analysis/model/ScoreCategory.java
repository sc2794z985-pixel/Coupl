package com.auditlab.mod.analysis.model;

/** Score buckets. Each has a configurable cap; the default caps sum to 100. */
public enum ScoreCategory {
    BLOCK_DENSITY("Tracked Blocks", 30),
    BLOCK_ENTITIES("Unexpected BlockEntity", 25),
    GEOMETRY("Artificial Geometry", 25),
    DYNAMIC_EVENTS("Hidden Activity", 20);

    private final String label;
    private final int defaultCap;

    ScoreCategory(String label, int defaultCap) {
        this.label = label;
        this.defaultCap = defaultCap;
    }

    public String label() {
        return label;
    }

    public int defaultCap() {
        return defaultCap;
    }
}
