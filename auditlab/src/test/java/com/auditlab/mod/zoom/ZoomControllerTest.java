package com.auditlab.mod.zoom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoomControllerTest {
    private static final long MS = 1_000_000L;

    @Test
    void noZoomLeavesFovUntouched() {
        ZoomController z = new ZoomController();
        assertEquals(70f, z.applyFov(70f, 0));
        assertEquals(70f, z.applyFov(70f, 500 * MS));
    }

    @Test
    void holdingEasesToDefaultFactorAndReleaseEasesBack() {
        ZoomController z = new ZoomController();
        z.setDefaultFactor(4);
        z.applyFov(70f, 0);
        z.setHeld(true);
        float mid = z.applyFov(70f, 20 * MS);
        assertTrue(mid < 70f && mid > 17.5f, "eases rather than jumping: " + mid);
        assertEquals(17.5f, z.applyFov(70f, 1000 * MS), 0.01f);

        z.setHeld(false);
        assertEquals(70f, z.applyFov(70f, 2000 * MS), 0.01f);
    }

    @Test
    void scrollAdjustsOnlyWhileHeldAndIsClamped() {
        ZoomController z = new ZoomController();
        z.setDefaultFactor(4);
        assertFalse(z.scroll(1), "scrolling without zoom keeps hotbar behaviour");
        z.setHeld(true);
        assertTrue(z.scroll(1));
        assertEquals(5.0, z.targetFactor(), 1e-9);
        for (int i = 0; i < 100; i++) z.scroll(1);
        assertEquals(ZoomController.MAX_FACTOR, z.targetFactor());
        for (int i = 0; i < 100; i++) z.scroll(-1);
        assertEquals(ZoomController.MIN_FACTOR, z.targetFactor());

        z.setHeld(false);
        z.setHeld(true);
        assertEquals(4.0, z.targetFactor(), 1e-9, "each press starts at the default factor");
    }

    @Test
    void defaultFactorIsClamped() {
        ZoomController z = new ZoomController();
        z.setDefaultFactor(1000);
        z.setHeld(true);
        assertEquals(ZoomController.MAX_FACTOR, z.targetFactor());
        z.setDefaultFactor(Double.NaN);
        z.setHeld(false);
        z.setHeld(true);
        assertEquals(ZoomController.MIN_FACTOR, z.targetFactor());
    }
}
