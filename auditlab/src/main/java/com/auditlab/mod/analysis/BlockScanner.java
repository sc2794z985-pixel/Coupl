package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.BlockEntityRecord;
import com.auditlab.mod.analysis.model.PositionedId;
import com.auditlab.mod.config.AuditLabConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Block density analysis. Decides which block and block-entity ids are tracked (anything with a
 * configured weight) and classifies each hit as buried or exposed relative to its column surface
 * in the {@link ChunkVolume}.
 */
public final class BlockScanner {
    /**
     * Result of scanning one chunk.
     *
     * @param minY lowest Y of any tracked hit, {@link Integer#MAX_VALUE} if none
     * @param maxY highest Y of any tracked hit, {@link Integer#MIN_VALUE} if none
     */
    public record ScanResult(Map<String, Integer> buriedBlocks, Map<String, Integer> exposedBlocks,
                             List<BlockEntityRecord> blockEntities, int minY, int maxY) {
        public ScanResult {
            buriedBlocks = Map.copyOf(buriedBlocks);
            exposedBlocks = Map.copyOf(exposedBlocks);
            blockEntities = List.copyOf(blockEntities);
        }

        public int trackedBlockCount() {
            int n = 0;
            for (int c : buriedBlocks.values()) n += c;
            for (int c : exposedBlocks.values()) n += c;
            return n;
        }
    }

    private final Set<String> trackedBlocks;
    private final Set<String> trackedBlockEntities;
    private final int buriedDepth;

    public BlockScanner(Set<String> trackedBlocks, Set<String> trackedBlockEntities, int buriedDepth) {
        this.trackedBlocks = Set.copyOf(trackedBlocks);
        this.trackedBlockEntities = Set.copyOf(trackedBlockEntities);
        this.buriedDepth = buriedDepth;
    }

    public static BlockScanner fromConfig(AuditLabConfig config) {
        return new BlockScanner(positiveKeys(config.blockWeights), positiveKeys(config.blockEntityWeights), config.buriedDepth);
    }

    public boolean isTrackedBlock(String blockId) {
        return trackedBlocks.contains(blockId);
    }

    public boolean isTrackedBlockEntity(String typeId) {
        return trackedBlockEntities.contains(typeId);
    }

    /**
     * @param blocks        tracked block hits (callers may pre-filter with {@link #isTrackedBlock})
     * @param blockEntities all block entities in the chunk; untracked types are dropped here
     */
    public ScanResult scan(ChunkVolume volume, List<PositionedId> blocks, List<PositionedId> blockEntities) {
        Map<String, Integer> buried = new TreeMap<>();
        Map<String, Integer> exposed = new TreeMap<>();
        List<BlockEntityRecord> entities = new ArrayList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (PositionedId b : blocks) {
            if (!trackedBlocks.contains(b.id()) || !contains(volume, b)) continue;
            (isBuried(volume, b) ? buried : exposed).merge(b.id(), 1, Integer::sum);
            minY = Math.min(minY, b.y());
            maxY = Math.max(maxY, b.y());
        }
        for (PositionedId e : blockEntities) {
            if (!trackedBlockEntities.contains(e.id()) || !contains(volume, e)) continue;
            entities.add(new BlockEntityRecord(e.id(), e.x(), e.y(), e.z(), isBuried(volume, e)));
            minY = Math.min(minY, e.y());
            maxY = Math.max(maxY, e.y());
        }
        return new ScanResult(buried, exposed, entities, minY, maxY);
    }

    private boolean isBuried(ChunkVolume v, PositionedId p) {
        return p.y() <= v.surfaceY(p.x() - v.originX(), p.z() - v.originZ()) - buriedDepth;
    }

    private static boolean contains(ChunkVolume v, PositionedId p) {
        int lx = p.x() - v.originX();
        int lz = p.z() - v.originZ();
        return lx >= 0 && lx < ChunkVolume.SIZE && lz >= 0 && lz < ChunkVolume.SIZE
            && p.y() >= v.minY() && p.y() <= v.maxY();
    }

    private static Set<String> positiveKeys(Map<String, Double> weights) {
        Set<String> keys = new java.util.HashSet<>();
        weights.forEach((k, v) -> {
            if (v != null && v > 0) keys.add(k);
        });
        return keys;
    }
}
