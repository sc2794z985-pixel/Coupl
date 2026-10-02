package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.analysis.model.ObservedEvent;
import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkAnalyzerTest {
    private static final String OW = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static BlockScanner.ScanResult anvils(int n, int y) {
        return new BlockScanner.ScanResult(Map.of("minecraft:anvil", n), Map.of(), List.of(), y, y);
    }

    private static ObservedEvent blockEvent(int chunkX, int y, long t) {
        return new ObservedEvent(EventType.BLOCK_EVENT, ExposureContext.UNDERGROUND, chunkX * 16 + 1, y, 1, t, "minecraft:chest");
    }

    @Test
    void separatesRawObservationFromInference() {
        ChunkAnalyzer a = new ChunkAnalyzer(new AuditLabConfig().normalize());
        ChunkKey key = new ChunkKey(OW, 3, -2);
        CavityFinding cave = new CavityFinding(CavityFinding.Kind.ROOM, 48, -40, -32, 52, -38, -28, 75, 1, 1, 1, 1, 1, false, false);
        a.applyScan(key, anvils(2, 5), List.of(cave), 1);

        ChunkObservation raw = a.findObservation(key).orElseThrow();
        assertEquals(2, raw.buriedBlocks().get("minecraft:anvil"));
        ChunkAnalysis inferred = a.analysis(key).orElseThrow();
        assertEquals(18, inferred.score().total()); // 6 + 12
        assertEquals(List.of(cave), inferred.cavities());
        assertEquals(-40, inferred.minY());
        assertEquals(5, inferred.maxY());
    }

    @Test
    void cacheInvalidatesOnNewData() {
        ChunkAnalyzer a = new ChunkAnalyzer(new AuditLabConfig().normalize());
        ChunkKey key = new ChunkKey(OW, 0, 0);
        a.applyScan(key, anvils(1, 5), List.of(), 1);
        assertEquals(3, a.analysis(key).orElseThrow().score().total());
        a.recordEvent(OW, blockEvent(0, 5, 2));
        a.recordEvent(OW, blockEvent(0, 5, 3));
        assertEquals(6, a.analysis(key).orElseThrow().score().total());
        a.applyScan(key, anvils(0, 5), List.of(), 4); // rescan replaces tallies, keeps events
        assertEquals(3, a.analysis(key).orElseThrow().score().total());
        assertEquals(2, a.eventsRecorded());
    }

    @Test
    void rescanDropsStaleGeometry() {
        ChunkAnalyzer a = new ChunkAnalyzer(new AuditLabConfig().normalize());
        ChunkKey key = new ChunkKey(OW, 0, 0);
        CavityFinding cave = new CavityFinding(CavityFinding.Kind.ROOM, 1, 1, 1, 4, 3, 4, 48, 1, 1, 1, 1, 1, false, false);
        a.applyScan(key, anvils(0, 0), List.of(cave), 1);
        assertEquals(1, a.cavities(key).size());
        a.applyScan(key, anvils(0, 0), List.of(), 2);
        assertTrue(a.cavities(key).isEmpty());
        assertTrue(a.analysesNear(key, 0).isEmpty());
    }

    @Test
    void nearAndTopRespectDimensionRadiusAndOrder() {
        ChunkAnalyzer a = new ChunkAnalyzer(new AuditLabConfig().normalize());
        a.applyScan(new ChunkKey(OW, 0, 0), anvils(1, 0), List.of(), 1);
        a.applyScan(new ChunkKey(OW, 5, 0), anvils(3, 0), List.of(), 2);
        a.applyScan(new ChunkKey(OW, 20, 0), anvils(2, 0), List.of(), 3);
        a.applyScan(new ChunkKey(NETHER, 0, 0), anvils(4, 0), List.of(), 4);
        a.applyScan(new ChunkKey(OW, 1, 1), anvils(0, 0), List.of(), 5); // score 0

        List<ChunkAnalysis> near = a.analysesNear(new ChunkKey(OW, 0, 0), 6);
        assertEquals(2, near.size());
        assertTrue(near.stream().allMatch(x -> x.key().dimension().equals(OW)));
        // Large radius exercises the full-scan path
        assertEquals(3, a.analysesNear(new ChunkKey(OW, 0, 0), 64).size());

        List<Integer> topX = a.top(3).stream().map(x -> x.score().total()).toList();
        assertEquals(List.of(12, 9, 6), topX);
    }

    @Test
    void evictsOldestChunksBeyondLimit() {
        AuditLabConfig c = new AuditLabConfig();
        c.maxTrackedChunks = 64;
        ChunkAnalyzer a = new ChunkAnalyzer(c.normalize());
        for (int i = 0; i < 65; i++) a.applyScan(new ChunkKey(OW, i, 0), anvils(1, 0), List.of(), 100 + i);
        assertTrue(a.chunkCount() <= 64);
        assertTrue(a.findObservation(new ChunkKey(OW, 0, 0)).isEmpty());
        assertTrue(a.findObservation(new ChunkKey(OW, 64, 0)).isPresent());
    }

    @Test
    void configChangeRescoresAndClearResets() {
        ChunkAnalyzer a = new ChunkAnalyzer(new AuditLabConfig().normalize());
        ChunkKey key = new ChunkKey(OW, 0, 0);
        a.applyScan(key, anvils(2, 0), List.of(), 1);
        assertEquals(6, a.analysis(key).orElseThrow().score().total());
        AuditLabConfig doubled = new AuditLabConfig();
        doubled.blockWeights.put("minecraft:anvil", 6.0);
        a.updateConfig(doubled.normalize());
        assertEquals(12, a.analysis(key).orElseThrow().score().total());
        a.clear();
        assertEquals(0, a.chunkCount());
        assertTrue(a.analysis(key).isEmpty());
    }
}
