package dev.simdistviz.state;

import dev.simdistviz.network.ServerDistanceTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Derived simulation region around the player, recomputed once per client tick.
 *
 * <p>The server ticks entities in every chunk whose Chebyshev (square) chunk distance to the player's chunk is
 * at most the simulation distance, so the region is the square
 * {@code [playerChunk - d, playerChunk + d]} on both axes, i.e. {@code (2d + 1)²} chunks.
 *
 * <p>{@link #version()} only changes when the player enters another chunk, the server sends a new distance,
 * the world/dimension changes, or the connection changes. Renderers use it to decide when to rebuild their
 * cached geometry instead of recomputing every frame.
 */
public final class SimulationDistanceState {
	private static boolean valid;
	private static int simulationDistance = ServerDistanceTracker.UNKNOWN;
	private static int playerChunkX;
	private static int playerChunkZ;
	private static int minChunkX;
	private static int maxChunkX;
	private static int minChunkZ;
	private static int maxChunkZ;
	private static int bottomY;
	private static int topY;
	private static ClientWorld world;
	private static RegistryKey<World> dimension;
	private static int trackerRevision = Integer.MIN_VALUE;
	private static int version;

	private SimulationDistanceState() {
	}

	public static void update(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld currentWorld = client.world;

		if (player == null || currentWorld == null || !ServerDistanceTracker.hasSimulationDistance()) {
			if (valid || world != null) {
				valid = false;
				world = null;
				dimension = null;
				version++;
			}

			return;
		}

		ChunkPos chunk = player.getChunkPos();
		int distance = ServerDistanceTracker.simulationDistance();
		boolean changed = !valid
				|| currentWorld != world
				|| ServerDistanceTracker.revision() != trackerRevision
				|| chunk.x != playerChunkX
				|| chunk.z != playerChunkZ;

		if (!changed) {
			return;
		}

		valid = true;
		world = currentWorld;
		dimension = currentWorld.getRegistryKey();
		trackerRevision = ServerDistanceTracker.revision();
		simulationDistance = distance;
		playerChunkX = chunk.x;
		playerChunkZ = chunk.z;
		minChunkX = chunk.x - distance;
		maxChunkX = chunk.x + distance;
		minChunkZ = chunk.z - distance;
		maxChunkZ = chunk.z + distance;
		// Nether/End have different build limits than the Overworld (-64..319).
		bottomY = currentWorld.getBottomY();
		topY = currentWorld.getTopYInclusive() + 1;
		version++;
	}

	public static void invalidate() {
		valid = false;
		world = null;
		dimension = null;
		version++;
	}

	public static boolean isValid() {
		return valid;
	}

	public static int version() {
		return version;
	}

	public static int simulationDistance() {
		return simulationDistance;
	}

	public static int playerChunkX() {
		return playerChunkX;
	}

	public static int playerChunkZ() {
		return playerChunkZ;
	}

	public static int minChunkX() {
		return minChunkX;
	}

	public static int maxChunkX() {
		return maxChunkX;
	}

	public static int minChunkZ() {
		return minChunkZ;
	}

	public static int maxChunkZ() {
		return maxChunkZ;
	}

	/** Lowest block Y of the current dimension. */
	public static int bottomY() {
		return bottomY;
	}

	/** One above the highest block Y of the current dimension. */
	public static int topY() {
		return topY;
	}

	public static RegistryKey<World> dimension() {
		return dimension;
	}

	/** West edge of the region in block coordinates. */
	public static int minBlockX() {
		return minChunkX << 4;
	}

	/** East edge of the region in block coordinates (exclusive). */
	public static int maxBlockX() {
		return (maxChunkX + 1) << 4;
	}

	public static int minBlockZ() {
		return minChunkZ << 4;
	}

	public static int maxBlockZ() {
		return (maxChunkZ + 1) << 4;
	}
}
