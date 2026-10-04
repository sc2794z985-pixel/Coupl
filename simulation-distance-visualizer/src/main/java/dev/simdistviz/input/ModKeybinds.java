package dev.simdistviz.input;

import dev.simdistviz.SimulationDistanceVisualizerClient;
import dev.simdistviz.config.ModConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (rebindable under Options → Controls → Key Binds → "Simulation Distance Visualizer").
 */
public final class ModKeybinds {
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(SimulationDistanceVisualizerClient.MOD_ID, "main"));

	private static KeyBinding toggleHud;
	private static KeyBinding toggleBoundary;
	private static KeyBinding cycleMode;
	private static KeyBinding cycleIntensity;
	private static KeyBinding toggleDebug;

	private ModKeybinds() {
	}

	public static void register() {
		toggleHud = register("toggle_hud", GLFW.GLFW_KEY_H);
		toggleBoundary = register("toggle_boundary", GLFW.GLFW_KEY_J);
		cycleMode = register("cycle_mode", GLFW.GLFW_KEY_K);
		// L is also vanilla's Advancements key; Minecraft will flag the conflict until one of them is rebound.
		cycleIntensity = register("cycle_intensity", GLFW.GLFW_KEY_L);
		toggleDebug = register("toggle_debug", GLFW.GLFW_KEY_I);
	}

	private static KeyBinding register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key." + SimulationDistanceVisualizerClient.MOD_ID + "." + name,
				InputUtil.Type.KEYSYM,
				key,
				CATEGORY));
	}

	/** Called at the end of every client tick. */
	public static void handle(MinecraftClient client) {
		ModConfig config = ModConfig.get();

		while (toggleHud.wasPressed()) {
			config.hudEnabled = !config.hudEnabled;
			ModConfig.save();
			chat(client, Text.literal("HUD: ").append(onOff(config.hudEnabled)));
		}

		while (toggleBoundary.wasPressed()) {
			config.boundaryEnabled = !config.boundaryEnabled;
			ModConfig.save();
			chat(client, Text.literal("Simulation Boundary: ").append(onOff(config.boundaryEnabled)));
		}

		while (cycleMode.wasPressed()) {
			config.displayMode = config.displayMode.next();
			ModConfig.save();
			chat(client, Text.literal("Display Mode: ")
					.append(Text.literal(config.displayMode.label()).formatted(Formatting.AQUA)));
		}

		while (cycleIntensity.wasPressed()) {
			config.cycleIntensity();
			ModConfig.save();
			chat(client, Text.literal("Intensity: ")
					.append(Text.literal(config.intensity + " %").formatted(Formatting.AQUA)));
		}

		while (toggleDebug.wasPressed()) {
			config.debugEnabled = !config.debugEnabled;
			ModConfig.save();
			chat(client, Text.literal("Debug: ").append(onOff(config.debugEnabled)));
		}
	}

	private static MutableText onOff(boolean on) {
		return on ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED);
	}

	/** Local chat line; nothing is sent to the server. */
	public static void chat(MinecraftClient client, Text message) {
		if (client.player != null) {
			client.player.sendMessage(message, false);
		}
	}
}
