package com.anticheatlab.scoring;

import com.anticheatlab.model.ChunkCoord;
import com.anticheatlab.model.ChunkObservation;
import com.anticheatlab.model.ChunkScore;
import com.anticheatlab.model.LeakEvent;
import com.anticheatlab.model.LeakType;
import com.anticheatlab.model.ScoreCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreCalculatorTest {
    private static final String OW = "minecraft:overworld";

    private static ChunkObservation obs() {
        return new ChunkObservation(new ChunkCoord(OW, 0, 0));
    }

    @Test
    void defaultCapsSumTo100() {
        int sum = 0;
        for (ScoreCategory c : ScoreCategory.values()) sum += ScoringConfig.defaults().cap(c);
        assertEquals(100, sum);
    }

    @Test
    void emptyObservationScoresZero() {
        ChunkScore score = ScoreCalculator.score(obs(), ScoringConfig.defaults());
        assertEquals(0, score.total());
        assertEquals(ScoreCategory.values().length, score.breakdown().size());
    }

    @Test
    void pointsAreWeightTimesCount() {
        ChunkObservation o = obs();
        o.record(LeakEvent.now(LeakType.STORAGE_BLOCK_ENTITY, OW, 0, 0, 0, ""));
        o.record(LeakEvent.now(LeakType.STORAGE_BLOCK_ENTITY, OW, 1, 0, 0, ""));
        o.record(LeakEvent.now(LeakType.OTHER_BLOCK_ENTITY, OW, 2, 0, 0, ""));
        ChunkScore score = ScoreCalculator.score(o, ScoringConfig.defaults());
        assertEquals(12.0, score.points(ScoreCategory.BLOCK_ENTITIES), 1e-9);
        assertEquals(12, score.total());
        assertEquals(2, score.breakdown().get(ScoreCategory.BLOCK_ENTITIES).counts().get(LeakType.STORAGE_BLOCK_ENTITY));
    }

    @Test
    void categoriesAreCappedAndTotalClampedTo100() {
        ChunkObservation o = obs();
        for (int i = 0; i < 200; i++) {
            for (LeakType t : LeakType.values()) {
                o.record(LeakEvent.now(t, OW, i % 16, i, i / 16, ""));
            }
        }
        ChunkScore score = ScoreCalculator.score(o, ScoringConfig.defaults());
        assertEquals(100, score.total());
        for (ScoreCategory c : ScoreCategory.values()) {
            assertTrue(score.breakdown().get(c).saturated(), c + " should be saturated");
            assertEquals(c.defaultCap(), score.points(c), 1e-9);
        }

        ScoringConfig inflated = ScoringConfig.defaults();
        for (ScoreCategory c : ScoreCategory.values()) inflated = inflated.withCap(c, 100);
        assertEquals(100, ScoreCalculator.score(o, inflated).total());
    }

    @Test
    void zeroCapDisablesCategory() {
        ChunkObservation o = obs();
        o.record(LeakEvent.now(LeakType.BLOCK_SOUND, OW, 0, 0, 0, ""));
        ScoringConfig config = ScoringConfig.defaults().withCap(ScoreCategory.SOUNDS, 0);
        assertEquals(0, ScoreCalculator.score(o, config).total());
    }

    @Test
    void configRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> ScoringConfig.defaults().withCap(ScoreCategory.SOUNDS, 101));
        assertThrows(IllegalArgumentException.class, () -> ScoringConfig.defaults().withWeight(LeakType.CAVITY, -1));
        assertThrows(IllegalArgumentException.class, () -> ScoringConfig.defaults().withWeight(LeakType.CAVITY, Double.NaN));
    }

    @Test
    void explainListsOnlyContributingCategories() {
        ChunkObservation o = obs();
        o.record(LeakEvent.now(LeakType.CAVITY, OW, 0, 0, 0, ""));
        var lines = ScoreCalculator.score(o, ScoringConfig.defaults()).explain();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("Cavity geometry"));
        assertTrue(lines.get(1).contains("CAVITYx1"));
    }
}
