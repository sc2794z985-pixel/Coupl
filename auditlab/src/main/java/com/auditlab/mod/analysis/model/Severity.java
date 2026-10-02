package com.auditlab.mod.analysis.model;

/** Score bands used for overlay colour: green, yellow, red. NONE is not rendered. */
public enum Severity {
    NONE(0x808080),
    LOW(0x3CC83C),
    MEDIUM(0xE6C83C),
    HIGH(0xE63C3C);

    private final int rgb;

    Severity(int rgb) {
        this.rgb = rgb;
    }

    /**
     * @param mediumThreshold lowest score that counts as MEDIUM
     * @param highThreshold   lowest score that counts as HIGH
     */
    public static Severity of(int score, int mediumThreshold, int highThreshold) {
        if (score <= 0) return NONE;
        if (score >= highThreshold) return HIGH;
        if (score >= mediumThreshold) return MEDIUM;
        return LOW;
    }

    public int rgb() {
        return rgb;
    }

    public int argb(int alpha) {
        return (alpha & 0xFF) << 24 | rgb;
    }

    public float red() {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (rgb & 0xFF) / 255f;
    }
}
