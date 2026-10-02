package com.auditlab.mod.analysis.model;

/**
 * Inference produced by {@code GeometryAnalyzer}: an enclosed air volume whose shape looks
 * man-made. All coordinates are world block coordinates (max inclusive).
 *
 * @param fillRatio       volume / bounding-box volume; 1.0 for a perfect box
 * @param floorFlatness   share of floor cells lying on the most common floor level
 * @param ceilingFlatness share of ceiling cells lying on the most common ceiling level
 * @param wallAlignment   share of side wall faces lying on the dominant plane per direction
 * @param confidence      0..1 combination of the above
 * @param clipped         touches the chunk border, so only part of the volume was seen
 * @param opensToSurface  connects to sky-exposed air
 */
public record CavityFinding(
    Kind kind,
    int minX, int minY, int minZ,
    int maxX, int maxY, int maxZ,
    int volume,
    double fillRatio,
    double floorFlatness,
    double ceilingFlatness,
    double wallAlignment,
    double confidence,
    boolean clipped,
    boolean opensToSurface
) {
    public enum Kind {
        ROOM("room"),
        CORRIDOR("corridor"),
        SHAFT("shaft");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
