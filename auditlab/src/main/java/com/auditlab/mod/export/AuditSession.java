package com.auditlab.mod.export;

import com.auditlab.mod.analysis.ChunkAnalyzer;
import com.auditlab.mod.config.AuditLabConfig;

import java.util.Objects;
import java.util.UUID;

/**
 * One continuous audit of one target (server address or "singleplayer"), from join to
 * disconnect. Owns the {@link ChunkAnalyzer} holding everything observed in that window.
 */
public final class AuditSession {
    private final UUID id = UUID.randomUUID();
    private final String target;
    private final long startedAtMillis;
    private final ChunkAnalyzer analyzer;
    private volatile long endedAtMillis = -1;

    public AuditSession(String target, AuditLabConfig config, long startedAtMillis) {
        this.target = Objects.requireNonNull(target, "target");
        this.startedAtMillis = startedAtMillis;
        this.analyzer = new ChunkAnalyzer(config);
    }

    public UUID id() {
        return id;
    }

    public String target() {
        return target;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    /** -1 while the session is running. */
    public long endedAtMillis() {
        return endedAtMillis;
    }

    public boolean isActive() {
        return endedAtMillis < 0;
    }

    public ChunkAnalyzer analyzer() {
        return analyzer;
    }

    void end(long nowMillis) {
        if (endedAtMillis < 0) endedAtMillis = nowMillis;
    }
}
