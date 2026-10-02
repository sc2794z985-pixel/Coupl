package com.auditlab.mod.analysis.model;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Raw, uninterpreted record of what the server sent about one chunk. Holds no scores or
 * inferences; those live in {@link ChunkAnalysis}.
 *
 * <p>Chunk-scan data (block tallies, block entities) is replaced on every scan because the
 * server re-sends whole chunks and the latest copy is authoritative. Dynamic events accumulate,
 * because their frequency is the signal.
 *
 * <p>Written from the analysis worker and the client thread, read from the render thread, so
 * all access is synchronized and getters return copies.
 */
public final class ChunkObservation {
    /** Aggregation key for dynamic events. */
    public record EventKey(EventType type, ExposureContext context, String subject) {
    }

    private final ChunkKey key;
    private final int eventLogLimit;

    private Map<String, Integer> buriedBlocks = Map.of();
    private Map<String, Integer> exposedBlocks = Map.of();
    private Map<Long, BlockEntityRecord> blockEntities = Map.of();
    private int scanMinY = Integer.MAX_VALUE;
    private int scanMaxY = Integer.MIN_VALUE;
    private int scanCount;

    private final Map<EventKey, Integer> eventCounts = new HashMap<>();
    private final ArrayDeque<ObservedEvent> eventLog = new ArrayDeque<>();
    private int eventMinY = Integer.MAX_VALUE;
    private int eventMaxY = Integer.MIN_VALUE;
    private long eventTotal;

    private long firstSeenMillis = -1;
    private long lastSeenMillis = -1;
    private long revision;

    public ChunkObservation(ChunkKey key, int eventLogLimit) {
        this.key = Objects.requireNonNull(key, "key");
        this.eventLogLimit = Math.max(0, eventLogLimit);
    }

    public ChunkKey key() {
        return key;
    }

    /** Replaces chunk-scan data with the result of a fresh scan. */
    public synchronized void applyScan(Map<String, Integer> buried, Map<String, Integer> exposed,
                                       List<BlockEntityRecord> entities, int minY, int maxY, long nowMillis) {
        buriedBlocks = Collections.unmodifiableMap(new TreeMap<>(buried));
        exposedBlocks = Collections.unmodifiableMap(new TreeMap<>(exposed));
        Map<Long, BlockEntityRecord> be = new LinkedHashMap<>();
        for (BlockEntityRecord r : entities) be.put(r.packedPos(), r);
        blockEntities = Collections.unmodifiableMap(be);
        scanMinY = minY;
        scanMaxY = maxY;
        scanCount++;
        touch(nowMillis);
    }

    public synchronized void recordEvent(ObservedEvent event) {
        if (!key.equals(event.chunk(key.dimension()))) {
            throw new IllegalArgumentException("Event at " + event.x() + "," + event.z() + " is outside " + key);
        }
        eventCounts.merge(new EventKey(event.type(), event.context(), event.subject()), 1, Integer::sum);
        eventTotal++;
        if (eventLogLimit > 0) {
            eventLog.addLast(event);
            while (eventLog.size() > eventLogLimit) eventLog.removeFirst();
        }
        eventMinY = Math.min(eventMinY, event.y());
        eventMaxY = Math.max(eventMaxY, event.y());
        touch(event.timestampMillis());
    }

    private void touch(long nowMillis) {
        if (firstSeenMillis < 0) firstSeenMillis = nowMillis;
        lastSeenMillis = Math.max(lastSeenMillis, nowMillis);
        revision++;
    }

    public synchronized Map<String, Integer> buriedBlocks() {
        return buriedBlocks;
    }

    public synchronized Map<String, Integer> exposedBlocks() {
        return exposedBlocks;
    }

    public synchronized List<BlockEntityRecord> blockEntities() {
        return List.copyOf(blockEntities.values());
    }

    public synchronized Map<EventKey, Integer> eventCounts() {
        return Map.copyOf(eventCounts);
    }

    /** Most recent events, oldest first, bounded by the configured log limit. */
    public synchronized List<ObservedEvent> eventLog() {
        return List.copyOf(eventLog);
    }

    public synchronized long eventTotal() {
        return eventTotal;
    }

    public synchronized int scanCount() {
        return scanCount;
    }

    /** Lowest Y of tracked blocks, block entities or events; {@link Integer#MAX_VALUE} if none. */
    public synchronized int minY() {
        return Math.min(scanMinY, eventMinY);
    }

    /** Highest Y of tracked blocks, block entities or events; {@link Integer#MIN_VALUE} if none. */
    public synchronized int maxY() {
        return Math.max(scanMaxY, eventMaxY);
    }

    public synchronized long firstSeenMillis() {
        return firstSeenMillis;
    }

    public synchronized long lastSeenMillis() {
        return lastSeenMillis;
    }

    /** Increments on every change; lets consumers detect staleness. */
    public synchronized long revision() {
        return revision;
    }
}
