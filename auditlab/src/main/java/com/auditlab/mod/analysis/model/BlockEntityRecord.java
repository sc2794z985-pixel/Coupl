package com.auditlab.mod.analysis.model;

import java.util.Objects;

/**
 * A block entity present in data the client received.
 *
 * @param buried whether it sits at least {@code buriedDepth} blocks below the column surface
 */
public record BlockEntityRecord(String typeId, int x, int y, int z, boolean buried) {
    public BlockEntityRecord {
        Objects.requireNonNull(typeId, "typeId");
    }

    public long packedPos() {
        return BlockPositions.pack(x, y, z);
    }
}
