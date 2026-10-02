package com.auditlab.mod.export;

import com.auditlab.mod.config.AuditLabConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {
    @Test
    void lifecycle() {
        SessionManager m = new SessionManager();
        assertNull(m.current());
        AuditSession a = m.start("localhost", new AuditLabConfig().normalize(), 100);
        assertSame(a, m.current());
        assertTrue(a.isActive());

        AuditSession b = m.start("singleplayer", new AuditLabConfig().normalize(), 200);
        assertFalse(a.isActive());
        assertEquals(200, a.endedAtMillis());
        assertNotEquals(a.id(), b.id());

        m.end(300);
        assertNull(m.current());
        assertSame(b, m.latest());
        assertEquals(300, b.endedAtMillis());
    }
}
