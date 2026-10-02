package com.auditlab.mod.analysis.model;

/** Kinds of dynamic server-to-client packets the listener records. */
public enum EventType {
    BLOCK_CHANGE("Block change"),
    BLOCK_ENTITY_DATA("Block entity data"),
    BLOCK_EVENT("Block event"),
    WORLD_EVENT("World event"),
    SOUND("Sound"),
    PARTICLE("Particle"),
    ENTITY_SPAWN("Entity spawn");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
