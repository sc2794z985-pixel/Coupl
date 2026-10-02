package com.anticheatlab.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable per-chunk aggregate of {@link LeakEvent}s. Scanners write from the network thread
 * while the GUI and renderer read from the render thread, so every access is synchronized.
 * Readers receive copies, never live views.
 */
public final class ChunkObservation {
    /** Upper bound on remembered positions per type; beyond it, deduping is best-effort. */
    public static final int MAX_TRACKED_POSITIONS = 4096;
    public static final int RECENT_EVENT_LIMIT = 64;

    private final ChunkCoord coord;
    private final EnumMap<LeakType, Integer> counts = new EnumMap<>(LeakType.class);
    private final EnumMap<LeakType, Set<Long>> seenPositions = new EnumMap<>(LeakType.class);
    private final ArrayDeque<LeakEvent> recent = new ArrayDeque<>();
    private long firstSeenMillis = -1;
    private long lastSeenMillis = -1;
    private long revision;

    public ChunkObservation(ChunkCoord coord) {
        this.coord = Objects.requireNonNull(coord, "coord");
    }

    public ChunkCoord coord() {
        return coord;
    }

    /**
     * Adds an event to this chunk.
     *
     * @return {@code true} if it changed the counts, {@code false} if it was a duplicate of an
     *     already-counted static observation
     * @throws IllegalArgumentException if the event belongs to a different chunk
     */
    public synchronized boolean record(LeakEvent event) {
        if (!coord.equals(event.chunk())) {
            throw new IllegalArgumentException("Event " + event + " is outside chunk " + coord);
        }

        if (event.type().dedupeByPosition()) {
            Set<Long> seen = seenPositions.computeIfAbsent(event.type(), t -> new HashSet<>());
            if (seen.size() < MAX_TRACKED_POSITIONS) {
                if (!seen.add(event.packedPos())) return false;
            } else if (seen.contains(event.packedPos())) {
                return false;
            }
        }

        counts.merge(event.type(), 1, Integer::sum);
        if (firstSeenMillis < 0) firstSeenMillis = event.timestampMillis();
        lastSeenMillis = Math.max(lastSeenMillis, event.timestampMillis());

        recent.addLast(event);
        if (recent.size() > RECENT_EVENT_LIMIT) recent.removeFirst();

        revision++;
        return true;
    }

    public synchronized int count(LeakType type) {
        return counts.getOrDefault(type, 0);
    }

    public synchronized Map<LeakType, Integer> counts() {
        return counts.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new EnumMap<>(counts));
    }

    public synchronized int totalCount() {
        int total = 0;
        for (int c : counts.values()) total += c;
        return total;
    }

    /** Most recent counted events, oldest first. */
    public synchronized List<LeakEvent> recentEvents() {
        return List.copyOf(new ArrayList<>(recent));
    }

    /** Epoch millis of the first counted event, or -1 if none. */
    public synchronized long firstSeenMillis() {
        return firstSeenMillis;
    }

    /** Epoch millis of the latest counted event, or -1 if none. */
    public synchronized long lastSeenMillis() {
        return lastSeenMillis;
    }

    /** Increments on every counted event; lets consumers cache derived values such as scores. */
    public synchronized long revision() {
        return revision;
    }
}
