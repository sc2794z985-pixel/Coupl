package dev.simdistviz.render;

import dev.simdistviz.config.ModConfig;
import dev.simdistviz.config.ModConfig.DisplayMode;
import dev.simdistviz.state.SimulationDistanceState;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the simulated chunk region into the world, aligned to the 16×16 chunk grid.
 *
 * <ul>
 *     <li>Boundary Only: outer edge at the player's height, vertical lines at every chunk corner on the edge from
 *     the bottom to the top of the world, faint rings at every chunk-section height (every 16 blocks), and an
 *     optional faint translucent wall.</li>
 *     <li>Chunk Grid: everything above plus a grid of all chunk borders inside the region (at the player's
 *     height) and a highlighted outline of the player's own chunk. Nothing is drawn outside the region.</li>
 * </ul>
 *
 * <p>Coordinates are built once per {@link SimulationDistanceState#version()} / display mode change and kept
 * as float arrays relative to the region's north-west corner. Each frame only shifts them by the camera
 * position and streams them into the vertex buffer. Long edges are split per chunk so no single line spans
 * hundreds of blocks, which keeps the screen-space line shader stable when an edge passes beside the camera.
 */
public final class SimulationBoundaryRenderer {
	private static final int BOUNDARY_RGB = 0x3CE6D2;
	private static final int GRID_RGB = 0xFFFFFF;
	private static final int PLAYER_CHUNK_RGB = 0xFFD24A;
	private static final float SEGMENT = 16.0F;
	/** Lifts lines drawn at the player's feet slightly off the floor to avoid z-fighting. */
	private static final double LEVEL_OFFSET = 0.02;

	private static Geometry geometry;

	private SimulationBoundaryRenderer() {
	}

	/** Registered for {@code WorldRenderEvents.BEFORE_TRANSLUCENT}. */
	public static void render(WorldRenderContext context) {
		ModConfig config = ModConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();

		if (!config.boundaryEnabled || client.player == null || !SimulationDistanceState.isValid()) {
			return;
		}

		Geometry geo = geometry;

		if (geo == null || geo.stateVersion != SimulationDistanceState.version() || geo.mode != config.displayMode) {
			geo = geometry = Geometry.build(config.displayMode);
		}

		Vec3d camera = context.worldState().cameraRenderState.pos;
		MatrixStack.Entry entry = context.matrices().peek();
		VertexConsumerProvider consumers = context.consumers();

		// Everything is expressed relative to the camera so floats stay precise far from the world origin.
		float ox = (float) (geo.originX - camera.x);
		float oz = (float) (geo.originZ - camera.z);
		float oy = (float) -camera.y;
		float levelY = (float) (Math.floor(client.player.getY()) + LEVEL_OFFSET - camera.y);

		float intensity = config.intensityFactor();
		float lineAlpha = 0.35F + 0.65F * intensity;
		float baseWidth = client.getWindow().getMinimumLineWidth();

		// Quads first, then lines: an immediate consumer may flush the previous layer when another is requested.
		if (config.showWalls) {
			VertexConsumer quads = consumers.getBuffer(RenderLayers.debugQuads());
			int wallColor = argb(0.12F * intensity, BOUNDARY_RGB);
			float x0 = ox;
			float z0 = oz;
			float x1 = ox + geo.size;
			float z1 = oz + geo.size;
			float y0 = oy + geo.bottomY;
			float y1 = oy + geo.topY;
			wall(quads, entry, x0, z0, x1, z0, y0, y1, wallColor);
			wall(quads, entry, x1, z0, x1, z1, y0, y1, wallColor);
			wall(quads, entry, x1, z1, x0, z1, y0, y1, wallColor);
			wall(quads, entry, x0, z1, x0, z0, y0, y1, wallColor);
		}

		VertexConsumer lines = consumers.getBuffer(RenderLayers.linesTranslucent());

		// Faint rings at every chunk-section height along the boundary.
		int sectionColor = argb(lineAlpha * 0.3F, BOUNDARY_RGB);
		float sectionWidth = baseWidth * 0.5F;
		float[] rings = geo.sectionRings;

		for (int i = 0; i < rings.length; i += 5) {
			float y = oy + rings[i + 4];
			horizontal(lines, entry, ox + rings[i], y, oz + rings[i + 1], ox + rings[i + 2], oz + rings[i + 3], sectionColor, sectionWidth);
		}

		// Vertical lines at every chunk corner on the boundary.
		int postColor = argb(lineAlpha * 0.75F, BOUNDARY_RGB);
		float postWidth = baseWidth * 0.75F;
		float[] posts = geo.posts;

		for (int i = 0; i < posts.length; i += 4) {
			float x = ox + posts[i];
			float z = oz + posts[i + 1];
			line(lines, entry, x, oy + posts[i + 2], z, x, oy + posts[i + 3], z, 0.0F, 1.0F, 0.0F, postColor, postWidth);
		}

		if (geo.mode == DisplayMode.CHUNK_GRID) {
			int gridColor = argb(0.15F + 0.5F * intensity, GRID_RGB);
			float gridWidth = baseWidth * 0.6F;
			float[] grid = geo.grid;

			for (int i = 0; i < grid.length; i += 4) {
				horizontal(lines, entry, ox + grid[i], levelY, oz + grid[i + 1], ox + grid[i + 2], oz + grid[i + 3], gridColor, gridWidth);
			}

			int ownColor = argb(lineAlpha, PLAYER_CHUNK_RGB);
			float ownWidth = baseWidth * 0.9F;
			float[] own = geo.playerChunk;

			for (int i = 0; i < own.length; i += 4) {
				horizontal(lines, entry, ox + own[i], levelY, oz + own[i + 1], ox + own[i + 2], oz + own[i + 3], ownColor, ownWidth);
			}
		}

		// The main boundary line at the player's height, drawn last so it sits on top.
		int edgeColor = argb(lineAlpha, BOUNDARY_RGB);
		float edgeWidth = baseWidth * 1.25F;
		float[] edge = geo.edge;

		for (int i = 0; i < edge.length; i += 4) {
			horizontal(lines, entry, ox + edge[i], levelY, oz + edge[i + 1], ox + edge[i + 2], oz + edge[i + 3], edgeColor, edgeWidth);
		}
	}

	/** Drops cached geometry, e.g. after a disconnect. */
	public static void invalidate() {
		geometry = null;
	}

	private static void horizontal(VertexConsumer lines, MatrixStack.Entry entry, float x1, float y, float z1,
			float x2, float z2, int color, float width) {
		// Segments are axis aligned: either along X or along Z.
		if (x1 == x2) {
			line(lines, entry, x1, y, z1, x2, y, z2, 0.0F, 0.0F, Math.signum(z2 - z1), color, width);
		} else {
			line(lines, entry, x1, y, z1, x2, y, z2, Math.signum(x2 - x1), 0.0F, 0.0F, color, width);
		}
	}

	/** One line segment for the 1.21.11 line pipeline: position, color, direction as normal, line width. */
	private static void line(VertexConsumer lines, MatrixStack.Entry entry, float x1, float y1, float z1,
			float x2, float y2, float z2, float nx, float ny, float nz, int color, float width) {
		lines.vertex(entry, x1, y1, z1).color(color).normal(entry, nx, ny, nz).lineWidth(width);
		lines.vertex(entry, x2, y2, z2).color(color).normal(entry, nx, ny, nz).lineWidth(width);
	}

	/** Vertical quad emitted with both windings so it is visible from inside and outside. */
	private static void wall(VertexConsumer quads, MatrixStack.Entry entry, float ax, float az, float bx, float bz,
			float y0, float y1, int color) {
		quads.vertex(entry, ax, y0, az).color(color);
		quads.vertex(entry, bx, y0, bz).color(color);
		quads.vertex(entry, bx, y1, bz).color(color);
		quads.vertex(entry, ax, y1, az).color(color);

		quads.vertex(entry, ax, y0, az).color(color);
		quads.vertex(entry, ax, y1, az).color(color);
		quads.vertex(entry, bx, y1, bz).color(color);
		quads.vertex(entry, bx, y0, bz).color(color);
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Cached, camera-independent geometry in block units relative to the region's north-west corner. */
	private static final class Geometry {
		final int stateVersion;
		final DisplayMode mode;
		final int originX;
		final int originZ;
		/** Edge length of the square region in blocks: (2 * distance + 1) * 16. */
		final float size;
		final float bottomY;
		final float topY;
		/** Outer edge at player height: x1, z1, x2, z2 per segment. */
		final float[] edge;
		/** Outer edge at every section height: x1, z1, x2, z2, y per segment. */
		final float[] sectionRings;
		/** Vertical lines at the edge's chunk corners: x, z, y1, y2 per segment. */
		final float[] posts;
		/** Inner chunk borders (Chunk Grid only): x1, z1, x2, z2 per segment. */
		final float[] grid;
		/** Outline of the player's chunk (Chunk Grid only): x1, z1, x2, z2 per segment. */
		final float[] playerChunk;

		private Geometry(DisplayMode mode) {
			this.stateVersion = SimulationDistanceState.version();
			this.mode = mode;
			this.originX = SimulationDistanceState.minBlockX();
			this.originZ = SimulationDistanceState.minBlockZ();

			int chunks = SimulationDistanceState.maxChunkX() - SimulationDistanceState.minChunkX() + 1;
			this.size = chunks * SEGMENT;
			this.bottomY = SimulationDistanceState.bottomY();
			this.topY = SimulationDistanceState.topY();

			FloatArrayList edgeList = new FloatArrayList();
			FloatArrayList ringList = new FloatArrayList();
			FloatArrayList postList = new FloatArrayList();
			FloatArrayList gridList = new FloatArrayList();
			FloatArrayList ownList = new FloatArrayList();

			// Outer edge, one segment per chunk on each of the four sides.
			for (int i = 0; i < chunks; i++) {
				float a = i * SEGMENT;
				float b = a + SEGMENT;
				addSquareSides(edgeList, a, b, size);
			}

			// The same edge repeated at every section boundary from the bottom to the top of the world.
			for (float y = bottomY; y <= topY; y += SEGMENT) {
				for (int i = 0; i < chunks; i++) {
					float a = i * SEGMENT;
					float b = a + SEGMENT;
					ringList.add(a); ringList.add(0.0F); ringList.add(b); ringList.add(0.0F); ringList.add(y);
					ringList.add(a); ringList.add(size); ringList.add(b); ringList.add(size); ringList.add(y);
					ringList.add(0.0F); ringList.add(a); ringList.add(0.0F); ringList.add(b); ringList.add(y);
					ringList.add(size); ringList.add(a); ringList.add(size); ringList.add(b); ringList.add(y);
				}
			}

			// Vertical lines at each chunk corner along the edge (corners of the square included once).
			for (int i = 0; i <= chunks; i++) {
				float c = i * SEGMENT;
				addPost(postList, c, 0.0F);
				addPost(postList, c, size);

				if (i > 0 && i < chunks) {
					addPost(postList, 0.0F, c);
					addPost(postList, size, c);
				}
			}

			if (mode == DisplayMode.CHUNK_GRID) {
				// Inner chunk borders only; the outer edge is already drawn above.
				for (int line = 1; line < chunks; line++) {
					float c = line * SEGMENT;

					for (int i = 0; i < chunks; i++) {
						float a = i * SEGMENT;
						float b = a + SEGMENT;
						gridList.add(c); gridList.add(a); gridList.add(c); gridList.add(b);
						gridList.add(a); gridList.add(c); gridList.add(b); gridList.add(c);
					}
				}

				float px = (SimulationDistanceState.playerChunkX() - SimulationDistanceState.minChunkX()) * SEGMENT;
				float pz = (SimulationDistanceState.playerChunkZ() - SimulationDistanceState.minChunkZ()) * SEGMENT;
				ownList.add(px); ownList.add(pz); ownList.add(px + SEGMENT); ownList.add(pz);
				ownList.add(px); ownList.add(pz + SEGMENT); ownList.add(px + SEGMENT); ownList.add(pz + SEGMENT);
				ownList.add(px); ownList.add(pz); ownList.add(px); ownList.add(pz + SEGMENT);
				ownList.add(px + SEGMENT); ownList.add(pz); ownList.add(px + SEGMENT); ownList.add(pz + SEGMENT);
			}

			this.edge = edgeList.toFloatArray();
			this.sectionRings = ringList.toFloatArray();
			this.posts = postList.toFloatArray();
			this.grid = gridList.toFloatArray();
			this.playerChunk = ownList.toFloatArray();
		}

		static Geometry build(DisplayMode mode) {
			return new Geometry(mode);
		}

		private static void addSquareSides(FloatArrayList list, float a, float b, float size) {
			list.add(a); list.add(0.0F); list.add(b); list.add(0.0F);
			list.add(a); list.add(size); list.add(b); list.add(size);
			list.add(0.0F); list.add(a); list.add(0.0F); list.add(b);
			list.add(size); list.add(a); list.add(size); list.add(b);
		}

		private void addPost(FloatArrayList list, float x, float z) {
			// Split into section-high pieces for the same reason edges are split per chunk.
			for (float y = bottomY; y < topY; y += SEGMENT) {
				list.add(x); list.add(z); list.add(y); list.add(Math.min(y + SEGMENT, topY));
			}
		}
	}
}
