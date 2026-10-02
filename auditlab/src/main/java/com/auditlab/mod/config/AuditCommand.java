package com.auditlab.mod.config;

import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.ScoreReason;
import com.auditlab.mod.events.AuditEventListener;
import com.auditlab.mod.events.McIds;
import com.auditlab.mod.export.AuditSession;
import com.auditlab.mod.export.SessionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side {@code /auditlab} command (never sent to the server):
 * {@code status}, {@code inspect}, {@code overlay}, {@code labels}, {@code clear}, {@code reload}.
 */
public final class AuditCommand {
    private AuditCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, ConfigManager configs,
                                SessionManager sessions, AuditEventListener listener) {
        dispatcher.register(literal("auditlab")
            .executes(ctx -> status(ctx, sessions, listener))
            .then(literal("status").executes(ctx -> status(ctx, sessions, listener)))
            .then(literal("inspect").executes(ctx -> inspect(ctx, sessions)))
            .then(literal("overlay").executes(ctx -> {
                configs.get().overlayEnabled = !configs.get().overlayEnabled;
                configs.save();
                return feedback(ctx, "Overlay " + (configs.get().overlayEnabled ? "on" : "off"));
            }))
            .then(literal("labels").executes(ctx -> {
                configs.get().labelsEnabled = !configs.get().labelsEnabled;
                configs.save();
                return feedback(ctx, "Labels " + (configs.get().labelsEnabled ? "on" : "off"));
            }))
            .then(literal("clear").executes(ctx -> {
                AuditSession s = sessions.current();
                if (s == null) return error(ctx, "No active session.");
                s.analyzer().clear();
                return feedback(ctx, "Cleared session data.");
            }))
            .then(literal("reload").executes(ctx -> {
                AuditLabConfig cfg = configs.load();
                sessions.applyConfig(cfg);
                if (configs.lastError() != null) {
                    error(ctx, "Could not read " + configs.file().toAbsolutePath() + ", using defaults: " + configs.lastError());
                } else {
                    feedback(ctx, "Reloaded " + configs.file().toAbsolutePath() + " (allowedServers " + cfg.allowedServers + ").");
                }
                var client = ctx.getSource().getClient();
                listener.evaluateAccess(client, client.getNetworkHandler());
                return 1;
            })));
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx, SessionManager sessions, AuditEventListener listener) {
        if (sessions.current() == null) {
            var client = ctx.getSource().getClient();
            feedback(ctx, "Not collecting: " + listener.describeAccess(client, client.getNetworkHandler()));
        }
        AuditSession s = sessions.latest();
        if (s == null) return 1;
        var a = s.analyzer();
        feedback(ctx, String.format(Locale.ROOT, "Session %s on %s (%s): %d chunks, %d scans, %d events",
            s.id().toString().substring(0, 8), s.target(), s.isActive() ? "active" : "ended",
            a.chunkCount(), a.scansApplied(), a.eventsRecorded()));
        for (ChunkAnalysis c : a.top(5)) {
            ScoreReason top = c.score().reasons().get(0);
            send(ctx, Text.literal(String.format(Locale.ROOT, "  %3d %-6s %s  %s",
                c.score().total(), c.score().severity(), c.key(), top)).formatted(color(c)));
        }
        return 1;
    }

    private static int inspect(CommandContext<FabricClientCommandSource> ctx, SessionManager sessions) {
        AuditSession s = sessions.current();
        if (s == null) return error(ctx, "No active session.");
        var player = ctx.getSource().getPlayer();
        var cp = player.getChunkPos();
        ChunkKey key = new ChunkKey(McIds.dimension(ctx.getSource().getWorld()), cp.x, cp.z);
        Optional<ChunkAnalysis> found = s.analyzer().analysis(key);
        if (found.isEmpty()) return feedback(ctx, key + ": nothing observed.");
        ChunkAnalysis c = found.get();
        send(ctx, Text.literal(key + ": score " + c.score().total() + " (" + c.score().severity() + ")").formatted(color(c)));
        for (ScoreReason r : c.score().reasons()) {
            feedback(ctx, "  " + r + (r.capped() ? String.format(Locale.ROOT, " [capped, raw %.1f]", r.raw()) : ""));
        }
        for (CavityFinding f : c.cavities()) {
            feedback(ctx, String.format(Locale.ROOT, "  %s %dx%dx%d at %d %d %d  fill %.2f  floor %.2f  ceil %.2f  walls %.2f%s%s",
                f.kind().label(), f.sizeX(), f.sizeY(), f.sizeZ(), f.minX(), f.minY(), f.minZ(),
                f.fillRatio(), f.floorFlatness(), f.ceilingFlatness(), f.wallAlignment(),
                f.clipped() ? "  clipped" : "", f.opensToSurface() ? "  open" : ""));
        }
        return 1;
    }

    private static Formatting color(ChunkAnalysis c) {
        return switch (c.score().severity()) {
            case HIGH -> Formatting.RED;
            case MEDIUM -> Formatting.YELLOW;
            case LOW -> Formatting.GREEN;
            case NONE -> Formatting.GRAY;
        };
    }

    private static int feedback(CommandContext<FabricClientCommandSource> ctx, String message) {
        send(ctx, Text.literal(message));
        return 1;
    }

    private static int error(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendError(Text.literal(message));
        return 0;
    }

    private static void send(CommandContext<FabricClientCommandSource> ctx, Text text) {
        ctx.getSource().sendFeedback(Text.literal("[AuditLab] ").formatted(Formatting.AQUA).append(text));
    }
}
