package com.anticheatlab.safety;

import java.util.Collection;
import java.util.Locale;

/**
 * Restricts data collection to singleplayer and to servers the operator has explicitly listed,
 * so the lab is only ever pointed at infrastructure under test.
 */
public final class AuthorizationPolicy {
    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    private AuthorizationPolicy() {
    }

    public static boolean isAuthorized(boolean singleplayer, String serverAddress, Collection<String> allowlist) {
        if (singleplayer) return true;
        if (serverAddress == null || serverAddress.isBlank()) return false;
        String target = normalize(serverAddress);
        for (String entry : allowlist) {
            if (entry != null && !entry.isBlank() && normalize(entry).equals(target)) return true;
        }
        return false;
    }

    /** Lowercases, trims, strips a trailing dot on the host and the default port. */
    public static String normalize(String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(DEFAULT_PORT_SUFFIX)) a = a.substring(0, a.length() - DEFAULT_PORT_SUFFIX.length());
        if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
        return a;
    }
}
