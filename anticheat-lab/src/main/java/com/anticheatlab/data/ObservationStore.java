package com.anticheatlab.data;

import com.anticheatlab.model.ChunkCoord;
import com.anticheatlab.model.ChunkObservation;
import com.anticheatlab.model.ChunkScore;
import com.anticheatlab.model.LeakEvent;
import com.anticheatlab.scoring.ScoreCalculator;
import com.anticheatlab.scoring.ScoringConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, in-memory index of observed chunks. Bounded: when {@code maxChunks} is exceeded,
 * the least recently updated tenth of the chunks is evicted. Nothing is written to disk.
 */
public final class ObservationStore {
    public static final int DEFAULT_MAX_CHUNKS = 16_384;

    private final ConcurrentHashMap<ChunkCoord, ChunkObservation> chunks = new ConcurrentHashMap<>();
    private final AtomicLong countedEvents = new AtomicLong();
    private final AtomicLong duplicateEvents = new AtomicLong();
    private final int maxChunks;

    public ObservationStore() {
        this(DEFAULT_MAX_CHUNKS);
    }

    public ObservationStore(int maxChunks) {
        if (maxChunks < 1) throw new IllegalArgumentException("maxChunks must be >= 1");
        this.maxChunks = maxChunks;
    }

    /** @return {@code true} if the event was counted (not a duplicate). */
    public boolean record(LeakEvent event) {
        ChunkObservation obs = chunks.computeIfAbsent(event.chunk(), ChunkObservation::new);
        boolean counted = obs.record(event);
        (counted ? countedEvents : duplicateEvents).incrementAndGet();
        if (chunks.size() > maxChunks) evictOldest();
        return counted;
    }

    public Optional<ChunkObservation> get(ChunkCoord coord) {
        return Optional.ofNullable(chunks.get(coord));
    }

    public Optional<ChunkScore> score(ChunkCoord coord, ScoringConfig config) {
        return get(coord).map(obs -> ScoreCalculator.score(obs, config));
    }

    /** Highest-scoring chunks first; ties broken by most recent activity. Excludes zero scores. */
    public List<ChunkScore> topScores(int limit, ScoringConfig config) {
        record Ranked(ChunkScore score, long lastSeen) {
        }
        List<Ranked> ranked = new ArrayList<>();
        for (ChunkObservation obs : chunks.values()) {
            ChunkScore s = ScoreCalculator.score(obs, config);
            if (s.total() > 0) ranked.add(new Ranked(s, obs.lastSeenMillis()));
        }
        ranked.sort(Comparator.comparingInt((Ranked r) -> r.score().total()).reversed()
            .thenComparing(Comparator.comparingLong(Ranked::lastSeen).reversed()));
        return ranked.stream().limit(Math.max(0, limit)).map(Ranked::score).toList();
    }

    public int size() {
        return chunks.size();
    }

    public long countedEvents() {
        return countedEvents.get();
    }

    public long duplicateEvents() {
        return duplicateEvents.get();
    }

    public void clear() {
        chunks.clear();
        countedEvents.set(0);
        duplicateEvents.set(0);
    }

    private synchronized void evictOldest() {
        int excess = chunks.size() - maxChunks;
        if (excess <= 0) return;
        int toRemove = Math.max(excess, maxChunks / 10);
        chunks.values().stream()
            .sorted(Comparator.comparingLong(ChunkObservation::lastSeenMillis))
            .limit(toRemove)
            .map(ChunkObservation::coord)
            .toList()
            .forEach(chunks::remove);
    }
}
