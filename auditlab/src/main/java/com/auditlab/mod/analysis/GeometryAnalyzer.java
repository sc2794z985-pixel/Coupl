package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.config.AuditLabConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Cavity and geometry heuristics over a {@link ChunkVolume}.
 *
 * <ol>
 *   <li>Cells that are {@link ChunkVolume#OPEN} and below their column's surface are "enclosed".</li>
 *   <li>Enclosed cells are grouped into 6-connected components (iterative flood fill).</li>
 *   <li>Each component of plausible size is measured:
 *     <ul>
 *       <li><b>fill ratio</b>: volume / bounding-box volume. Carved boxes are close to 1, caves are not.</li>
 *       <li><b>floor / ceiling flatness</b>: share of floor (ceiling) faces on the most common Y level.</li>
 *       <li><b>wall alignment</b>: for each horizontal direction, share of wall faces on the dominant
 *           plane. Straight walls meeting at right angles score near 1.</li>
 *     </ul>
 *   </li>
 *   <li>A component is reported when fill ratio and planarity (mean of the three shape metrics)
 *       both clear their thresholds.</li>
 * </ol>
 *
 * <p>Known false positives: generated structures (dungeons, mineshafts, strongholds, trial
 * chambers, ancient cities) are also rectilinear.
 */
public final class GeometryAnalyzer {
    private static final int[][] HORIZONTAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /** Flood-fill outcome; cell indices are in the caller's {@code members} buffer. */
    private record Component(int size, boolean oversized, boolean opensToSurface) {
    }

    private final AuditLabConfig.Geometry settings;

    public GeometryAnalyzer(AuditLabConfig.Geometry settings) {
        this.settings = settings;
    }

    public List<CavityFinding> analyze(ChunkVolume volume) {
        final int s = ChunkVolume.SIZE;
        final int h = volume.height();
        final int n = s * s * h;

        int[] surfaceLocal = new int[s * s];
        for (int z = 0; z < s; z++) {
            for (int x = 0; x < s; x++) surfaceLocal[z * s + x] = volume.surfaceY(x, z) - volume.minY();
        }

        // 0 = unvisited enclosed, -1 = not enclosed / visited
        byte[] state = new byte[n];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < s; z++) {
                for (int x = 0; x < s; x++) {
                    boolean enclosed = volume.getLocal(x, y, z) == ChunkVolume.OPEN && y < surfaceLocal[z * s + x];
                    if (!enclosed) state[ChunkVolume.index(x, y, z)] = -1;
                }
            }
        }

        List<CavityFinding> findings = new ArrayList<>();
        int[] members = new int[n];
        for (int start = 0; start < n; start++) {
            if (state[start] != 0) continue;
            Component c = flood(volume, state, surfaceLocal, start, members);
            if (c.oversized() || c.size() < settings.minVolume) continue;
            CavityFinding f = measure(volume, members, c.size(), c.opensToSurface());
            if (f != null) findings.add(f);
        }
        return findings;
    }

    /**
     * Flood-fills one component, marking every cell visited and writing its cell indices to
     * {@code members}. Oversized components are still walked completely so they are not
     * revisited from another start cell.
     */
    private Component flood(ChunkVolume volume, byte[] state, int[] surfaceLocal, int start, int[] members) {
        final int s = ChunkVolume.SIZE;
        final int h = volume.height();
        int count = 0;
        int head = 0;
        boolean opens = false;

        state[start] = -1;
        members[count++] = start;
        while (head < count) {
            int idx = members[head++];
            int x = idx % s;
            int z = (idx / s) % s;
            int y = idx / (s * s);
            for (int d = 0; d < 6; d++) {
                int nx = x, ny = y, nz = z;
                switch (d) {
                    case 0 -> nx--;
                    case 1 -> nx++;
                    case 2 -> nz--;
                    case 3 -> nz++;
                    case 4 -> ny--;
                    default -> ny++;
                }
                if (nx < 0 || nx >= s || nz < 0 || nz >= s || ny < 0 || ny >= h) continue;
                int ni = ChunkVolume.index(nx, ny, nz);
                if (state[ni] == 0) {
                    state[ni] = -1;
                    members[count++] = ni;
                } else if (!opens && volume.getLocal(nx, ny, nz) == ChunkVolume.OPEN && ny >= surfaceLocal[nz * s + nx]) {
                    opens = true;
                }
            }
        }
        return new Component(count, count > settings.maxVolume, opens);
    }

    private CavityFinding measure(ChunkVolume v, int[] members, int count, boolean opensToSurface) {
        final int s = ChunkVolume.SIZE;
        final int h = v.height();
        int minX = s, minY = h, minZ = s, maxX = -1, maxY = -1, maxZ = -1;
        for (int i = 0; i < count; i++) {
            int idx = members[i];
            int x = idx % s, z = (idx / s) % s, y = idx / (s * s);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        if (sy < settings.minHeight) return null;

        double fill = (double) count / ((long) sx * sy * sz);
        if (fill < settings.minFillRatio) return null;

        int[] floorHist = new int[h];
        int[] ceilHist = new int[h];
        int floors = 0, ceilings = 0;
        int[][] wallHist = {new int[s], new int[s], new int[s], new int[s]};
        int[] wallTotals = new int[4];
        boolean clipped = false;

        for (int i = 0; i < count; i++) {
            int idx = members[i];
            int x = idx % s, z = (idx / s) % s, y = idx / (s * s);
            if (x == 0 || x == s - 1 || z == 0 || z == s - 1) clipped = true;
            if (y > 0 && v.getLocal(x, y - 1, z) != ChunkVolume.OPEN) {
                floorHist[y]++;
                floors++;
            }
            if (y < h - 1 && v.getLocal(x, y + 1, z) != ChunkVolume.OPEN) {
                ceilHist[y]++;
                ceilings++;
            }
            for (int d = 0; d < 4; d++) {
                int nx = x + HORIZONTAL[d][0], nz = z + HORIZONTAL[d][1];
                if (nx < 0 || nx >= s || nz < 0 || nz >= s) continue;
                if (v.getLocal(nx, y, nz) != ChunkVolume.OPEN) {
                    wallHist[d][d < 2 ? x : z]++;
                    wallTotals[d]++;
                }
            }
        }

        double floorFlat = dominantShare(floorHist, floors);
        double ceilFlat = dominantShare(ceilHist, ceilings);
        int wallFaces = 0, aligned = 0;
        for (int d = 0; d < 4; d++) {
            wallFaces += wallTotals[d];
            aligned += max(wallHist[d]);
        }
        double walls = wallFaces == 0 ? 0 : (double) aligned / wallFaces;
        double planarity = (floorFlat + ceilFlat + walls) / 3.0;
        if (planarity < settings.minPlanarity) return null;

        int horizMin = Math.min(sx, sz), horizMax = Math.max(sx, sz);
        CavityFinding.Kind kind;
        if (sy >= 4 && horizMax <= 2) kind = CavityFinding.Kind.SHAFT;
        else if (horizMin <= 2 && horizMax >= 4) kind = CavityFinding.Kind.CORRIDOR;
        else kind = CavityFinding.Kind.ROOM;

        double confidence = Math.max(0, Math.min(1, 0.5 * fill + 0.5 * planarity));
        int ox = v.originX(), oz = v.originZ(), oy = v.minY();
        return new CavityFinding(kind,
            ox + minX, oy + minY, oz + minZ,
            ox + maxX, oy + maxY, oz + maxZ,
            count, fill, floorFlat, ceilFlat, walls, confidence, clipped, opensToSurface);
    }

    private static double dominantShare(int[] hist, int total) {
        return total == 0 ? 0 : (double) max(hist) / total;
    }

    private static int max(int[] a) {
        int m = 0;
        for (int v : a) m = Math.max(m, v);
        return m;
    }
}
