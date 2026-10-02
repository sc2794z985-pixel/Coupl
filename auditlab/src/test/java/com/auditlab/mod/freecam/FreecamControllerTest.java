package com.auditlab.mod.freecam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FreecamControllerTest {
    private static FreecamController at(float yaw) {
        FreecamController c = new FreecamController();
        c.setSmoothing(0);
        c.setSpeed(1);
        c.activate(0, 64, 0, yaw, 0);
        return c;
    }

    @Test
    void forwardFollowsYaw() {
        FreecamController south = at(0);
        south.tick(true, false, false, false, false, false, false);
        assertEquals(1, south.z(1), 1e-9);
        assertEquals(0, south.x(1), 1e-9);

        FreecamController west = at(90);
        west.tick(true, false, false, false, false, false, false);
        assertEquals(-1, west.x(1), 1e-9);
    }

    @Test
    void strafeRightAndVertical() {
        FreecamController c = at(0); // facing +Z, right-hand side is -X
        c.tick(false, false, false, true, false, false, false);
        assertEquals(-1, c.x(1), 1e-9);
        c.tick(false, false, false, false, true, false, false);
        assertEquals(65, c.y(1), 1e-9);
    }

    @Test
    void diagonalIsNormalisedAndSprintDoubles() {
        FreecamController c = at(0);
        c.tick(true, false, false, true, false, false, true);
        double dx = c.x(1), dz = c.z(1);
        assertEquals(2.0, Math.sqrt(dx * dx + dz * dz), 1e-9);
    }

    @Test
    void smoothingEasesInAndOutAndInterpolates() {
        FreecamController c = new FreecamController();
        c.setSpeed(1);
        c.setSmoothing(0.5);
        c.activate(0, 0, 0, 0, 0);
        c.tick(true, false, false, false, false, false, false);
        assertEquals(0.5, c.z(1), 1e-9);
        assertEquals(0.25, c.z(0.5f), 1e-9);
        c.tick(true, false, false, false, false, false, false);
        assertEquals(0.5 + 0.75, c.z(1), 1e-9);
        c.tick(false, false, false, false, false, false, false);
        assertTrue(c.z(1) > 1.25, "keeps gliding after release");
    }

    @Test
    void lookClampsPitchAndInactiveDoesNotMove() {
        FreecamController c = at(0);
        c.look(100, 10_000);
        assertEquals(15f, c.yaw(), 1e-4);
        assertEquals(90f, c.pitch());
        c.deactivate();
        c.tick(true, false, false, false, false, false, false);
        assertEquals(0, c.z(1), 1e-9);
    }

    @Test
    void speedScrollIsClamped() {
        FreecamController c = at(0);
        for (int i = 0; i < 100; i++) c.scrollSpeed(1);
        assertEquals(FreecamController.MAX_SPEED, c.speed());
        for (int i = 0; i < 100; i++) c.scrollSpeed(-1);
        assertEquals(FreecamController.MIN_SPEED, c.speed());
    }
}
