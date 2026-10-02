package com.auditlab.mod.config;

import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.analysis.model.ScoreCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All user-tunable settings. A plain Gson-serialisable bean persisted as
 * {@code config/auditlab.json}; call {@link #normalize()} after loading to fill gaps and clamp
 * out-of-range values.
 */
public final class AuditLabConfig {
    // --- UI toggles -------------------------------------------------------------------------
    public boolean overlayEnabled = true;
    public boolean labelsEnabled = true;
    public int renderDistanceChunks = 12;
    public int labelReasonLines = 2;
    public int maxLabels = 48;

    // --- Safety -----------------------------------------------------------------------------
    /** Servers you operate. Collection only runs here and in singleplayer. */
    public List<String> allowedServers = new ArrayList<>(List.of("localhost", "127.0.0.1"));

    // --- Thresholds -------------------------------------------------------------------------
    public int mediumThreshold = 30;
    public int highThreshold = 60;
    /** A block counts as buried if it is at least this many blocks below its column surface. */
    public int buriedDepth = 6;
    /** Also score tracked blocks/block entities that are near the surface (normally visible). */
    public boolean scoreExposedBlocks = false;
    /** Dynamic events closer than this to the local player are ignored (self-inflicted noise). */
    public double ignoreRadiusAroundPlayer = 8.0;

    // --- Scoring weights --------------------------------------------------------------------
    public Map<ScoreCategory, Integer> categoryCaps = defaultCaps();
    /** Non-block-entity blocks; block-entity blocks are scored via {@link #blockEntityWeights}. */
    public Map<String, Double> blockWeights = defaultBlockWeights();
    public Map<String, Double> blockEntityWeights = defaultBlockEntityWeights();
    public Map<EventType, Double> eventWeights = defaultEventWeights();
    /** Overrides {@code eventWeights[ENTITY_SPAWN]} per entity type; mobs default to 0. */
    public Map<String, Double> entityWeights = defaultEntityWeights();
    public Map<ExposureContext, Double> contextMultipliers = defaultContextMultipliers();
    /** {@code SoundCategory} enum names whose sounds are recorded. */
    public List<String> trackedSoundCategories = new ArrayList<>(List.of("BLOCKS", "PLAYERS", "RECORDS"));

    // --- Geometry heuristics ----------------------------------------------------------------
    public Geometry geometry = new Geometry();

    // --- Limits -----------------------------------------------------------------------------
    public int maxTrackedChunks = 16_384;
    public int eventLogLimit = 128;

    public static final class Geometry {
        public int minVolume = 12;
        public int maxVolume = 4096;
        public int minHeight = 2;
        public double minFillRatio = 0.80;
        public double minPlanarity = 0.85;
        public double roomPoints = 12.0;
        public double corridorPoints = 6.0;
        public double shaftPoints = 4.0;
    }

    public int cap(ScoreCategory category) {
        return categoryCaps.getOrDefault(category, category.defaultCap());
    }

    public double blockWeight(String id) {
        return blockWeights.getOrDefault(id, 0.0);
    }

    public double blockEntityWeight(String id) {
        return blockEntityWeights.getOrDefault(id, 0.0);
    }

    public double eventWeight(EventType type, String subject) {
        double base = eventWeights.getOrDefault(type, 0.0);
        if (type == EventType.ENTITY_SPAWN) return entityWeights.getOrDefault(subject, base);
        return base;
    }

    public double contextMultiplier(ExposureContext context) {
        return contextMultipliers.getOrDefault(context, 0.0);
    }

    /** Fills missing sections with defaults and clamps values into valid ranges. Returns this. */
    public AuditLabConfig normalize() {
        if (allowedServers == null) allowedServers = new ArrayList<>();
        if (categoryCaps == null) categoryCaps = defaultCaps();
        if (blockWeights == null) blockWeights = defaultBlockWeights();
        if (blockEntityWeights == null) blockEntityWeights = defaultBlockEntityWeights();
        if (eventWeights == null) eventWeights = defaultEventWeights();
        if (entityWeights == null) entityWeights = defaultEntityWeights();
        if (contextMultipliers == null) contextMultipliers = defaultContextMultipliers();
        if (trackedSoundCategories == null) trackedSoundCategories = new ArrayList<>();
        if (geometry == null) geometry = new Geometry();

        categoryCaps = new LinkedHashMap<>(categoryCaps);
        categoryCaps.values().removeIf(v -> v == null);
        categoryCaps.replaceAll((k, v) -> clamp(v, 0, 100));
        blockWeights = nonNegative(blockWeights);
        blockEntityWeights = nonNegative(blockEntityWeights);
        eventWeights = nonNegative(eventWeights);
        entityWeights = nonNegative(entityWeights);
        contextMultipliers = nonNegative(contextMultipliers);

        renderDistanceChunks = clamp(renderDistanceChunks, 1, 64);
        labelReasonLines = clamp(labelReasonLines, 0, 8);
        maxLabels = clamp(maxLabels, 0, 512);
        highThreshold = clamp(highThreshold, 1, 100);
        mediumThreshold = clamp(mediumThreshold, 1, highThreshold);
        buriedDepth = clamp(buriedDepth, 0, 64);
        if (!(ignoreRadiusAroundPlayer >= 0)) ignoreRadiusAroundPlayer = 0;
        maxTrackedChunks = clamp(maxTrackedChunks, 64, 1_000_000);
        eventLogLimit = clamp(eventLogLimit, 0, 10_000);

        Geometry g = geometry;
        g.minHeight = clamp(g.minHeight, 1, 64);
        g.minVolume = clamp(g.minVolume, 1, 1 << 20);
        g.maxVolume = Math.max(g.minVolume, g.maxVolume);
        g.minFillRatio = clamp01(g.minFillRatio);
        g.minPlanarity = clamp01(g.minPlanarity);
        g.roomPoints = Math.max(0, g.roomPoints);
        g.corridorPoints = Math.max(0, g.corridorPoints);
        g.shaftPoints = Math.max(0, g.shaftPoints);
        return this;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    private static <K> Map<K, Double> nonNegative(Map<K, Double> in) {
        Map<K, Double> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (k != null && v != null && Double.isFinite(v)) out.put(k, Math.max(0, v));
        });
        return out;
    }

    private static Map<ScoreCategory, Integer> defaultCaps() {
        Map<ScoreCategory, Integer> m = new LinkedHashMap<>();
        for (ScoreCategory c : ScoreCategory.values()) m.put(c, c.defaultCap());
        return m;
    }

    private static Map<String, Double> defaultBlockWeights() {
        Map<String, Double> m = new LinkedHashMap<>();
        // Utility
        put(m, 2.0, "crafting_table", "smithing_table", "fletching_table");
        put(m, 3.0, "anvil", "chipped_anvil", "damaged_anvil");
        put(m, 1.5, "grindstone", "stonecutter", "loom", "cartography_table");
        put(m, 4.0, "respawn_anchor");
        // Redstone
        put(m, 0.5, "redstone_wire", "lever", "redstone_torch", "redstone_wall_torch", "tripwire_hook");
        put(m, 1.0, "repeater", "observer", "piston", "redstone_lamp", "target", "note_block");
        put(m, 1.5, "sticky_piston");
        // Placed building / lighting blocks rare in natural generation
        put(m, 0.3, "torch", "wall_torch", "scaffolding", "ladder");
        put(m, 0.5, "glass", "glass_pane", "iron_bars", "iron_door", "iron_trapdoor");
        return m;
    }

    private static Map<String, Double> defaultBlockEntityWeights() {
        Map<String, Double> m = new LinkedHashMap<>();
        put(m, 4.0, "chest", "trapped_chest", "barrel", "ender_chest", "enchanting_table");
        // Block entity types are colour-independent: all shulker boxes are minecraft:shulker_box.
        put(m, 8.0, "shulker_box", "beacon");
        put(m, 3.0, "brewing_stand", "bed", "crafter");
        put(m, 2.0, "furnace", "blast_furnace", "smoker", "hopper", "banner");
        put(m, 1.0, "sign", "hanging_sign", "dispenser", "dropper", "comparator", "lectern", "jukebox");
        return m;
    }

    private static Map<EventType, Double> defaultEventWeights() {
        Map<EventType, Double> m = new LinkedHashMap<>();
        m.put(EventType.BLOCK_CHANGE, 0.5);
        m.put(EventType.BLOCK_ENTITY_DATA, 1.0);
        m.put(EventType.BLOCK_EVENT, 1.5);
        m.put(EventType.WORLD_EVENT, 1.0);
        m.put(EventType.SOUND, 1.0);
        m.put(EventType.PARTICLE, 0.25);
        m.put(EventType.ENTITY_SPAWN, 0.0);
        return m;
    }

    private static Map<String, Double> defaultEntityWeights() {
        Map<String, Double> m = new LinkedHashMap<>();
        put(m, 2.0, "item_frame", "glow_item_frame", "armor_stand", "end_crystal");
        put(m, 1.0, "chest_minecart", "hopper_minecart", "tnt", "tnt_minecart", "painting");
        put(m, 0.5, "item", "experience_orb", "falling_block");
        return m;
    }

    private static Map<ExposureContext, Double> defaultContextMultipliers() {
        Map<ExposureContext, Double> m = new LinkedHashMap<>();
        m.put(ExposureContext.UNLOADED_CHUNK, 1.5);
        m.put(ExposureContext.UNDERGROUND, 1.0);
        m.put(ExposureContext.SURFACE, 0.0);
        return m;
    }

    private static void put(Map<String, Double> m, double weight, String... paths) {
        for (String p : paths) m.put("minecraft:" + p, weight);
    }
}
