package dev.simdistviz.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.simdistviz.SimulationDistanceVisualizerClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User preferences, persisted to {@code config/simulation-distance-visualizer.json}.
 *
 * <p>Only display settings are stored. Server-sent distances are deliberately not part of this file.
 */
public final class ModConfig {
	public static final int[] INTENSITY_STEPS = {25, 50, 75, 100};
	private static final String FILE_NAME = "simulation-distance-visualizer.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static ModConfig instance = new ModConfig();

	public enum DisplayMode {
		BOUNDARY_ONLY("Boundary Only"),
		CHUNK_GRID("Chunk Grid");

		private final String label;

		DisplayMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public DisplayMode next() {
			return this == BOUNDARY_ONLY ? CHUNK_GRID : BOUNDARY_ONLY;
		}
	}

	public enum HudPosition {
		TOP_LEFT,
		TOP_RIGHT
	}

	public boolean hudEnabled = true;
	public boolean boundaryEnabled = true;
	public DisplayMode displayMode = DisplayMode.BOUNDARY_ONLY;
	/** Visualization intensity in percent; one of {@link #INTENSITY_STEPS}. */
	public int intensity = 50;
	public boolean debugEnabled = false;
	/** Faint translucent wall from the bottom to the top of the world along the boundary. */
	public boolean showWalls = true;
	public boolean showCurrentChunk = true;
	public HudPosition hudPosition = HudPosition.TOP_LEFT;
	/** Chat note when the server changes its simulation distance mid-session. */
	public boolean notifyOnServerChange = true;

	public static ModConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);

				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				SimulationDistanceVisualizerClient.LOGGER.warn("Could not read {}, using defaults", path, e);
				instance = new ModConfig();
			}
		}

		instance.sanitize();
		// Writes defaults on first start and normalizes hand-edited files.
		save();
	}

	public static void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			SimulationDistanceVisualizerClient.LOGGER.warn("Could not write {}", path, e);
		}
	}

	public void cycleIntensity() {
		for (int i = 0; i < INTENSITY_STEPS.length; i++) {
			if (INTENSITY_STEPS[i] == intensity) {
				intensity = INTENSITY_STEPS[(i + 1) % INTENSITY_STEPS.length];
				return;
			}
		}

		intensity = INTENSITY_STEPS[0];
	}

	/** Intensity as a 0..1 factor. */
	public float intensityFactor() {
		return intensity / 100.0F;
	}

	private void sanitize() {
		if (displayMode == null) {
			displayMode = DisplayMode.BOUNDARY_ONLY;
		}

		if (hudPosition == null) {
			hudPosition = HudPosition.TOP_LEFT;
		}

		int closest = INTENSITY_STEPS[0];

		for (int step : INTENSITY_STEPS) {
			if (Math.abs(step - intensity) < Math.abs(closest - intensity)) {
				closest = step;
			}
		}

		intensity = closest;
	}
}
