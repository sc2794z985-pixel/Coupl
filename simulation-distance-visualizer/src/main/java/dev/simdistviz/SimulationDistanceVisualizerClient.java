package dev.simdistviz;

import dev.simdistviz.config.ModConfig;
import dev.simdistviz.input.ModKeybinds;
import dev.simdistviz.network.ServerDistanceTracker;
import dev.simdistviz.render.SimulationBoundaryRenderer;
import dev.simdistviz.render.SimulationHudRenderer;
import dev.simdistviz.state.SimulationDistanceState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Wires the read-only packet observer, per-tick state, key bindings, HUD and world rendering.
 */
public final class SimulationDistanceVisualizerClient implements ClientModInitializer {
	public static final String MOD_ID = "simulation-distance-visualizer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		ModKeybinds.register();

		// New connection (also after a proxy server switch) and disconnect: never reuse values of a previous server.
		ClientPlayConnectionEvents.INIT.register((handler, client) -> resetSession());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSession());

		ServerDistanceTracker.setChangeListener((oldDistance, newDistance) -> {
			if (ModConfig.get().notifyOnServerChange) {
				ModKeybinds.chat(MinecraftClient.getInstance(), Text.literal("Server Simulation Distance: ")
						.append(Text.literal(oldDistance + " → " + newDistance + " chunks").formatted(Formatting.AQUA)));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SimulationDistanceState.update(client);
			ModKeybinds.handle(client);
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "hud"),
				SimulationHudRenderer::render);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(SimulationBoundaryRenderer::render);

		LOGGER.info("Simulation Distance Visualizer initialized (read-only, client-side)");
	}

	private static void resetSession() {
		ServerDistanceTracker.reset();
		SimulationDistanceState.invalidate();
		SimulationBoundaryRenderer.invalidate();
	}
}
