package com.auditlab.mod.render;

import com.auditlab.mod.analysis.ChunkScorer;
import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LabelFormatterTest {
    @Test
    void headerAndTopReasons() {
        ChunkKey key = new ChunkKey("minecraft:overworld", -4, 9);
        ChunkObservation o = new ChunkObservation(key, 0);
        o.applyScan(Map.of("minecraft:anvil", 2, "minecraft:lever", 2), Map.of(), List.of(), 0, 0, 0);
        ChunkAnalysis a = new ChunkAnalysis(key, new ChunkScorer(new AuditLabConfig().normalize()).score(o, List.of(), 0), List.of(), 0, 0);

        List<String> lines = LabelFormatter.lines(a, 2);
        assertEquals(List.of("Chunk -4, 9  Score 7 (LOW)", "+7 Tracked Blocks (2x anvil, 2x lever)"), lines);
        assertEquals(1, LabelFormatter.lines(a, 0).size());
    }

    @Test
    void truncatesLongLines() {
        String s = "x".repeat(100);
        String t = LabelFormatter.truncate(s);
        assertEquals(LabelFormatter.MAX_LINE_CHARS, t.length());
        assertTrue(t.endsWith("…"));
    }
}
