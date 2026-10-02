package com.auditlab.mod.config;

import com.auditlab.mod.export.SessionManager;
import com.auditlab.mod.freecam.FreecamController;
import com.auditlab.mod.zoom.ZoomController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * UI keys (rebindable in Controls): O toggles the overlay, L toggles labels, hold C to zoom,
 * F4 toggles freecam. Freecam is only available while an audit session is running, i.e. in
 * singleplayer or on a server listed in {@code allowedServers}.
 */
public final class AuditKeyBindings {
    /** Shown in Controls under the translation key {@code key.category.auditlab.main}. */
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("auditlab", "main"));

    private AuditKeyBindings() {
    }

    public static void register(ConfigManager configs, SessionManager sessions) {
        KeyBinding overlay = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
        KeyBinding labels = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_labels", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));
        KeyBinding zoomKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        KeyBinding freecamKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F4, CATEGORY));
        SmoothCamera smoothCamera = new SmoothCamera();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AuditLabConfig cfg = configs.get();

            boolean zooming = cfg.zoomEnabled && isHeld(client, zoomKey) && client.currentScreen == null;
            ZoomController zoom = ZoomController.get();
            zoom.setDefaultFactor(cfg.zoomFactor);
            zoom.setHeld(zooming);

            FreecamController freecam = FreecamController.get();
            freecam.setSmoothing(cfg.freecamSmoothing);
            while (freecamKey.wasPressed()) toggleFreecam(client, cfg, sessions, freecam);
            if (freecam.isActive() && (client.world == null || client.player == null || sessions.current() == null)) {
                freecam.deactivate();
            }

            smoothCamera.update(client, (zooming && cfg.zoomSmoothCamera) || (freecam.isActive() && cfg.freecamSmoothCamera));

            while (overlay.wasPressed()) {
                cfg.overlayEnabled = !cfg.overlayEnabled;
                configs.save();
                actionBar(client, "AuditLab overlay " + (cfg.overlayEnabled ? "on" : "off"));
            }
            while (labels.wasPressed()) {
                cfg.labelsEnabled = !cfg.labelsEnabled;
                configs.save();
                actionBar(client, "AuditLab labels " + (cfg.labelsEnabled ? "on" : "off"));
            }
        });
    }

    private static void toggleFreecam(MinecraftClient client, AuditLabConfig cfg, SessionManager sessions, FreecamController freecam) {
        if (freecam.isActive()) {
            freecam.deactivate();
            actionBar(client, "Freecam off");
            return;
        }
        if (client.player == null || client.world == null) return;
        if (sessions.current() == null) {
            actionBar(client, "Freecam is only available in singleplayer and on allowedServers");
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        Vec3d pos = camera.getCameraPos();
        freecam.setSpeed(cfg.freecamSpeed);
        freecam.activate(pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch());
        actionBar(client, "Freecam on (scroll to change speed)");
    }

    /**
     * C is also vanilla's "Save Toolbar Activator". When two bindings share a key, vanilla may
     * route the press to only one of them, so the physical key state is checked as well.
     */
    private static boolean isHeld(MinecraftClient client, KeyBinding binding) {
        if (binding.isPressed()) return true;
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        return key.getCategory() == InputUtil.Type.KEYSYM
            && key.getCode() != InputUtil.UNKNOWN_KEY.getCode()
            && InputUtil.isKeyPressed(client.getWindow(), key.getCode());
    }

    /** Turns vanilla's smooth (cinematic) camera on while wanted and restores the user's setting afterwards. */
    private static final class SmoothCamera {
        private boolean overriding;
        private boolean saved;

        void update(MinecraftClient client, boolean wanted) {
            if (wanted == overriding) return;
            overriding = wanted;
            if (wanted) {
                saved = client.options.smoothCameraEnabled;
                client.options.smoothCameraEnabled = true;
            } else {
                client.options.smoothCameraEnabled = saved;
            }
        }
    }

    private static void actionBar(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }
}
