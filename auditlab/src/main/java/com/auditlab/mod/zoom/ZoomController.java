package com.auditlab.mod.zoom;

/**
 * Hold-to-zoom state. Divides the field of view by the current factor and eases towards the
 * target factor so zooming in and out is smooth. Plain Java so it can be unit-tested; the
 * client thread drives it from the key binding, the FOV mixin and the scroll mixin.
 */
public final class ZoomController {
    public static final double MIN_FACTOR = 1.5;
    public static final double MAX_FACTOR = 50.0;
    /** Each scroll notch multiplies or divides the factor by this. */
    public static final double SCROLL_STEP = 1.25;
    /** Time constant of the easing, in seconds; ~95% of the way after three of these. */
    private static final double EASE_SECONDS = 0.05;

    private static final ZoomController INSTANCE = new ZoomController();

    private boolean held;
    private double defaultFactor = 4.0;
    private double targetFactor = 1.0;
    private double currentFactor = 1.0;
    private long lastNanos = -1;

    public static ZoomController get() {
        return INSTANCE;
    }

    public void setDefaultFactor(double factor) {
        defaultFactor = clamp(factor);
    }

    /** Called every tick with whether the zoom key is held. Pressing resets to the default factor. */
    public void setHeld(boolean pressed) {
        if (pressed && !held) targetFactor = defaultFactor;
        if (!pressed) targetFactor = 1.0;
        held = pressed;
    }

    public boolean isHeld() {
        return held;
    }

    /** Positive amounts zoom in, negative zoom out. Only applies while held. */
    public boolean scroll(double amount) {
        if (!held || amount == 0) return false;
        targetFactor = clamp(targetFactor * Math.pow(SCROLL_STEP, amount));
        return true;
    }

    /** Returns the zoomed FOV for this frame and advances the easing. */
    public float applyFov(float fov, long nowNanos) {
        if (lastNanos < 0) lastNanos = nowNanos;
        double dt = Math.max(0, (nowNanos - lastNanos) / 1e9);
        lastNanos = nowNanos;
        double alpha = 1 - Math.exp(-dt / EASE_SECONDS);
        currentFactor += (targetFactor - currentFactor) * alpha;
        if (Math.abs(currentFactor - targetFactor) < 1e-3) currentFactor = targetFactor;
        return currentFactor == 1.0 ? fov : (float) (fov / currentFactor);
    }

    public double currentFactor() {
        return currentFactor;
    }

    public double targetFactor() {
        return targetFactor;
    }

    private static double clamp(double factor) {
        if (Double.isNaN(factor)) return MIN_FACTOR;
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
    }
}
