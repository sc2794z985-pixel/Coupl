package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.BlockEntityRecord;
import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.analysis.model.ChunkScore;
import com.auditlab.mod.analysis.model.ScoreCategory;
import com.auditlab.mod.analysis.model.ScoreReason;
import com.auditlab.mod.analysis.model.Severity;
import com.auditlab.mod.config.AuditLabConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Normalised scoring. Per category: {@code raw = Σ count × weight}, {@code points =
 * round(min(raw, cap))}. The total is the sum of category points clamped to 0..100, so every
 * point on screen traces back to one {@link ScoreReason}.
 */
public final class ChunkScorer {
    private static final int DETAIL_ITEMS = 3;

    private final AuditLabConfig config;

    public ChunkScorer(AuditLabConfig config) {
        this.config = config;
    }

    public ChunkScore score(ChunkObservation obs, List<CavityFinding> cavities, long nowMillis) {
        Map<ScoreCategory, Double> raw = new EnumMap<>(ScoreCategory.class);
        Map<ScoreCategory, String> detail = new EnumMap<>(ScoreCategory.class);

        // Tracked blocks
        Map<String, Integer> blocks = new HashMap<>(obs.buriedBlocks());
        if (config.scoreExposedBlocks) obs.exposedBlocks().forEach((id, n) -> blocks.merge(id, n, Integer::sum));
        Map<String, Double> blockPoints = new HashMap<>();
        blocks.forEach((id, n) -> blockPoints.put(id, n * config.blockWeight(id)));
        raw.put(ScoreCategory.BLOCK_DENSITY, sum(blockPoints));
        detail.put(ScoreCategory.BLOCK_DENSITY, topItems(blockPoints, blocks));

        // Block entities
        Map<String, Integer> beCounts = new HashMap<>();
        for (BlockEntityRecord r : obs.blockEntities()) {
            if (r.buried() || config.scoreExposedBlocks) beCounts.merge(r.typeId(), 1, Integer::sum);
        }
        Map<String, Double> bePoints = new HashMap<>();
        beCounts.forEach((id, n) -> bePoints.put(id, n * config.blockEntityWeight(id)));
        raw.put(ScoreCategory.BLOCK_ENTITIES, sum(bePoints));
        detail.put(ScoreCategory.BLOCK_ENTITIES, topItems(bePoints, beCounts));

        // Geometry
        double geo = 0;
        Map<CavityFinding.Kind, Integer> kinds = new EnumMap<>(CavityFinding.Kind.class);
        for (CavityFinding f : cavities) {
            geo += pointsFor(f.kind()) * f.confidence();
            kinds.merge(f.kind(), 1, Integer::sum);
        }
        raw.put(ScoreCategory.GEOMETRY, geo);
        StringJoiner kindText = new StringJoiner(", ");
        kinds.forEach((k, n) -> kindText.add(n + " " + k.label() + (n == 1 ? "" : "s")));
        detail.put(ScoreCategory.GEOMETRY, kindText.toString());

        // Dynamic events
        Map<String, Double> eventPoints = new LinkedHashMap<>();
        Map<String, Integer> eventCounts = new HashMap<>();
        obs.eventCounts().forEach((key, n) -> {
            double p = n * config.eventWeight(key.type(), key.subject()) * config.contextMultiplier(key.context());
            if (p <= 0) return;
            String label = key.type().label().toLowerCase(Locale.ROOT) + " " + key.context().label();
            eventPoints.merge(label, p, Double::sum);
            eventCounts.merge(label, n, Integer::sum);
        });
        raw.put(ScoreCategory.DYNAMIC_EVENTS, sum(eventPoints));
        detail.put(ScoreCategory.DYNAMIC_EVENTS, topItems(eventPoints, eventCounts));

        Map<ScoreCategory, Integer> points = new EnumMap<>(ScoreCategory.class);
        List<ScoreReason> reasons = new ArrayList<>();
        int total = 0;
        for (ScoreCategory c : ScoreCategory.values()) {
            double r = raw.getOrDefault(c, 0.0);
            int p = (int) Math.round(Math.min(r, config.cap(c)));
            points.put(c, p);
            total += p;
            if (p > 0) reasons.add(new ScoreReason(c, p, r, detail.get(c)));
        }
        reasons.sort(Comparator.comparingInt(ScoreReason::points).reversed());
        total = Math.max(0, Math.min(100, total));
        Severity severity = Severity.of(total, config.mediumThreshold, config.highThreshold);
        return new ChunkScore(obs.key(), total, severity, points, reasons, nowMillis);
    }

    private double pointsFor(CavityFinding.Kind kind) {
        return switch (kind) {
            case ROOM -> config.geometry.roomPoints;
            case CORRIDOR -> config.geometry.corridorPoints;
            case SHAFT -> config.geometry.shaftPoints;
        };
    }

    private static double sum(Map<String, Double> m) {
        double s = 0;
        for (double v : m.values()) s += v;
        return s;
    }

    /** "4x crafting_table, 2x anvil" for the highest-point entries. */
    private static String topItems(Map<String, Double> points, Map<String, Integer> counts) {
        StringJoiner j = new StringJoiner(", ");
        points.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(DETAIL_ITEMS)
            .forEach(e -> j.add(counts.get(e.getKey()) + "x " + shortId(e.getKey())));
        return j.toString();
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
