package com.auditlab.mod.config;

import com.auditlab.mod.zoom.ZoomController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** UI keys (rebindable in Controls): O toggles the overlay, L toggles labels, hold C to zoom. */
public final class AuditKeyBindings {
    /** Shown in Controls under the translation key {@code key.category.auditlab.main}. */
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("auditlab", "main"));

    private AuditKeyBindings() {
    }

    public static void register(ConfigManager configs) {
        KeyBinding overlay = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
        KeyBinding labels = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_labels", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));
        KeyBinding zoomKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        ZoomState zoomState = new ZoomState();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            zoomState.update(client, configs.get(), isHeld(client, zoomKey));
            while (overlay.wasPressed()) {
                AuditLabConfig cfg = configs.get();
                cfg.overlayEnabled = !cfg.overlayEnabled;
                configs.save();
                actionBar(client, "AuditLab overlay " + (cfg.overlayEnabled ? "on" : "off"));
            }
            while (labels.wasPressed()) {
                AuditLabConfig cfg = configs.get();
                cfg.labelsEnabled = !cfg.labelsEnabled;
                configs.save();
                actionBar(client, "AuditLab labels " + (cfg.labelsEnabled ? "on" : "off"));
            }
        });
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

    /** Drives {@link ZoomController} and swaps the smooth camera in and out around a zoom. */
    private static final class ZoomState {
        private boolean active;
        private boolean savedSmoothCamera;

        void update(MinecraftClient client, AuditLabConfig cfg, boolean keyDown) {
            boolean want = cfg.zoomEnabled && keyDown && client.currentScreen == null;
            ZoomController zoom = ZoomController.get();
            zoom.setDefaultFactor(cfg.zoomFactor);
            zoom.setHeld(want);
            if (want == active) return;
            active = want;
            if (active) {
                savedSmoothCamera = client.options.smoothCameraEnabled;
                if (cfg.zoomSmoothCamera) client.options.smoothCameraEnabled = true;
            } else {
                client.options.smoothCameraEnabled = savedSmoothCamera;
            }
        }
    }

    private static void actionBar(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }
}
