package com.auditlab.mod.config;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** UI toggles: O toggles the overlay, L toggles labels (rebindable in Controls). */
public final class AuditKeyBindings {
    private static final String CATEGORY = "category.auditlab";

    private AuditKeyBindings() {
    }

    public static void register(ConfigManager configs) {
        KeyBinding overlay = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
        KeyBinding labels = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.auditlab.toggle_labels", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

    private static void actionBar(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }
}
