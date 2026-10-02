package com.auditlab.mod.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    private static final List<String> ALLOW = List.of("localhost", "Test.MyServer.net", "10.0.0.5:25570");

    @Test
    void singleplayerAlwaysAllowed() {
        assertTrue(AccessPolicy.isAuthorized(true, null, null));
    }

    @Test
    void matchesNormalizedAddresses() {
        assertTrue(AccessPolicy.isAuthorized(false, "localhost:25565", ALLOW));
        assertTrue(AccessPolicy.isAuthorized(false, " test.myserver.net. ", ALLOW));
        assertTrue(AccessPolicy.isAuthorized(false, "10.0.0.5:25570", ALLOW));
    }

    @Test
    void rejectsEverythingElse() {
        assertFalse(AccessPolicy.isAuthorized(false, "10.0.0.5", ALLOW));
        assertFalse(AccessPolicy.isAuthorized(false, "play.example.org", ALLOW));
        assertFalse(AccessPolicy.isAuthorized(false, null, ALLOW));
        assertFalse(AccessPolicy.isAuthorized(false, "localhost", null));
    }
}
