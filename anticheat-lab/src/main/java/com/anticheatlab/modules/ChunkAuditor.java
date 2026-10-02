package com.anticheatlab.modules;

import com.anticheatlab.AntiCheatLab;
import com.anticheatlab.data.ObservationStore;
import com.anticheatlab.model.ChunkScore;
import com.anticheatlab.model.ScoreCategory;
import com.anticheatlab.model.Severity;
import com.anticheatlab.safety.AuthorizationPolicy;
import com.anticheatlab.scoring.ScoringConfig;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Control module for the lab: owns the authorization guard and the scoring settings, and shows
 * the ranked chunk list in its settings screen. Phase 2 scanners call {@link #isCollecting()}
 * before recording anything.
 */
public class ChunkAuditor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgScoring = settings.createGroup("Scoring");

    private final Setting<Integer> listSize = sgGeneral.add(new IntSetting.Builder()
        .name("list-size")
        .description("How many top-scoring chunks to show in this panel.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 50)
        .build()
    );

    private final Setting<Boolean> clearOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-on-disconnect")
        .description("Discard all observations when leaving a world or server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> allowedServers = sgSafety.add(new StringListSetting.Builder()
        .name("allowed-servers")
        .description("Servers you operate and are auditing. Collection is disabled everywhere else (singleplayer is always allowed).")
        .defaultValue(List.of("localhost", "127.0.0.1"))
        .build()
    );

    private final Map<ScoreCategory, Setting<Integer>> caps = new EnumMap<>(ScoreCategory.class);

    private volatile boolean authorized;

    public ChunkAuditor() {
        super(AntiCheatLab.CATEGORY, "chunk-auditor",
            "Passively scores chunks by how much hidden-build information the server leaks to this client.");

        for (ScoreCategory category : ScoreCategory.values()) {
            caps.put(category, sgScoring.add(new IntSetting.Builder()
                .name(category.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-cap")
                .description("Maximum points '" + category.displayName() + "' can contribute to a chunk's score.")
                .defaultValue(category.defaultCap())
                .min(0)
                .max(100)
                .sliderRange(0, 100)
                .build()
            ));
        }
    }

    @Override
    public void onActivate() {
        if (mc.world == null) {
            // Armed from the main menu; the guard is evaluated on join.
            authorized = false;
            return;
        }
        if (!refreshAuthorization()) {
            error("Current server is not in allowed-servers; Chunk Auditor disabled.");
            toggle();
            return;
        }
        info("Collecting passively on %s.", currentTargetName());
    }

    @Override
    public void onDeactivate() {
        authorized = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!refreshAuthorization()) {
            warning("Joined a server that is not in allowed-servers; Chunk Auditor disabled.");
            toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        authorized = false;
        if (clearOnDisconnect.get()) AntiCheatLab.store().clear();
    }

    /** True only while the module is on and connected to an authorized target. */
    public boolean isCollecting() {
        return isActive() && authorized;
    }

    public ScoringConfig scoringConfig() {
        ScoringConfig config = ScoringConfig.defaults();
        for (Map.Entry<ScoreCategory, Setting<Integer>> e : caps.entrySet()) {
            config = config.withCap(e.getKey(), e.getValue().get());
        }
        return config;
    }

    private boolean refreshAuthorization() {
        ServerInfo server = mc.getCurrentServerEntry();
        authorized = AuthorizationPolicy.isAuthorized(
            mc.isInSingleplayer(), server == null ? null : server.address, allowedServers.get());
        return authorized;
    }

    private String currentTargetName() {
        if (mc.isInSingleplayer()) return "singleplayer";
        ServerInfo server = mc.getCurrentServerEntry();
        return server == null ? "unknown" : server.address;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = list.add(theme.table()).expandX().widget();
        fillTable(theme, table);

        WHorizontalList buttons = list.add(theme.horizontalList()).expandX().widget();
        WButton refresh = buttons.add(theme.button("Refresh")).widget();
        refresh.action = () -> {
            table.clear();
            fillTable(theme, table);
        };
        WButton clear = buttons.add(theme.button("Clear data")).widget();
        clear.action = () -> {
            AntiCheatLab.store().clear();
            table.clear();
            fillTable(theme, table);
        };
        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        ObservationStore store = AntiCheatLab.store();
        String status = isCollecting() ? "Collecting" : isActive() ? "Armed (not authorized / not in world)" : "Off";

        table.add(theme.label("Status: " + status));
        table.row();
        table.add(theme.label(String.format("Chunks: %d   Events: %d counted, %d duplicate",
            store.size(), store.countedEvents(), store.duplicateEvents())));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        List<ChunkScore> top = store.topScores(listSize.get(), scoringConfig());
        if (top.isEmpty()) {
            table.add(theme.label("No suspicious chunks observed yet."));
            table.row();
            return;
        }

        for (ChunkScore score : top) {
            Severity severity = score.severity();
            WLabel label = table.add(theme.label(String.format("%3d  %-8s  %s  (top: %s)",
                score.total(), severity, score.coord(), topCategory(score)))).widget();
            label.color = new Color(severity.red(), severity.green(), severity.blue());
            table.row();
        }
    }

    private static String topCategory(ChunkScore score) {
        ScoreCategory best = null;
        for (ScoreCategory c : ScoreCategory.values()) {
            if (best == null || score.points(c) > score.points(best)) best = c;
        }
        return best == null ? "-" : best.displayName();
    }
}
