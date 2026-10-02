package com.auditlab.mod.analysis.model;

import java.util.List;
import java.util.Objects;

/**
 * Everything inferred about a chunk, kept separate from the raw {@link ChunkObservation}.
 *
 * @param minY lowest Y of anything observed or inferred in the chunk (for the overlay box)
 * @param maxY highest Y of anything observed or inferred in the chunk
 */
public record ChunkAnalysis(ChunkKey key, ChunkScore score, List<CavityFinding> cavities, int minY, int maxY) {
    public ChunkAnalysis {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(score, "score");
        cavities = List.copyOf(cavities);
    }
}
