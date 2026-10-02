package com.anticheatlab.model;

/**
 * A kind of information the server sent that a masking plugin may have intended to hide.
 *
 * <p>{@code dedupeByPosition} decides how repeats are counted. Static observations (a block
 * or block entity present in chunk data) are counted once per position, because the server
 * legitimately re-sends chunks when the player moves back into range. Dynamic observations
 * (updates, block events, sounds) are counted every time they arrive, because their
 * frequency is itself the signal.
 */
public enum LeakType {
    PLACED_BLOCK(ScoreCategory.SUSPICIOUS_BLOCKS, 1.0, true,
        "Block type that does not generate naturally at this location"),
    FUNCTIONAL_BLOCK(ScoreCategory.SUSPICIOUS_BLOCKS, 3.0, true,
        "Crafted utility block (crafting table, furnace, bed, anvil, ...)"),
    STORAGE_BLOCK_ENTITY(ScoreCategory.BLOCK_ENTITIES, 5.0, true,
        "Storage block entity (chest, barrel, shulker box, ...)"),
    OTHER_BLOCK_ENTITY(ScoreCategory.BLOCK_ENTITIES, 2.0, true,
        "Other block entity (sign, banner, hopper, ...)"),
    CAVITY(ScoreCategory.CAVITY_GEOMETRY, 4.0, true,
        "Enclosed air volume with artificial geometry"),
    BLOCK_UPDATE(ScoreCategory.UPDATE_ANOMALIES, 0.5, false,
        "Single or multi block-change packet"),
    BLOCK_ENTITY_UPDATE(ScoreCategory.UPDATE_ANOMALIES, 1.0, false,
        "Block entity data update packet"),
    BLOCK_EVENT(ScoreCategory.UPDATE_ANOMALIES, 1.5, false,
        "Block event packet (chest lid, piston, note block, ...)"),
    BLOCK_SOUND(ScoreCategory.SOUNDS, 1.0, false,
        "Positional sound originating from a block");

    private final ScoreCategory category;
    private final double defaultWeight;
    private final boolean dedupeByPosition;
    private final String description;

    LeakType(ScoreCategory category, double defaultWeight, boolean dedupeByPosition, String description) {
        this.category = category;
        this.defaultWeight = defaultWeight;
        this.dedupeByPosition = dedupeByPosition;
        this.description = description;
    }

    public ScoreCategory category() {
        return category;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    public boolean dedupeByPosition() {
        return dedupeByPosition;
    }

    public String description() {
        return description;
    }
}
