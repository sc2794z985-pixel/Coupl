package com.auditlab.mod.analysis.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable computed score for one chunk: a 0-100 total, its severity band, and the reasons
 * (one per contributing category, highest first) that explain every point.
 */
public record ChunkScore(ChunkKey key, int total, Severity severity, Map<ScoreCategory, Integer> categoryPoints,
                         List<ScoreReason> reasons, long computedAtMillis) {
    public ChunkScore {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(severity, "severity");
        if (total < 0 || total > 100) throw new IllegalArgumentException("total out of range: " + total);
        EnumMap<ScoreCategory, Integer> points = new EnumMap<>(ScoreCategory.class);
        points.putAll(categoryPoints);
        categoryPoints = Collections.unmodifiableMap(points);
        reasons = List.copyOf(reasons);
    }

    public int points(ScoreCategory category) {
        return categoryPoints.getOrDefault(category, 0);
    }

    public List<ScoreReason> topReasons(int limit) {
        return reasons.subList(0, Math.min(Math.max(0, limit), reasons.size()));
    }
}
