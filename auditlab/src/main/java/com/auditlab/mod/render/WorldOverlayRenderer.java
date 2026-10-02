package com.auditlab.mod.render;

import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.config.AuditLabConfig;
import com.auditlab.mod.events.McIds;
import com.auditlab.mod.export.AuditSession;
import com.auditlab.mod.export.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws, in {@link WorldRenderEvents#LAST}:
 * <ul>
 *   <li>a box over each scored chunk, spanning the Y range of its observations and findings,
 *       coloured by severity (green / yellow / red); depth-tested like normal world geometry</li>
 *   <li>a camera-facing label above the box with the score and top reasons</li>
 * </ul>
 * {@code context.consumers()} is null in LAST on 1.21.8, so the renderer uses the shared entity
 * buffer and flushes it itself.
 */
public final class WorldOverlayRenderer {
    private static final float BOX_ALPHA = 0.9f;
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
        WorldRenderEvents.LAST.register(this::render);
    }

    private void render(WorldRenderContext context) {
        AuditLabConfig cfg = config.get();
        AuditSession session = sessions.current();
        if (session == null || !cfg.overlayEnabled || context.world() == null) return;

        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        ChunkKey center = ChunkKey.ofBlock(McIds.dimension(context.world()), MathHelper.floor(cam.x), MathHelper.floor(cam.z));
        List<ChunkAnalysis> visible = session.analyzer().analysesNear(center, cfg.renderDistanceChunks);
        if (visible.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();

        VertexConsumer lines = buffers.getBuffer(RenderLayer.getLines());
        for (ChunkAnalysis a : visible) drawChunkBox(matrices, lines, a, cam);
        buffers.draw(RenderLayer.getLines());

        if (cfg.labelsEnabled && cfg.maxLabels > 0) {
            visible.sort(Comparator.comparingDouble(a -> squaredDistance(a, cam)));
            TextRenderer text = client.textRenderer;
            int drawn = 0;
            for (ChunkAnalysis a : visible) {
                if (drawn++ >= cfg.maxLabels) break;
                drawLabel(matrices, buffers, text, camera, a, LabelFormatter.lines(a, cfg.labelReasonLines));
            }
        }
        buffers.draw();
    }

    private static void drawChunkBox(MatrixStack matrices, VertexConsumer lines, ChunkAnalysis a, Vec3d cam) {
        var severity = a.score().severity();
        double x1 = a.key().minBlockX() - cam.x;
        double z1 = a.key().minBlockZ() - cam.z;
        double y1 = a.minY() - cam.y;
        double y2 = a.maxY() + 1 - cam.y;
        VertexRendering.drawBox(matrices, lines, x1, y1, z1, x1 + 16, y2, z1 + 16,
            severity.red(), severity.green(), severity.blue(), BOX_ALPHA);
    }

    private static void drawLabel(MatrixStack matrices, VertexConsumerProvider buffers, TextRenderer text, Camera camera,
                                  ChunkAnalysis a, List<String> lines) {
        Vec3d cam = camera.getPos();
        double x = a.key().minBlockX() + 8 - cam.x;
        double y = a.maxY() + 1 + LABEL_LIFT - cam.y;
        double z = a.key().minBlockZ() + 8 - cam.z;
        // Grow with distance so labels stay readable across the render radius.
        float scale = LABEL_SCALE * (float) Math.max(1.0, Math.sqrt(x * x + y * y + z * z) / 12.0);

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int headerColor = a.score().severity().argb(0xFF);
        float lineY = -lines.size() * LINE_HEIGHT;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float lineX = -text.getWidth(line) / 2f;
            int color = i == 0 ? headerColor : 0xFFFFFFFF;
            text.draw(line, lineX, lineY, color, false, matrix, buffers, TextRenderer.TextLayerType.SEE_THROUGH,
                LABEL_BACKGROUND, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            lineY += LINE_HEIGHT;
        }
        matrices.pop();
    }

    private static double squaredDistance(ChunkAnalysis a, Vec3d cam) {
        double dx = a.key().minBlockX() + 8 - cam.x;
        double dz = a.key().minBlockZ() + 8 - cam.z;
        return dx * dx + dz * dz;
    }
}
