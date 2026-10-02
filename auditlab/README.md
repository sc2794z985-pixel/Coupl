# AuditLab

A client-side Fabric mod for server operators who test chunk-masking and anti-ESP plugins. It
measures what a server sends to a legitimately connected client and gives each chunk an
exposure score (0-100), with a breakdown of the reasons.

**Passive only.** AuditLab reads packets the client has already received. It never sends
packets, moves the player, pathfinds, mines or otherwise interacts with the world. It only
collects in singleplayer and on servers listed in `allowedServers` in `config/auditlab.json`
(default: `localhost`, `127.0.0.1`).

## Status

| Phase | Scope | State |
|-------|-------|-------|
| 1 | Entry point, event pipeline, data model, block scanner, geometry analyser, scoring, world overlay | done |
| 2 | JSON session export, session comparison | next |

## Build

Requirements: JDK 21. The Gradle wrapper (9.2.0) is included.

```sh
cd auditlab
./gradlew build        # -> build/libs/auditlab-0.1.1.jar
./gradlew test         # unit tests for the game-independent packages
./gradlew runClient    # dev client
```

Install: put the jar and Fabric API into `.minecraft/mods/` on Fabric Loader for Minecraft 1.21.11.

| Component | Version | Source of the pin |
|-----------|---------|-------------------|
| Minecraft | 1.21.11 | |
| Yarn | 1.21.11+build.3 | Meteor Client `1.21.11` branch (Yarn build) |
| Fabric Loader | 0.18.2 | Meteor Client `1.21.11` branch |
| Fabric Loom | 1.14-SNAPSHOT | Meteor Client `1.21.11` branch |
| Gradle | 9.2.0 | Meteor Client `1.21.11` branch |
| Fabric API | 0.141.6+1.21.11 | fabric-example-mod `1.21.11` branch |

1.21.11 is the last version with Yarn mappings. From 26.x Minecraft ships unobfuscated and
Fabric uses Mojang names, so a port beyond 1.21.11 means renaming Minecraft references (the
`analysis` packages have none).

## Controls

| Input | Action |
|-------|--------|
| `O` | Toggle chunk overlay |
| `L` | Toggle labels |
| `/auditlab status` | Session summary and top 5 chunks |
| `/auditlab inspect` | Full breakdown and cavity metrics for the chunk you're standing in |
| `/auditlab overlay`, `/auditlab labels` | Toggle overlay / labels |
| `/auditlab clear` | Discard the current session's data |
| `/auditlab reload` | Reload `config/auditlab.json` and rescore |

The command is client-side only and is never sent to the server.

## Layout

```
com.auditlab.mod
├── AuditLabMod                  client entrypoint: wires everything below
├── config/                      AuditLabConfig (all toggles, weights, thresholds), ConfigManager (JSON),
│                                AuditKeyBindings, AuditCommand
├── events/                      AuditEvents (Fabric Event<> per packet type), AuditEventListener (central
│                                registration + session lifecycle), ChunkSnapshotter (client thread → worker),
│                                ExposureClassifier, McIds
├── mixin/                       ClientPlayNetworkHandlerMixin: read-only RETURN injections on packet handlers
├── analysis/                    plain Java, no Minecraft imports
│   ├── BlockScanner             tracked block / block-entity tallies, buried vs exposed
│   ├── GeometryAnalyzer         flood-fill cavity detection + shape metrics
│   ├── ChunkVolume              dense OPEN / SOLID / FLUID grid for one chunk
│   ├── ChunkScorer              capped, normalised 0-100 score with reasons
│   ├── ChunkAnalyzer            per-session store: raw observations, geometry, cached analyses
│   └── model/                   ChunkKey, ChunkObservation (raw), ChunkScore / ChunkAnalysis /
│                                CavityFinding (inference), ObservedEvent, ScoreReason, Severity, ...
├── render/                      WorldOverlayRenderer (END_EXTRACTION → END_MAIN), LabelFormatter
├── export/                      AuditSession, SessionManager (Phase 2: JSON exporter)
└── safety/                      AccessPolicy (singleplayer / allowlist check)
```

### Data flow

```
packets ─► mixin (RETURN, client thread) ─► AuditEvents ─► AuditEventListener ─► ExposureClassifier
                                                                              └► ObservedEvent ─► ChunkObservation (raw)
ClientChunkEvents.CHUNK_LOAD ─► ChunkSnapshotter.capture (section container copies, block entities)
                              └► worker: ChunkVolume ─► BlockScanner ─► ChunkObservation (raw)
                                                    └► GeometryAnalyzer ─► CavityFinding (inference)
ChunkAnalyzer.analysis(key) ─► ChunkScorer ─► ChunkScore + ChunkAnalysis (inference, cached) ─► overlay / command

WorldRenderEvents.END_EXTRACTION ─► visible ChunkAnalysis + label text ─► WorldRenderState (RenderStateDataKey)
WorldRenderEvents.END_MAIN       ─► chunk boxes (VertexRendering.drawOutline, RenderLayers.lines) + labels
```

Raw observations and inferences are stored separately. `ChunkObservation` holds only what the
server sent. `CavityFinding`, `ChunkScore` and `ChunkAnalysis` are derived from it, and are
recomputed whenever the raw data or the config changes.

Threading: packet handlers and chunk-load callbacks run on the client thread and only copy
data. Block classification, scanning and geometry analysis run on one low-priority worker
thread, at about 0.5-1.5 ms per full-height chunk. Chunks touched by block-change packets are
rescanned after a 1-second debounce.

## Scoring

Per category, `raw = Σ count × weight` and `points = round(min(raw, cap))`. The total is the sum
of category points, clamped to 0-100. Every point appears in exactly one reason line.

| Category | Cap | Inputs |
|----------|----:|--------|
| Tracked Blocks | 30 | Buried non-block-entity blocks: utility (crafting table, anvil, ...), redstone, placed lighting/glass |
| Unexpected BlockEntity | 25 | Buried block entities: chest, barrel, shulker box, beacon, furnace, sign, ... |
| Artificial Geometry | 25 | Per cavity: room 12, corridor 6, shaft 4 points, each × confidence |
| Hidden Activity | 20 | Dynamic events × type weight × context (unloaded 1.5, underground 1.0, surface 0) |

- **Buried** means at least `buriedDepth` (default 6) blocks below the top non-air block of
  the column. Surface builds are normally visible, so they are not scored unless
  `scoreExposedBlocks` is true. Roofed surface builds taller than `buriedDepth` will count as
  buried.
- **Hidden Activity** ignores events within `ignoreRadiusAroundPlayer` (default 8) of you, so
  your own mining doesn't count. Entity spawns score only for listed types (item frames, armor
  stands, minecarts, items, ...), so ordinary mob spawning stays at 0. Sounds are recorded for
  the `BLOCKS`, `PLAYERS` and `RECORDS` categories only.
- **Severity bands:** green (LOW) is 1-29, yellow (MEDIUM) is 30-59, red (HIGH) is 60 and above.
  `mediumThreshold` and `highThreshold` change them.

Example label:

```
Chunk 12, -40  Score 51 (MEDIUM)
+25 Artificial Geometry (2 rooms, 1 corridor)
+16 Unexpected BlockEntity (3x chest, 1x barrel)
```

## Geometry heuristics

Air below the column surface is treated as enclosed. Torches, rails, vines and anything else
you can walk through count as air too. Enclosed air is grouped into 6-connected components.
Components with 12-4096 cells and a height of at least 2 are then measured:

| Metric | Meaning | Box room | Natural cave |
|--------|---------|---------:|-------------:|
| fill ratio | volume ÷ bounding-box volume | 1.0 | ~0.4-0.6 |
| floor flatness | share of floor faces on the most common Y | 1.0 | low |
| ceiling flatness | same for ceilings | 1.0 | low |
| wall alignment | share of wall faces on the dominant plane per direction | 1.0 | low |

A component is reported when `fill ≥ 0.80` and the mean of the three flatness and alignment
metrics is `≥ 0.85`. It is classified as a room, a corridor (≤ 2 wide, ≥ 4 long) or a shaft
(≤ 2×2, ≥ 4 tall). Findings also note whether they were clipped by the chunk edge and whether
they open to the sky.

**Known false positives:** generated structures are rectilinear too: dungeons, mineshafts,
strongholds, trial chambers and ancient cities. A masking plugin that hides player builds but
leaves these structures intact will still produce some geometry points there. Compare the
results against a control world.
