package com.anticheatlab.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationPolicyTest {
    private static final List<String> ALLOW = List.of("localhost", "Test.MyServer.net", "10.0.0.5:25570");

    @Test
    void singleplayerIsAlwaysAllowed() {
        assertTrue(AuthorizationPolicy.isAuthorized(true, null, List.of()));
    }

    @Test
    void matchesNormalizedAddresses() {
        assertTrue(AuthorizationPolicy.isAuthorized(false, "localhost:25565", ALLOW));
        assertTrue(AuthorizationPolicy.isAuthorized(false, " test.myserver.net. ", ALLOW));
        assertTrue(AuthorizationPolicy.isAuthorized(false, "10.0.0.5:25570", ALLOW));
    }

    @Test
    void rejectsUnlistedOrMissingAddresses() {
        assertFalse(AuthorizationPolicy.isAuthorized(false, "10.0.0.5", ALLOW));
        assertFalse(AuthorizationPolicy.isAuthorized(false, "other.net", ALLOW));
        assertFalse(AuthorizationPolicy.isAuthorized(false, null, ALLOW));
        assertFalse(AuthorizationPolicy.isAuthorized(false, "", ALLOW));
    }
}
