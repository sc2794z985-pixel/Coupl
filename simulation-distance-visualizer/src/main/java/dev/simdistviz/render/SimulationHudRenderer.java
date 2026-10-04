package dev.simdistviz.render;

import dev.simdistviz.config.ModConfig;
import dev.simdistviz.network.ServerDistanceTracker;
import dev.simdistviz.state.SimulationDistanceState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Small HUD panel with the server-sent simulation and view distance, plus the optional debug panel.
 * Registered through Fabric's {@code HudElementRegistry}, so it hides with F1 like the rest of the HUD.
 */
public final class SimulationHudRenderer {
	private static final int MARGIN = 4;
	private static final int PADDING = 3;
	private static final int LINE_GAP = 1;
	private static final int PANEL_GAP = 4;
	private static final int BACKGROUND = 0x80000000;

	private static final int TITLE = 0xFF3CE6D2;
	private static final int VALUE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFAAAAAA;
	private static final int VIEW = 0xFFFFAA00;
	private static final int WAITING = 0xFFFF7777;

	private SimulationHudRenderer() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ModConfig config = ModConfig.get();
		ClientPlayerEntity player = client.player;

		if (player == null || client.options.hudHidden || (!config.hudEnabled && !config.debugEnabled)) {
			return;
		}

		TextRenderer font = client.textRenderer;
		int y = MARGIN;

		if (config.hudEnabled) {
			y = drawPanel(context, font, config, mainLines(config, player), y) + PANEL_GAP;
		}

		if (config.debugEnabled) {
			drawPanel(context, font, config, debugLines(config, player), y);
		}
	}

	private static List<Line> mainLines(ModConfig config, ClientPlayerEntity player) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("SIMULATION DISTANCE", TITLE));

		if (ServerDistanceTracker.hasSimulationDistance()) {
			int distance = ServerDistanceTracker.simulationDistance();
			lines.add(new Line(distance + (distance == 1 ? " chunk" : " chunks"), VALUE));
			lines.add(new Line("≈ " + distance * 16 + " blocks", MUTED));
		} else {
			lines.add(new Line("waiting for server…", WAITING));
		}

		if (ServerDistanceTracker.hasViewDistance()) {
			lines.add(new Line("View Distance: " + ServerDistanceTracker.viewDistance() + " chunks", VIEW));
		}

		if (config.showCurrentChunk) {
			lines.add(Line.SPACER);
			lines.add(new Line("Current Chunk:", MUTED));
			lines.add(new Line("X: " + player.getChunkPos().x, VALUE));
			lines.add(new Line("Z: " + player.getChunkPos().z, VALUE));
		}

		return lines;
	}

	private static List<Line> debugLines(ModConfig config, ClientPlayerEntity player) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("SIM DEBUG", TITLE));
		lines.add(new Line("Server Simulation Distance: " + valueOrDash(ServerDistanceTracker.simulationDistance()), VALUE));
		lines.add(new Line("  via " + ServerDistanceTracker.simulationSource().packetName()
				+ age(ServerDistanceTracker.simulationUpdatedAtMillis()), MUTED));
		lines.add(new Line("Server View Distance: " + valueOrDash(ServerDistanceTracker.viewDistance()), VIEW));
		lines.add(new Line("  via " + ServerDistanceTracker.viewSource().packetName()
				+ age(ServerDistanceTracker.viewUpdatedAtMillis()), MUTED));
		lines.add(Line.SPACER);
		lines.add(new Line("Player Chunk: " + player.getChunkPos().x + " / " + player.getChunkPos().z, VALUE));
		lines.add(new Line("Player Block: " + player.getBlockX() + " / " + player.getBlockY() + " / " + player.getBlockZ(), VALUE));

		if (SimulationDistanceState.isValid()) {
			lines.add(Line.SPACER);
			lines.add(new Line("Boundary (chunks):", MUTED));
			lines.add(new Line("X: " + SimulationDistanceState.minChunkX() + " → " + SimulationDistanceState.maxChunkX(), VALUE));
			lines.add(new Line("Z: " + SimulationDistanceState.minChunkZ() + " → " + SimulationDistanceState.maxChunkZ(), VALUE));
			int side = SimulationDistanceState.maxChunkX() - SimulationDistanceState.minChunkX() + 1;
			lines.add(new Line(side + " × " + side + " = " + side * side + " chunks", MUTED));
			lines.add(new Line("Dimension: " + SimulationDistanceState.dimension().getValue(), MUTED));
		} else {
			lines.add(new Line("Boundary: not available yet", WAITING));
		}

		lines.add(new Line("Mode: " + config.displayMode.label() + " | " + config.intensity + " %"
				+ (config.boundaryEnabled ? "" : " | hidden"), MUTED));
		return lines;
	}

	/** Draws the lines in a translucent box and returns the y coordinate just below it. */
	private static int drawPanel(DrawContext context, TextRenderer font, ModConfig config, List<Line> lines, int top) {
		int width = 0;
		int height = 0;

		for (Line line : lines) {
			width = Math.max(width, font.getWidth(line.text()));
			height += line == Line.SPACER ? 4 : font.fontHeight + LINE_GAP;
		}

		int left = config.hudPosition == ModConfig.HudPosition.TOP_RIGHT
				? context.getScaledWindowWidth() - MARGIN - width - 2 * PADDING
				: MARGIN;

		context.fill(left, top, left + width + 2 * PADDING, top + height + 2 * PADDING - LINE_GAP, BACKGROUND);

		int x = left + PADDING;
		int y = top + PADDING;

		for (Line line : lines) {
			if (line == Line.SPACER) {
				y += 4;
				continue;
			}

			context.drawTextWithShadow(font, line.text(), x, y, line.color());
			y += font.fontHeight + LINE_GAP;
		}

		return top + height + 2 * PADDING - LINE_GAP;
	}

	private static String valueOrDash(int value) {
		return value == ServerDistanceTracker.UNKNOWN ? "-" : Integer.toString(value);
	}

	private static String age(long millis) {
		if (millis == 0L) {
			return "";
		}

		long seconds = (System.currentTimeMillis() - millis) / 1000L;
		return " (" + seconds + " s ago)";
	}

	private record Line(String text, int color) {
		static final Line SPACER = new Line("", 0);
	}
}
