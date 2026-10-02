package com.anticheatlab.model;

/** Banding of the 0-100 score, used for list colouring now and world overlays in Phase 2. */
public enum Severity {
    CLEAN(0, 120, 120, 120),
    LOW(1, 90, 200, 90),
    MEDIUM(25, 230, 200, 60),
    HIGH(50, 240, 130, 40),
    CRITICAL(75, 230, 40, 40);

    private final int minScore;
    private final int red;
    private final int green;
    private final int blue;

    Severity(int minScore, int red, int green, int blue) {
        this.minScore = minScore;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public static Severity of(int score) {
        Severity result = CLEAN;
        for (Severity s : values()) {
            if (score >= s.minScore) result = s;
        }
        return result;
    }

    public int minScore() {
        return minScore;
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }
}
