package com.auditlab.mod.analysis.model;

/**
 * Where a dynamic event happened relative to what the player could legitimately see.
 * Surface activity is ordinary visible information; activity underground or in chunks the
 * client has not loaded is what a masking plugin is expected to suppress.
 */
public enum ExposureContext {
    UNLOADED_CHUNK("outside loaded chunks"),
    UNDERGROUND("underground"),
    SURFACE("surface");

    private final String label;

    ExposureContext(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
