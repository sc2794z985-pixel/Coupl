package com.anticheatlab.scoring;

import com.anticheatlab.model.ChunkObservation;
import com.anticheatlab.model.ChunkScore;
import com.anticheatlab.model.ChunkScore.CategoryScore;
import com.anticheatlab.model.LeakType;
import com.anticheatlab.model.ScoreCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Turns an observation into a {@link ChunkScore}.
 *
 * <p>Model: for each category, {@code raw = sum(count(type) * weight(type))} over its leak types,
 * {@code points = min(raw, cap)}. The total is the rounded sum of category points, clamped to
 * 0..100. Linear-with-cap keeps every point traceable to a concrete count in the breakdown.
 */
public final class ScoreCalculator {
    private ScoreCalculator() {
    }

    public static ChunkScore score(ChunkObservation observation, ScoringConfig config) {
        Map<LeakType, Integer> counts = observation.counts();

        EnumMap<ScoreCategory, EnumMap<LeakType, Integer>> grouped = new EnumMap<>(ScoreCategory.class);
        EnumMap<ScoreCategory, Double> raw = new EnumMap<>(ScoreCategory.class);
        for (ScoreCategory c : ScoreCategory.values()) {
            grouped.put(c, new EnumMap<>(LeakType.class));
            raw.put(c, 0.0);
        }

        counts.forEach((type, count) -> {
            grouped.get(type.category()).put(type, count);
            raw.merge(type.category(), count * config.weight(type), Double::sum);
        });

        EnumMap<ScoreCategory, CategoryScore> breakdown = new EnumMap<>(ScoreCategory.class);
        double sum = 0;
        for (ScoreCategory c : ScoreCategory.values()) {
            int cap = config.cap(c);
            double points = Math.min(raw.get(c), cap);
            sum += points;
            breakdown.put(c, new CategoryScore(c, raw.get(c), points, cap, grouped.get(c)));
        }

        int total = (int) Math.max(0, Math.min(100, Math.round(sum)));
        return new ChunkScore(observation.coord(), total, breakdown, System.currentTimeMillis());
    }
}
