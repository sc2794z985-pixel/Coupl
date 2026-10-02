package com.auditlab.mod.analysis;

import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ChunkObservation;
import com.auditlab.mod.analysis.model.ChunkScore;
import com.auditlab.mod.analysis.model.ObservedEvent;
import com.auditlab.mod.analysis.model.Severity;
import com.auditlab.mod.config.AuditLabConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-session aggregator keyed by chunk. Owns three maps:
 * <ul>
 *   <li>{@code observations}: raw {@link ChunkObservation}s (what the server sent)</li>
 *   <li>{@code geometry}: {@link CavityFinding}s from the latest scan (inference)</li>
 *   <li>{@code cache}: memoised {@link ChunkAnalysis} (inference), invalidated on every write</li>
 * </ul>
 * Thread-safe: the worker applies scans, the client thread records events, the render thread reads.
 */
public final class ChunkAnalyzer {
    private final ConcurrentHashMap<ChunkKey, ChunkObservation> observations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkKey, List<CavityFinding>> geometry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkKey, ChunkAnalysis> cache = new ConcurrentHashMap<>();
    private final AtomicLong eventsRecorded = new AtomicLong();
    private final AtomicLong scansApplied = new AtomicLong();

    private volatile AuditLabConfig config;
    private volatile ChunkScorer scorer;

    public ChunkAnalyzer(AuditLabConfig config) {
        updateConfig(config);
    }

    public void updateConfig(AuditLabConfig config) {
        this.config = config;
        this.scorer = new ChunkScorer(config);
        cache.clear();
    }

    public void applyScan(ChunkKey key, BlockScanner.ScanResult scan, List<CavityFinding> cavities, long nowMillis) {
        ChunkObservation obs = observation(key);
        obs.applyScan(scan.buriedBlocks(), scan.exposedBlocks(), scan.blockEntities(), scan.minY(), scan.maxY(), nowMillis);
        if (cavities.isEmpty()) geometry.remove(key);
        else geometry.put(key, List.copyOf(cavities));
        scansApplied.incrementAndGet();
        cache.remove(key);
        enforceLimit();
    }

    public void recordEvent(String dimension, ObservedEvent event) {
        ChunkKey key = event.chunk(dimension);
        observation(key).recordEvent(event);
        eventsRecorded.incrementAndGet();
        cache.remove(key);
        enforceLimit();
    }

    public Optional<ChunkObservation> findObservation(ChunkKey key) {
        return Optional.ofNullable(observations.get(key));
    }

    public List<CavityFinding> cavities(ChunkKey key) {
        return geometry.getOrDefault(key, List.of());
    }

    public Optional<ChunkAnalysis> analysis(ChunkKey key) {
        if (!observations.containsKey(key)) return Optional.empty();
        return Optional.ofNullable(cache.computeIfAbsent(key, this::compute));
    }

    /** Analyses with severity above NONE within {@code radius} chunks of {@code center}. */
    public List<ChunkAnalysis> analysesNear(ChunkKey center, int radius) {
        List<ChunkAnalysis> out = new ArrayList<>();
        if ((long) (2 * radius + 1) * (2 * radius + 1) < observations.size()) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    addIfScored(out, new ChunkKey(center.dimension(), center.x() + dx, center.z() + dz));
                }
            }
        } else {
            for (ChunkKey key : observations.keySet()) {
                if (center.chunkDistance(key) <= radius) addIfScored(out, key);
            }
        }
        return out;
    }

    /** Highest scores first, ties broken by most recent activity. */
    public List<ChunkAnalysis> top(int limit) {
        List<ChunkAnalysis> all = new ArrayList<>();
        for (ChunkKey key : observations.keySet()) addIfScored(all, key);
        all.sort(Comparator.comparingInt((ChunkAnalysis a) -> a.score().total()).reversed()
            .thenComparing(Comparator.comparingLong((ChunkAnalysis a) -> lastSeen(a.key())).reversed()));
        return all.subList(0, Math.min(Math.max(0, limit), all.size()));
    }

    public int chunkCount() {
        return observations.size();
    }

    public long eventsRecorded() {
        return eventsRecorded.get();
    }

    public long scansApplied() {
        return scansApplied.get();
    }

    public void clear() {
        observations.clear();
        geometry.clear();
        cache.clear();
        eventsRecorded.set(0);
        scansApplied.set(0);
    }

    private void addIfScored(List<ChunkAnalysis> out, ChunkKey key) {
        analysis(key).filter(a -> a.score().severity() != Severity.NONE).ifPresent(out::add);
    }

    private ChunkObservation observation(ChunkKey key) {
        return observations.computeIfAbsent(key, k -> new ChunkObservation(k, config.eventLogLimit));
    }

    private ChunkAnalysis compute(ChunkKey key) {
        ChunkObservation obs = observations.get(key);
        if (obs == null) return null;
        List<CavityFinding> cavities = cavities(key);
        ChunkScore score = scorer.score(obs, cavities, System.currentTimeMillis());
        int minY = obs.minY();
        int maxY = obs.maxY();
        for (CavityFinding f : cavities) {
            minY = Math.min(minY, f.minY());
            maxY = Math.max(maxY, f.maxY());
        }
        if (minY > maxY) {
            minY = 0;
            maxY = 0;
        }
        return new ChunkAnalysis(key, score, cavities, minY, maxY);
    }

    private long lastSeen(ChunkKey key) {
        ChunkObservation obs = observations.get(key);
        return obs == null ? -1 : obs.lastSeenMillis();
    }

    private void enforceLimit() {
        int limit = config.maxTrackedChunks;
        if (observations.size() <= limit) return;
        synchronized (this) {
            int excess = observations.size() - limit;
            if (excess <= 0) return;
            int toRemove = Math.max(excess, limit / 10);
            observations.values().stream()
                .sorted(Comparator.comparingLong(ChunkObservation::lastSeenMillis))
                .limit(toRemove)
                .map(ChunkObservation::key)
                .toList()
                .forEach(k -> {
                    observations.remove(k);
                    geometry.remove(k);
                    cache.remove(k);
                });
        }
    }
}
