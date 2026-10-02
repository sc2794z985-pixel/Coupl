package com.auditlab.mod.safety;

import java.util.Collection;
import java.util.Locale;

/**
 * Collection runs only in singleplayer and on servers the operator has listed in
 * {@code allowedServers}, so the auditor is only ever pointed at infrastructure under test.
 */
public final class AccessPolicy {
    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    private AccessPolicy() {
    }

    public static boolean isAuthorized(boolean singleplayer, String serverAddress, Collection<String> allowlist) {
        if (singleplayer) return true;
        if (serverAddress == null || serverAddress.isBlank() || allowlist == null) return false;
        String target = normalize(serverAddress);
        for (String entry : allowlist) {
            if (entry != null && !entry.isBlank() && normalize(entry).equals(target)) return true;
        }
        return false;
    }

    /** Lowercases, trims, strips the default port and a trailing dot on the host. */
    public static String normalize(String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(DEFAULT_PORT_SUFFIX)) a = a.substring(0, a.length() - DEFAULT_PORT_SUFFIX.length());
        if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
        return a;
    }
}
