package com.auditlab.mod.analysis.model;

import java.util.Objects;

/**
 * One line of a score breakdown, e.g. {@code +15 Artificial Geometry (2 rooms, 1 corridor)}.
 *
 * @param points capped, rounded points this category contributed to the total
 * @param raw    uncapped points before rounding
 */
public record ScoreReason(ScoreCategory category, int points, double raw, String detail) {
    public ScoreReason {
        Objects.requireNonNull(category, "category");
        detail = detail == null ? "" : detail;
    }

    public boolean capped() {
        return raw > points + 0.5;
    }

    @Override
    public String toString() {
        String base = "+" + points + " " + category.label();
        return detail.isEmpty() ? base : base + " (" + detail + ")";
    }
}
