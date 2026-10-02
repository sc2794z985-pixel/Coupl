package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.analysis.model.ObservedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkObservationTest {
    private static final String OW = "minecraft:overworld";

    private static ObservedEvent sound(int x, int y, int z, long t) {
        return new ObservedEvent(EventType.SOUND, ExposureContext.UNDERGROUND, x, y, z, t, "minecraft:block.chest.open");
    }

    @Test
    void eventsAggregateAndLogIsBounded() {
        ChunkObservation o = new ChunkObservation(new ChunkKey(OW, 0, 0), 3);
        for (int i = 0; i < 5; i++) o.recordEvent(sound(1, i, 1, 10 + i));
        assertEquals(5, o.eventTotal());
        assertEquals(1, o.eventCounts().size());
        assertEquals(5, o.eventCounts().values().iterator().next());
        assertEquals(3, o.eventLog().size());
        assertEquals(2, o.eventLog().get(0).y());
        assertEquals(10, o.firstSeenMillis());
        assertEquals(14, o.lastSeenMillis());
        assertEquals(0, o.minY());
        assertEquals(4, o.maxY());
    }

    @Test
    void scanReplacesPreviousScan() {
        ChunkObservation o = new ChunkObservation(new ChunkKey(OW, 0, 0), 0);
        o.applyScan(Map.of("minecraft:anvil", 2), Map.of(), List.of(), -10, 5, 1);
        o.applyScan(Map.of("minecraft:lever", 1), Map.of(), List.of(), 20, 20, 2);
        assertEquals(Map.of("minecraft:lever", 1), o.buriedBlocks());
        assertEquals(2, o.scanCount());
        assertEquals(20, o.minY());
        assertTrue(o.eventLog().isEmpty());
    }

    @Test
    void rejectsEventsFromOtherChunks() {
        ChunkObservation o = new ChunkObservation(new ChunkKey(OW, 0, 0), 4);
        assertThrows(IllegalArgumentException.class, () -> o.recordEvent(sound(16, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> o.recordEvent(sound(-1, 0, 0, 1)));
    }

    @Test
    void chunkKeyMatchesVanillaPacking() {
        ChunkKey k = new ChunkKey(OW, -3, 7);
        assertEquals((-3 & 0xFFFFFFFFL) | (7L << 32), k.packedPos());
        assertEquals(new ChunkKey(OW, -1, -1), ChunkKey.ofBlock(OW, -1, -16));
        assertEquals(Integer.MAX_VALUE, k.chunkDistance(new ChunkKey("minecraft:the_end", -3, 7)));
    }
}
