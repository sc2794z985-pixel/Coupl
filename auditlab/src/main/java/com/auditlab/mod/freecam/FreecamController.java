package com.auditlab.mod.freecam;

/**
 * Detached camera for cinematic shots. Plain Java so it can be unit-tested; the client thread
 * drives it once per tick from the keyboard input mixin and every frame from the mouse.
 *
 * <p>Movement is creative-flight style: forward/back/strafe follow the yaw on the horizontal
 * plane, jump/sneak move straight up/down, sprint doubles the speed. Velocity eases towards
 * the target so starts and stops are smooth; the camera position is interpolated between ticks.
 */
public final class FreecamController {
    public static final double MIN_SPEED = 0.02;
    public static final double MAX_SPEED = 10.0;
    public static final double SPEED_SCROLL_STEP = 1.25;
    /** Same mouse-to-degrees factor vanilla uses for entity look. */
    private static final double LOOK_SENSITIVITY = 0.15;

    private static final FreecamController INSTANCE = new FreecamController();

    private boolean active;
    private double x, y, z;
    private double prevX, prevY, prevZ;
    private double vx, vy, vz;
    private float yaw, pitch;
    private double speed = 0.5;
    private double smoothing = 0.6;

    public static FreecamController get() {
        return INSTANCE;
    }

    public void activate(double x, double y, double z, float yaw, float pitch) {
        this.x = prevX = x;
        this.y = prevY = y;
        this.z = prevZ = z;
        vx = vy = vz = 0;
        this.yaw = yaw;
        this.pitch = clampPitch(pitch);
        active = true;
    }

    public void deactivate() {
        active = false;
        vx = vy = vz = 0;
    }

    public boolean isActive() {
        return active;
    }

    /** Blocks per tick at walking input; sprint doubles it. */
    public void setSpeed(double blocksPerTick) {
        speed = clampSpeed(blocksPerTick);
    }

    public double speed() {
        return speed;
    }

    /** 0 = instant response, close to 1 = very floaty. */
    public void setSmoothing(double smoothing) {
        this.smoothing = Double.isNaN(smoothing) ? 0 : Math.max(0, Math.min(0.95, smoothing));
    }

    /** Positive amounts speed up, negative slow down. */
    public void scrollSpeed(double amount) {
        speed = clampSpeed(speed * Math.pow(SPEED_SCROLL_STEP, amount));
    }

    /** Mouse movement in the same units vanilla passes to {@code Entity.changeLookDirection}. */
    public void look(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) (cursorDeltaX * LOOK_SENSITIVITY);
        pitch = clampPitch(pitch + (float) (cursorDeltaY * LOOK_SENSITIVITY));
    }

    /** Advances one client tick using the movement keys the player is holding. */
    public void tick(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, boolean sprint) {
        prevX = x;
        prevY = y;
        prevZ = z;
        if (!active) return;

        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // facing direction (yaw 0 = +Z)
        double rx = -Math.cos(rad), rz = -Math.sin(rad);  // right-hand side
        double f = (forward ? 1 : 0) - (back ? 1 : 0);
        double s = (right ? 1 : 0) - (left ? 1 : 0);
        double u = (up ? 1 : 0) - (down ? 1 : 0);

        double tx = f * fx + s * rx;
        double tz = f * fz + s * rz;
        double ty = u;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double target = speed * (sprint ? 2 : 1);
        if (len > 0) {
            tx = tx / len * target;
            ty = ty / len * target;
            tz = tz / len * target;
        }

        double response = 1 - smoothing;
        vx += (tx - vx) * response;
        vy += (ty - vy) * response;
        vz += (tz - vz) * response;
        if (Math.abs(vx) < 1e-4) vx = 0;
        if (Math.abs(vy) < 1e-4) vy = 0;
        if (Math.abs(vz) < 1e-4) vz = 0;

        x += vx;
        y += vy;
        z += vz;
    }

    public double x(float tickProgress) {
        return prevX + (x - prevX) * tickProgress;
    }

    public double y(float tickProgress) {
        return prevY + (y - prevY) * tickProgress;
    }

    public double z(float tickProgress) {
        return prevZ + (z - prevZ) * tickProgress;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    private static double clampSpeed(double speed) {
        if (Double.isNaN(speed)) return MIN_SPEED;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }
}
