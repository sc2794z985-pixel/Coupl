package com.auditlab.mod.events;

import com.auditlab.mod.analysis.BlockScanner;
import com.auditlab.mod.analysis.ChunkVolume;
import com.auditlab.mod.analysis.GeometryAnalyzer;
import com.auditlab.mod.analysis.model.CavityFinding;
import com.auditlab.mod.analysis.model.ChunkKey;
import com.auditlab.mod.analysis.model.PositionedId;
import com.auditlab.mod.config.AuditLabConfig;
import com.auditlab.mod.export.AuditSession;
import com.auditlab.mod.export.SessionManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Turns loaded client chunks into analysis results without stalling the client thread.
 *
 * <p>On the client thread it only copies each non-empty section's block-state container (a
 * cheap palette + packed-array copy) and lists block entities. Classification, block scanning
 * and geometry analysis run on a single low-priority worker thread.
 *
 * <p>Chunks touched by block-change packets are rescanned after a short debounce so geometry
 * stays current. When the worker falls behind, new chunks are deferred to that same queue
 * instead of holding more snapshots in memory.
 */
public final class ChunkSnapshotter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("AuditLab");
    private static final long RESCAN_DEBOUNCE_MS = 1000;
    private static final int MAX_IN_FLIGHT = 128;
    private static final int MAX_SUBMITS_PER_TICK = 8;

    private record Snapshot(ChunkKey key, int bottomY, int height,
                            List<PalettedContainer<BlockState>> sections, List<PositionedId> blockEntities) {
    }

    /** Per-block-state classification, cached per scan because palettes are small. */
    private record CellInfo(byte cell, String trackedId) {
    }

    private final SessionManager sessions;
    private final Supplier<AuditLabConfig> config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuditLab-Analysis");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final AtomicInteger inFlight = new AtomicInteger();
    /** Client thread only. Value is the earliest time the rescan may run. */
    private final Map<ChunkKey, Long> pending = new LinkedHashMap<>();

    public ChunkSnapshotter(SessionManager sessions, Supplier<AuditLabConfig> config) {
        this.sessions = sessions;
        this.config = config;
    }

    /** Client thread. Called for every chunk the server sends while a session is running. */
    public void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        AuditSession session = sessions.current();
        if (session == null) return;
        ChunkKey key = new ChunkKey(McIds.dimension(world), chunk.getPos().x, chunk.getPos().z);
        if (inFlight.get() >= MAX_IN_FLIGHT) {
            pending.putIfAbsent(key, 0L);
            return;
        }
        submit(session, capture(key, chunk));
    }

    /** Client thread. Schedules a debounced rescan of a chunk whose blocks changed. */
    public void requestRescan(ChunkKey key) {
        pending.putIfAbsent(key, System.currentTimeMillis() + RESCAN_DEBOUNCE_MS);
    }

    /** Client thread. Queues a chunk for scanning on the next ticks, without debounce. */
    public void requestScanNow(ChunkKey key) {
        pending.put(key, 0L);
    }

    /** Client thread, once per tick: drains due rescans while the worker has capacity. */
    public void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        AuditSession session = sessions.current();
        if (world == null || session == null) {
            pending.clear();
            return;
        }
        String dimension = McIds.dimension(world);
        long now = System.currentTimeMillis();
        int submitted = 0;
        Iterator<Map.Entry<ChunkKey, Long>> it = pending.entrySet().iterator();
        while (it.hasNext() && submitted < MAX_SUBMITS_PER_TICK && inFlight.get() < MAX_IN_FLIGHT) {
            Map.Entry<ChunkKey, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            ChunkKey key = e.getKey();
            if (!key.dimension().equals(dimension) || !world.isChunkLoaded(key.x(), key.z())) continue;
            WorldChunk chunk = world.getWorldChunk(new BlockPos(key.minBlockX(), 0, key.minBlockZ()));
            submit(session, capture(key, chunk));
            submitted++;
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    public int inFlightCount() {
        return inFlight.get();
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    private Snapshot capture(ChunkKey key, WorldChunk chunk) {
        ChunkSection[] sectionArray = chunk.getSectionArray();
        List<PalettedContainer<BlockState>> sections = new ArrayList<>(sectionArray.length);
        for (ChunkSection section : sectionArray) {
            sections.add(section == null || section.isEmpty() ? null : section.getBlockStateContainer().copy());
        }
        List<PositionedId> blockEntities = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos p = be.getPos();
            blockEntities.add(new PositionedId(McIds.blockEntity(be.getType()), p.getX(), p.getY(), p.getZ()));
        }
        return new Snapshot(key, chunk.getBottomY(), chunk.getHeight(), sections, blockEntities);
    }

    private void submit(AuditSession session, Snapshot snapshot) {
        inFlight.incrementAndGet();
        try {
            worker.execute(() -> {
                try {
                    analyze(session, snapshot);
                } catch (Throwable t) {
                    LOG.warn("Chunk analysis failed for {}", snapshot.key(), t);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
        }
    }

    /** Worker thread. Touches only the private snapshot copies. */
    private void analyze(AuditSession session, Snapshot s) {
        if (!session.isActive()) return;
        AuditLabConfig cfg = config.get();
        BlockScanner scanner = BlockScanner.fromConfig(cfg);
        ChunkKey key = s.key();
        ChunkVolume volume = new ChunkVolume(key.x(), key.z(), s.bottomY(), s.height());
        int ox = key.minBlockX();
        int oz = key.minBlockZ();

        IdentityHashMap<BlockState, CellInfo> cache = new IdentityHashMap<>();
        List<PositionedId> tracked = new ArrayList<>();
        for (int i = 0; i < s.sections().size(); i++) {
            PalettedContainer<BlockState> container = s.sections().get(i);
            if (container == null) continue; // empty section: all OPEN already
            int baseY = s.bottomY() + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = container.get(x, y, z);
                        CellInfo info = cache.computeIfAbsent(state, st -> classify(st, scanner));
                        if (info.cell() != ChunkVolume.OPEN) volume.set(x, baseY + y, z, info.cell());
                        if (info.trackedId() != null) tracked.add(new PositionedId(info.trackedId(), ox + x, baseY + y, oz + z));
                    }
                }
            }
        }

        BlockScanner.ScanResult scan = scanner.scan(volume, tracked, s.blockEntities());
        List<CavityFinding> cavities = new GeometryAnalyzer(cfg.geometry).analyze(volume);
        if (session.isActive()) session.analyzer().applyScan(key, scan, cavities, System.currentTimeMillis());
    }

    private static CellInfo classify(BlockState state, BlockScanner scanner) {
        byte cell;
        if (state.isAir()) cell = ChunkVolume.OPEN;
        else if (!state.getFluidState().isEmpty()) cell = ChunkVolume.FLUID;
        else if (!state.blocksMovement()) cell = ChunkVolume.OPEN; // torches, rails, carpets of vines...
        else cell = ChunkVolume.SOLID;
        String id = McIds.block(state.getBlock());
        return new CellInfo(cell, scanner.isTrackedBlock(id) ? id : null);
    }
}
