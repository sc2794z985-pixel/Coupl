package com.anticheatlab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of scoring one {@link ChunkObservation}: a 0-100 total plus a per-category
 * breakdown explaining where every point came from.
 */
public record ChunkScore(ChunkCoord coord, int total, Map<ScoreCategory, CategoryScore> breakdown, long computedAtMillis) {
    public ChunkScore {
        Objects.requireNonNull(coord, "coord");
        if (total < 0 || total > 100) throw new IllegalArgumentException("total out of range: " + total);
        EnumMap<ScoreCategory, CategoryScore> copy = new EnumMap<>(ScoreCategory.class);
        copy.putAll(breakdown);
        breakdown = Collections.unmodifiableMap(copy);
    }

    /**
     * Points contributed by one category.
     *
     * @param raw    uncapped sum of {@code count * weight} over the category's leak types
     * @param points {@code min(raw, cap)}, the value that feeds the total
     * @param counts per-type event counts that produced {@code raw}
     */
    public record CategoryScore(ScoreCategory category, double raw, double points, int cap, Map<LeakType, Integer> counts) {
        public CategoryScore {
            Objects.requireNonNull(category, "category");
            EnumMap<LeakType, Integer> copy = new EnumMap<>(LeakType.class);
            copy.putAll(counts);
            counts = Collections.unmodifiableMap(copy);
        }

        public boolean saturated() {
            return cap > 0 && raw >= cap;
        }
    }

    public Severity severity() {
        return Severity.of(total);
    }

    public double points(ScoreCategory category) {
        CategoryScore s = breakdown.get(category);
        return s == null ? 0 : s.points();
    }

    /** Human-readable breakdown, one line per non-empty category. */
    public List<String> explain() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%s: %d/100 (%s)", coord, total, severity()));
        for (CategoryScore s : breakdown.values()) {
            if (s.counts().isEmpty()) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %s: %.1f/%d", s.category().displayName(), s.points(), s.cap()));
            if (s.saturated()) sb.append(String.format(" [capped, raw %.1f]", s.raw()));
            sb.append(" <-");
            s.counts().forEach((type, count) -> sb.append(' ').append(type).append('x').append(count));
            lines.add(sb.toString());
        }
        return lines;
    }
}
