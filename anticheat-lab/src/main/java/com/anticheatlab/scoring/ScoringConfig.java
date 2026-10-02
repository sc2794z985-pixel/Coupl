package com.anticheatlab.scoring;

import com.anticheatlab.model.LeakType;
import com.anticheatlab.model.ScoreCategory;

import java.util.EnumMap;

/** Immutable weights (points per event) and caps (max points per category). */
public final class ScoringConfig {
    private static final ScoringConfig DEFAULTS = new ScoringConfig(defaultWeights(), defaultCaps());

    private final EnumMap<LeakType, Double> weights;
    private final EnumMap<ScoreCategory, Integer> caps;

    private ScoringConfig(EnumMap<LeakType, Double> weights, EnumMap<ScoreCategory, Integer> caps) {
        this.weights = weights;
        this.caps = caps;
    }

    public static ScoringConfig defaults() {
        return DEFAULTS;
    }

    public double weight(LeakType type) {
        return weights.get(type);
    }

    public int cap(ScoreCategory category) {
        return caps.get(category);
    }

    public ScoringConfig withWeight(LeakType type, double weight) {
        if (weight < 0 || !Double.isFinite(weight)) throw new IllegalArgumentException("weight must be >= 0: " + weight);
        EnumMap<LeakType, Double> w = new EnumMap<>(weights);
        w.put(type, weight);
        return new ScoringConfig(w, caps);
    }

    public ScoringConfig withCap(ScoreCategory category, int cap) {
        if (cap < 0 || cap > 100) throw new IllegalArgumentException("cap must be within 0..100: " + cap);
        EnumMap<ScoreCategory, Integer> c = new EnumMap<>(caps);
        c.put(category, cap);
        return new ScoringConfig(weights, c);
    }

    private static EnumMap<LeakType, Double> defaultWeights() {
        EnumMap<LeakType, Double> map = new EnumMap<>(LeakType.class);
        for (LeakType t : LeakType.values()) map.put(t, t.defaultWeight());
        return map;
    }

    private static EnumMap<ScoreCategory, Integer> defaultCaps() {
        EnumMap<ScoreCategory, Integer> map = new EnumMap<>(ScoreCategory.class);
        for (ScoreCategory c : ScoreCategory.values()) map.put(c, c.defaultCap());
        return map;
    }
}
