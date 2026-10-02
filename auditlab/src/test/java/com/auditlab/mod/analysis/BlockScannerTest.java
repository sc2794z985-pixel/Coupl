package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.PositionedId;
import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockScannerTest {
    private final BlockScanner scanner = BlockScanner.fromConfig(new AuditLabConfig().normalize());

    @Test
    void tracksOnlyWeightedIds() {
        assertTrue(scanner.isTrackedBlock("minecraft:crafting_table"));
        assertFalse(scanner.isTrackedBlock("minecraft:stone"));
        assertTrue(scanner.isTrackedBlockEntity("minecraft:chest"));
        assertFalse(scanner.isTrackedBlockEntity("minecraft:mob_spawner"));
    }

    @Test
    void splitsBuriedAndExposed() {
        ChunkVolume v = Volumes.solidUpTo(1, 1, 63); // origin 16,16
        BlockScanner.ScanResult r = scanner.scan(v,
            List.of(
                new PositionedId("minecraft:crafting_table", 20, 10, 20),  // 53 below surface
                new PositionedId("minecraft:crafting_table", 20, 62, 20),  // 1 below surface
                new PositionedId("minecraft:stone", 20, 0, 20),            // untracked
                new PositionedId("minecraft:anvil", 100, 0, 100)),         // other chunk
            List.of(
                new PositionedId("minecraft:chest", 17, -30, 30),
                new PositionedId("minecraft:mob_spawner", 18, -30, 30)));

        assertEquals(1, r.buriedBlocks().get("minecraft:crafting_table"));
        assertEquals(1, r.exposedBlocks().get("minecraft:crafting_table"));
        assertEquals(2, r.trackedBlockCount());
        assertEquals(1, r.blockEntities().size());
        assertTrue(r.blockEntities().get(0).buried());
        assertEquals(-30, r.minY());
        assertEquals(62, r.maxY());
    }

    @Test
    void emptyScanHasNoExtent() {
        BlockScanner.ScanResult r = scanner.scan(Volumes.solidUpTo(0, 0, 63), List.of(), List.of());
        assertTrue(r.minY() > r.maxY());
    }
}
