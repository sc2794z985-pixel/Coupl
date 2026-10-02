package com.auditlab.mod.analysis.model;

import java.util.Objects;

/**
 * One raw dynamic observation taken from a packet the client received.
 *
 * @param subject registry id of what the packet referred to (block, sound, particle, entity
 *                type, or {@code world_event:<id>}); used for weights and the breakdown
 */
public record ObservedEvent(EventType type, ExposureContext context, int x, int y, int z, long timestampMillis, String subject) {
    public ObservedEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(context, "context");
        subject = subject == null ? "" : subject;
    }

    public ChunkKey chunk(String dimension) {
        return ChunkKey.ofBlock(dimension, x, z);
    }
}
