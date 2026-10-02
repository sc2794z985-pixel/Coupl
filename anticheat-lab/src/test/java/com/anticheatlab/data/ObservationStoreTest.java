package com.anticheatlab.data;

import com.anticheatlab.model.ChunkCoord;
import com.anticheatlab.model.ChunkScore;
import com.anticheatlab.model.LeakEvent;
import com.anticheatlab.model.LeakType;
import com.anticheatlab.scoring.ScoringConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservationStoreTest {
    private static final String OW = "minecraft:overworld";

    private static LeakEvent at(LeakType type, int chunkX, int y, long time) {
        return new LeakEvent(type, OW, chunkX * 16, y, 0, time, "");
    }

    @Test
    void routesEventsToChunksAndCountsDuplicates() {
        ObservationStore store = new ObservationStore();
        assertTrue(store.record(at(LeakType.CAVITY, 0, 10, 1)));
        assertFalse(store.record(at(LeakType.CAVITY, 0, 10, 2)));
        assertTrue(store.record(at(LeakType.CAVITY, 1, 10, 3)));
        assertEquals(2, store.size());
        assertEquals(2, store.countedEvents());
        assertEquals(1, store.duplicateEvents());
        assertTrue(store.get(new ChunkCoord(OW, 1, 0)).isPresent());
    }

    @Test
    void topScoresAreRankedAndExcludeZero() {
        ObservationStore store = new ObservationStore();
        store.record(at(LeakType.BLOCK_SOUND, 0, 0, 1));
        for (int y = 0; y < 3; y++) store.record(at(LeakType.STORAGE_BLOCK_ENTITY, 1, y, 1));
        store.record(at(LeakType.CAVITY, 2, 0, 1));

        ScoringConfig config = ScoringConfig.defaults().withCap(com.anticheatlab.model.ScoreCategory.SOUNDS, 0);
        List<ChunkScore> top = store.topScores(10, config);
        assertEquals(List.of(1, 2), top.stream().map(s -> s.coord().x()).toList());
        assertEquals(1, store.topScores(1, config).size());
    }

    @Test
    void evictsLeastRecentlyUpdatedChunks() {
        ObservationStore store = new ObservationStore(10);
        for (int i = 0; i < 11; i++) store.record(at(LeakType.CAVITY, i, 0, 100 + i));
        assertTrue(store.size() <= 10);
        assertTrue(store.get(new ChunkCoord(OW, 0, 0)).isEmpty(), "oldest chunk should be evicted");
        assertTrue(store.get(new ChunkCoord(OW, 10, 0)).isPresent(), "newest chunk should survive");
    }

    @Test
    void clearResetsEverything() {
        ObservationStore store = new ObservationStore();
        store.record(at(LeakType.CAVITY, 0, 0, 1));
        store.clear();
        assertEquals(0, store.size());
        assertEquals(0, store.countedEvents());
    }
}
