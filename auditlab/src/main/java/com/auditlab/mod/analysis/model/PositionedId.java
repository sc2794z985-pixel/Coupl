package com.auditlab.mod.analysis.model;

import java.util.Objects;

/** A registry id at a world block position, as extracted from chunk data (block or block entity). */
public record PositionedId(String id, int x, int y, int z) {
    public PositionedId {
        Objects.requireNonNull(id, "id");
    }
}
