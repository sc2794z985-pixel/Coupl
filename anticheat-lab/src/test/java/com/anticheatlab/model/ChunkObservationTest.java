package com.anticheatlab.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkObservationTest {
    private static final String OW = "minecraft:overworld";

    @Test
    void staticObservationsAreDedupedByPosition() {
        ChunkObservation obs = new ChunkObservation(new ChunkCoord(OW, 0, 0));
        assertTrue(obs.record(LeakEvent.now(LeakType.STORAGE_BLOCK_ENTITY, OW, 1, 64, 1, "chest")));
        assertFalse(obs.record(LeakEvent.now(LeakType.STORAGE_BLOCK_ENTITY, OW, 1, 64, 1, "chest")));
        assertTrue(obs.record(LeakEvent.now(LeakType.STORAGE_BLOCK_ENTITY, OW, 2, 64, 1, "chest")));
        assertEquals(2, obs.count(LeakType.STORAGE_BLOCK_ENTITY));
    }

    @Test
    void dynamicObservationsCountEveryOccurrence() {
        ChunkObservation obs = new ChunkObservation(new ChunkCoord(OW, 0, 0));
        for (int i = 0; i < 5; i++) {
            assertTrue(obs.record(LeakEvent.now(LeakType.BLOCK_EVENT, OW, 3, 10, 3, "chest lid")));
        }
        assertEquals(5, obs.count(LeakType.BLOCK_EVENT));
        assertEquals(5, obs.totalCount());
        assertEquals(5, obs.revision());
    }

    @Test
    void rejectsEventsFromAnotherChunk() {
        ChunkObservation obs = new ChunkObservation(new ChunkCoord(OW, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> obs.record(LeakEvent.now(LeakType.CAVITY, OW, 16, 0, 0, "")));
        assertThrows(IllegalArgumentException.class,
            () -> obs.record(LeakEvent.now(LeakType.CAVITY, "minecraft:the_nether", 0, 0, 0, "")));
    }

    @Test
    void negativeCoordinatesMapToCorrectChunk() {
        assertEquals(new ChunkCoord(OW, -1, -1), LeakEvent.now(LeakType.CAVITY, OW, -1, 0, -16, "").chunk());
        assertEquals(new ChunkCoord(OW, -2, 0), LeakEvent.now(LeakType.CAVITY, OW, -17, 0, 15, "").chunk());
    }

    @Test
    void packedPositionsDistinguishNegativeY() {
        LeakEvent a = LeakEvent.now(LeakType.PLACED_BLOCK, OW, 5, -60, 5, "");
        LeakEvent b = LeakEvent.now(LeakType.PLACED_BLOCK, OW, 5, 60, 5, "");
        assertNotEquals(a.packedPos(), b.packedPos());
    }

    @Test
    void recentEventsAreBounded() {
        ChunkObservation obs = new ChunkObservation(new ChunkCoord(OW, 0, 0));
        for (int i = 0; i < ChunkObservation.RECENT_EVENT_LIMIT + 10; i++) {
            obs.record(LeakEvent.now(LeakType.BLOCK_SOUND, OW, 0, i, 0, ""));
        }
        assertEquals(ChunkObservation.RECENT_EVENT_LIMIT, obs.recentEvents().size());
        assertEquals(10, obs.recentEvents().get(0).y());
    }

    @Test
    void severityBands() {
        assertEquals(Severity.CLEAN, Severity.of(0));
        assertEquals(Severity.LOW, Severity.of(1));
        assertEquals(Severity.MEDIUM, Severity.of(25));
        assertEquals(Severity.HIGH, Severity.of(74));
        assertEquals(Severity.CRITICAL, Severity.of(100));
    }
}
