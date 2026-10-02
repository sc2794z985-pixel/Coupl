package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.auditlab.mod.analysis.ChunkVolume.OPEN;
import static com.auditlab.mod.analysis.ChunkVolume.SOLID;
import static org.junit.jupiter.api.Assertions.*;

class GeometryAnalyzerTest {
    private final GeometryAnalyzer analyzer = new GeometryAnalyzer(new AuditLabConfig().normalize().geometry);

    @Test
    void solidChunkHasNoFindings() {
        assertTrue(analyzer.analyze(Volumes.solidUpTo(0, 0, 63)).isEmpty());
    }

    @Test
    void detectsRectangularRoom() {
        ChunkVolume v = Volumes.solidUpTo(2, -3, 63);
        Volumes.fill(v, 4, 10, 5, 9, 12, 10, OPEN); // 6 x 3 x 6
        List<CavityFinding> f = analyzer.analyze(v);
        assertEquals(1, f.size());
        CavityFinding room = f.get(0);
        assertEquals(CavityFinding.Kind.ROOM, room.kind());
        assertEquals(108, room.volume());
        assertEquals(1.0, room.fillRatio(), 1e-9);
        assertEquals(1.0, room.floorFlatness(), 1e-9);
        assertEquals(1.0, room.ceilingFlatness(), 1e-9);
        assertEquals(1.0, room.wallAlignment(), 1e-9);
        assertEquals(32 + 4, room.minX());
        assertEquals(10, room.minY());
        assertEquals(-48 + 10, room.maxZ());
        assertFalse(room.clipped());
        assertFalse(room.opensToSurface());
    }

    @Test
    void roomWithPillarStillDetected() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        Volumes.fill(v, 3, 0, 3, 10, 3, 10, OPEN);
        Volumes.fill(v, 6, 0, 6, 6, 3, 6, SOLID);
        List<CavityFinding> f = analyzer.analyze(v);
        assertEquals(1, f.size());
        assertTrue(f.get(0).confidence() > 0.9);
    }

    @Test
    void classifiesCorridorAndShaft() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        Volumes.fill(v, 1, 20, 2, 12, 21, 2, OPEN); // 12 x 2 x 1 corridor
        Volumes.fill(v, 7, -20, 10, 8, -10, 10, OPEN); // 2 x 11 x 1 shaft
        List<CavityFinding> f = analyzer.analyze(v);
        assertEquals(2, f.size());
        assertTrue(f.stream().anyMatch(c -> c.kind() == CavityFinding.Kind.CORRIDOR));
        assertTrue(f.stream().anyMatch(c -> c.kind() == CavityFinding.Kind.SHAFT));
    }

    @Test
    void ignoresIrregularCave() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        // Sphere of radius 5: fill ratio ~0.52
        for (int y = -5; y <= 5; y++) {
            for (int z = -5; z <= 5; z++) {
                for (int x = -5; x <= 5; x++) {
                    if (x * x + y * y + z * z <= 25) v.set(8 + x, 20 + y, 8 + z, OPEN);
                }
            }
        }
        assertTrue(analyzer.analyze(v).isEmpty());
    }

    @Test
    void ignoresSteppedIrregularPocket() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        int[] heights = {2, 4, 3, 5, 2, 4, 3, 5, 2};
        for (int i = 0; i < heights.length; i++) {
            Volumes.fill(v, 3 + i, 0, 3 + (i % 3), 3 + i, heights[i], 5 + (i % 4), OPEN);
        }
        assertTrue(analyzer.analyze(v).isEmpty());
    }

    @Test
    void ignoresSmallPocketsAndOpenSky() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        Volumes.fill(v, 2, 0, 2, 3, 1, 3, OPEN); // 8 cells < minVolume
        assertTrue(analyzer.analyze(v).isEmpty());
        // Air above the surface is never "enclosed"
        assertTrue(analyzer.analyze(Volumes.solidUpTo(0, 0, -60)).isEmpty());
    }

    @Test
    void flagsClippedAndSurfaceConnectedCavities() {
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        Volumes.fill(v, 0, 30, 4, 5, 32, 8, OPEN); // touches x=0 border
        Volumes.fill(v, 10, 40, 10, 14, 42, 14, OPEN);
        Volumes.fill(v, 12, 43, 12, 12, 63, 12, OPEN); // 1x1 shaft to the sky
        List<CavityFinding> f = analyzer.analyze(v);
        CavityFinding clipped = f.stream().filter(c -> c.minY() == 30).findFirst().orElseThrow();
        assertTrue(clipped.clipped());
        // The shaft column is open to the sky (its surface drops below the room), so the room
        // minus that column is reported and flagged as surface-connected.
        CavityFinding connected = f.stream().filter(c -> c.minY() == 40).findFirst().orElseThrow();
        assertTrue(connected.opensToSurface());
        assertEquals(5 * 3 * 5 - 3, connected.volume());
    }

    @Test
    void oversizedComponentsAreSkipped() {
        AuditLabConfig config = new AuditLabConfig();
        config.geometry.maxVolume = 50;
        GeometryAnalyzer small = new GeometryAnalyzer(config.normalize().geometry);
        ChunkVolume v = Volumes.solidUpTo(0, 0, 63);
        Volumes.fill(v, 2, 0, 2, 9, 3, 9, OPEN); // 256 cells
        assertTrue(small.analyze(v).isEmpty());
    }
}
