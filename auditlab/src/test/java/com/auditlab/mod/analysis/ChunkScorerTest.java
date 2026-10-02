package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.BlockEntityRecord;
import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.analysis.model.ChunkScore;
import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.analysis.model.ObservedEvent;
import com.auditlab.mod.analysis.model.ScoreCategory;
import com.auditlab.mod.analysis.model.ScoreReason;
import com.auditlab.mod.analysis.model.Severity;
import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkScorerTest {
    private static final String OW = "minecraft:overworld";
    private final AuditLabConfig config = new AuditLabConfig().normalize();
    private final ChunkScorer scorer = new ChunkScorer(config);

    private static ChunkObservation obs() {
        return new ChunkObservation(new ChunkKey(OW, 0, 0), 16);
    }

    private static CavityFinding room(double confidence) {
        return new CavityFinding(CavityFinding.Kind.ROOM, 1, 10, 1, 6, 12, 6, 108, 1, 1, 1, 1, confidence, false, false);
    }

    private static ObservedEvent event(EventType type, ExposureContext ctx, String subject) {
        return new ObservedEvent(type, ctx, 3, 20, 3, 1000, subject);
    }

    @Test
    void defaultCapsSumTo100() {
        int sum = 0;
        for (ScoreCategory c : ScoreCategory.values()) sum += config.cap(c);
        assertEquals(100, sum);
    }

    @Test
    void emptyObservationScoresZero() {
        ChunkScore s = scorer.score(obs(), List.of(), 0);
        assertEquals(0, s.total());
        assertEquals(Severity.NONE, s.severity());
        assertTrue(s.reasons().isEmpty());
    }

    @Test
    void reasonsExplainEveryPoint() {
        ChunkObservation o = obs();
        o.applyScan(Map.of("minecraft:crafting_table", 2, "minecraft:anvil", 1), Map.of("minecraft:furnace", 5),
            List.of(new BlockEntityRecord("minecraft:chest", 1, 10, 1, true),
                new BlockEntityRecord("minecraft:chest", 2, 10, 1, true),
                new BlockEntityRecord("minecraft:barrel", 3, 70, 1, false)),
            10, 70, 0);
        ChunkScore s = scorer.score(o, List.of(room(1.0), room(0.5)), 0);

        assertEquals(7, s.points(ScoreCategory.BLOCK_DENSITY));   // 2x2 + 1x3
        assertEquals(8, s.points(ScoreCategory.BLOCK_ENTITIES));  // 2 buried chests x4; exposed barrel ignored
        assertEquals(18, s.points(ScoreCategory.GEOMETRY));       // 12 x (1.0 + 0.5)
        assertEquals(33, s.total());
        assertEquals(Severity.MEDIUM, s.severity());

        int sum = s.reasons().stream().mapToInt(ScoreReason::points).sum();
        assertEquals(s.total(), sum);
        assertEquals("+18 Artificial Geometry (2 rooms)", s.reasons().get(0).toString());
        assertEquals("+8 Unexpected BlockEntity (2x chest)", s.reasons().get(1).toString());
        assertEquals("+7 Tracked Blocks (2x crafting_table, 1x anvil)", s.reasons().get(2).toString());
    }

    @Test
    void exposedBlocksCountWhenEnabled() {
        AuditLabConfig c = new AuditLabConfig();
        c.scoreExposedBlocks = true;
        ChunkObservation o = obs();
        o.applyScan(Map.of(), Map.of("minecraft:crafting_table", 3), List.of(), 60, 60, 0);
        assertEquals(6, new ChunkScorer(c.normalize()).score(o, List.of(), 0).total());
        assertEquals(0, scorer.score(o, List.of(), 0).total());
    }

    @Test
    void categoriesAreCappedAndTotalClamped() {
        ChunkObservation o = obs();
        o.applyScan(Map.of("minecraft:anvil", 500), Map.of(), List.of(), 0, 0, 0);
        for (int i = 0; i < 500; i++) o.recordEvent(event(EventType.BLOCK_EVENT, ExposureContext.UNDERGROUND, "minecraft:chest"));
        ChunkScore s = scorer.score(o, List.of(room(1), room(1), room(1)), 0);
        assertEquals(30, s.points(ScoreCategory.BLOCK_DENSITY));
        assertEquals(25, s.points(ScoreCategory.GEOMETRY));
        assertEquals(20, s.points(ScoreCategory.DYNAMIC_EVENTS));
        assertTrue(s.reasons().get(0).capped());
        assertEquals(75, s.total());
        assertEquals(Severity.HIGH, s.severity());

        AuditLabConfig inflated = new AuditLabConfig();
        for (ScoreCategory c : ScoreCategory.values()) inflated.categoryCaps.put(c, 100);
        assertEquals(100, new ChunkScorer(inflated.normalize()).score(o, List.of(room(1)), 0).total());
    }

    @Test
    void dynamicEventsWeightedByContext() {
        ChunkObservation o = obs();
        for (int i = 0; i < 10; i++) o.recordEvent(event(EventType.SOUND, ExposureContext.SURFACE, "minecraft:block.chest.open"));
        assertEquals(0, scorer.score(o, List.of(), 0).total(), "surface activity is visible anyway");

        for (int i = 0; i < 4; i++) o.recordEvent(event(EventType.SOUND, ExposureContext.UNLOADED_CHUNK, "minecraft:block.chest.open"));
        o.recordEvent(event(EventType.BLOCK_EVENT, ExposureContext.UNDERGROUND, "minecraft:chest"));
        ChunkScore s = scorer.score(o, List.of(), 0);
        assertEquals(8, s.points(ScoreCategory.DYNAMIC_EVENTS)); // 4 x 1.0 x 1.5 + 1 x 1.5 = 7.5
        assertEquals("+8 Hidden Activity (4x sound outside loaded chunks, 1x block event underground)", s.reasons().get(0).toString());
    }

    @Test
    void entitySpawnsUsePerTypeWeights() {
        ChunkObservation o = obs();
        for (int i = 0; i < 20; i++) o.recordEvent(event(EventType.ENTITY_SPAWN, ExposureContext.UNDERGROUND, "minecraft:zombie"));
        assertEquals(0, scorer.score(o, List.of(), 0).total(), "mob spawns are noise by default");
        o.recordEvent(event(EventType.ENTITY_SPAWN, ExposureContext.UNDERGROUND, "minecraft:item_frame"));
        assertEquals(2, scorer.score(o, List.of(), 0).total());
    }

    @Test
    void severityBands() {
        assertEquals(Severity.NONE, Severity.of(0, 30, 60));
        assertEquals(Severity.LOW, Severity.of(29, 30, 60));
        assertEquals(Severity.MEDIUM, Severity.of(30, 30, 60));
        assertEquals(Severity.HIGH, Severity.of(60, 30, 60));
        assertEquals(0xFF3CC83C, Severity.LOW.argb(255));
    }
}
