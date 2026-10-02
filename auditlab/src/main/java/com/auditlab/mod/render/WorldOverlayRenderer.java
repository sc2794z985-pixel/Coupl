package com.auditlab.mod.render;

import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.config.AuditLabConfig;
import com.auditlab.mod.events.McIds;
import com.auditlab.mod.export.AuditSession;
import com.auditlab.mod.export.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Chunk overlay for the 1.21.9+ two-phase world renderer.
 *
 * <ul>
 *   <li>{@link WorldRenderEvents#END_EXTRACTION}: pick the scored chunks near the camera, build
 *       their label text and attach the immutable result to the frame's {@code WorldRenderState}.</li>
 *   <li>{@link WorldRenderEvents#END_MAIN}: draw only from that render state. A box over each chunk,
 *       spanning the Y range of its observations and findings, coloured by severity (green / yellow
 *       / red) and depth-tested like world geometry; plus a camera-facing label with the score and
 *       top reasons.</li>
 * </ul>
 * END_MAIN runs before vanilla's final buffer flush, so everything written to
 * {@code context.consumers()} is drawn without a manual flush.
 */
public final class WorldOverlayRenderer {
    /** One chunk to draw, captured during extraction. */
    private record OverlayEntry(ChunkAnalysis analysis, List<String> labelLines) {
    }

    private static final RenderStateDataKey<List<OverlayEntry>> OVERLAY = RenderStateDataKey.create(() -> "auditlab:chunk_overlay");

    private static final int BOX_ALPHA = 0xE6;
    private static final float LABEL_SCALE = 0.025f;
    private static final int LINE_HEIGHT = 10;
    private static final int LABEL_BACKGROUND = 0x66000000;
    private static final double LABEL_LIFT = 1.5;

    private final SessionManager sessions;
    private final Supplier<AuditLabConfig> config;

    public WorldOverlayRenderer(SessionManager sessions, Supplier<AuditLabConfig> config) {
        this.sessions = sessions;
        this.config = config;
    }

    public void register() {
        WorldRenderEvents.END_EXTRACTION.register(this::extract);
        WorldRenderEvents.END_MAIN.register(this::draw);
    }

    private void extract(WorldExtractionContext context) {
        context.worldState().setData(OVERLAY, null);
        AuditLabConfig cfg = config.get();
        AuditSession session = sessions.current();
        if (session == null || !cfg.overlayEnabled || context.world() == null) return;

        Vec3d cam = context.camera().getCameraPos();
        ChunkKey center = ChunkKey.ofBlock(McIds.dimension(context.world()), MathHelper.floor(cam.x), MathHelper.floor(cam.z));
        List<ChunkAnalysis> visible = session.analyzer().analysesNear(center, cfg.renderDistanceChunks);
        if (visible.isEmpty()) return;

        visible.sort(Comparator.comparingDouble(a -> horizontalDistanceSq(a, cam)));
        List<OverlayEntry> entries = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            ChunkAnalysis a = visible.get(i);
            boolean labelled = cfg.labelsEnabled && i < cfg.maxLabels;
            entries.add(new OverlayEntry(a, labelled ? LabelFormatter.lines(a, cfg.labelReasonLines) : List.of()));
        }
        context.worldState().setData(OVERLAY, List.copyOf(entries));
    }

    private void draw(WorldRenderContext context) {
        List<OverlayEntry> entries = context.worldState().getData(OVERLAY);
        if (entries == null || entries.isEmpty()) return;

        CameraRenderState camera = context.worldState().cameraRenderState;
        Vec3d cam = camera.pos;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        MinecraftClient client = MinecraftClient.getInstance();

        VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());
        float lineWidth = client.getWindow().getMinimumLineWidth();
        for (OverlayEntry e : entries) drawChunkBox(matrices, lines, e.analysis(), cam, lineWidth);

        TextRenderer text = client.textRenderer;
        for (OverlayEntry e : entries) {
            if (!e.labelLines().isEmpty()) drawLabel(matrices, consumers, text, camera, e.analysis(), e.labelLines());
        }
    }

    private static void drawChunkBox(MatrixStack matrices, VertexConsumer lines, ChunkAnalysis a, Vec3d cam, float lineWidth) {
        int height = a.maxY() + 1 - a.minY();
        VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(0, 0, 0, 16, height, 16),
            a.key().minBlockX() - cam.x, a.minY() - cam.y, a.key().minBlockZ() - cam.z,
            a.score().severity().argb(BOX_ALPHA), lineWidth);
    }

    private static void drawLabel(MatrixStack matrices, VertexConsumerProvider consumers, TextRenderer text,
                                  CameraRenderState camera, ChunkAnalysis a, List<String> lines) {
        Vec3d cam = camera.pos;
        double x = a.key().minBlockX() + 8 - cam.x;
        double y = a.maxY() + 1 + LABEL_LIFT - cam.y;
        double z = a.key().minBlockZ() + 8 - cam.z;
        // Grow with distance so labels stay readable across the render radius.
        float scale = LABEL_SCALE * (float) Math.max(1.0, Math.sqrt(x * x + y * y + z * z) / 12.0);

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(camera.orientation);
        matrices.scale(scale, -scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int headerColor = a.score().severity().argb(0xFF);
        float lineY = -lines.size() * LINE_HEIGHT;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float lineX = -text.getWidth(line) / 2f;
            int color = i == 0 ? headerColor : 0xFFFFFFFF;
            text.draw(line, lineX, lineY, color, false, matrix, consumers, TextRenderer.TextLayerType.SEE_THROUGH,
                LABEL_BACKGROUND, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            lineY += LINE_HEIGHT;
        }
        matrices.pop();
    }

    private static double horizontalDistanceSq(ChunkAnalysis a, Vec3d cam) {
        double dx = a.key().minBlockX() + 8 - cam.x;
        double dz = a.key().minBlockZ() + 8 - cam.z;
        return dx * dx + dz * dz;
    }
}
