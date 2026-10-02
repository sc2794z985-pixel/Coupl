package com.auditlab.mod.export;

import com.auditlab.mod.config.AuditLabConfig;

/**
 * Tracks the running session and keeps the most recently finished one available for
 * inspection and (Phase 2) export.
 */
public final class SessionManager {
    private volatile AuditSession current;
    private volatile AuditSession last;

    public synchronized AuditSession start(String target, AuditLabConfig config, long nowMillis) {
        end(nowMillis);
        current = new AuditSession(target, config, nowMillis);
        return current;
    }

    public synchronized void end(long nowMillis) {
        if (current != null) {
            current.end(nowMillis);
            last = current;
            current = null;
        }
    }

    /** The running session, or {@code null} when not collecting. */
    public AuditSession current() {
        return current;
    }

    /** The running session if any, otherwise the most recently finished one, or {@code null}. */
    public AuditSession latest() {
        AuditSession c = current;
        return c != null ? c : last;
    }

    public synchronized void applyConfig(AuditLabConfig config) {
        if (current != null) current.analyzer().updateConfig(config);
        if (last != null) last.analyzer().updateConfig(config);
    }
}
