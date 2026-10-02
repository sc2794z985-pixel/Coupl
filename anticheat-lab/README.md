# AntiCheat Lab

A Meteor Client addon for server operators who test chunk-masking and anti-ESP plugins such as
ChunkVeil. It records what your server sends to a legitimately connected client and gives each
chunk a **Chunk Suspicion Score** (0-100) that shows how much a hidden build is exposed.

**Passive only.** The addon reads packets the client has already received. It never sends
packets, moves the player, pathfinds or mines. It only collects in singleplayer and on servers
listed in `allowed-servers`.

## Status

| Phase | Scope | State |
|-------|-------|-------|
| 1 | Project layout, data model, scoring, store, Meteor category and control module | done |
| 2 | Packet scanners and world/HUD rendering | next |

## Build

Requirements: JDK 21. You don't need a local Gradle install because the wrapper (Gradle 9.2.0) is included.

```sh
cd anticheat-lab
./gradlew build          # -> build/libs/anticheat-lab-0.1.0.jar
./gradlew test           # unit tests for the game-independent packages
./gradlew runClient      # dev client (put a matching Meteor jar in run/mods/)
```

Install: copy the jar into `.minecraft/mods/` next to Fabric Loader and the Meteor Client build
for the **same** Minecraft version.

Version pins in `gradle.properties` and `build.gradle` match the official
`meteor-addon-template` at its 1.21.11 commit:

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Yarn | 1.21.11+build.3 |
| Fabric Loader | 0.18.2 |
| Fabric Loom | 1.14-SNAPSHOT |
| Meteor Client | 1.21.11-SNAPSHOT |

To target an earlier 1.21.x release, copy the versions from the template's commit for that
release (`git log` on MeteorDevelopment/meteor-addon-template) into `gradle.properties` and the
Loom version in `build.gradle`.

## Layout

```
src/main/java/com/anticheatlab/
├── AntiCheatLab.java          Meteor entrypoint: registers the category and modules
├── model/                     plain Java, no Minecraft imports
│   ├── ChunkCoord             dimension + chunk x/z
│   ├── LeakType               what was leaked; maps to a category, default weight, dedupe rule
│   ├── ScoreCategory          score buckets and their default caps (sum = 100)
│   ├── LeakEvent              one immutable observation produced by a scanner
│   ├── ChunkObservation       thread-safe per-chunk aggregate (counts, dedupe, recent events)
│   ├── ChunkScore             immutable 0-100 score + per-category breakdown + explain()
│   └── Severity               CLEAN / LOW / MEDIUM / HIGH / CRITICAL bands and colours
├── scoring/                   ScoringConfig (weights, caps) and ScoreCalculator
├── data/                      ObservationStore: bounded, concurrent, in-memory chunk index
├── safety/                    AuthorizationPolicy: singleplayer or allowlisted servers only
├── modules/                   ChunkAuditor: Meteor module, settings and ranked-chunk panel
├── scanner/                   Phase 2: passive packet scanners
└── render/                    Phase 2: world overlays and HUD
```

Data flow: packet → scanner → `LeakEvent` → `ObservationStore` → `ChunkObservation` →
`ScoreCalculator` → `ChunkScore` → GUI / renderer.

## Scoring model

For each category, `raw = Σ count(type) × weight(type)` and `points = min(raw, cap)`. The score
is `round(Σ points)`, clamped to 0..100. With the default caps, a chunk that maxes out every
category scores exactly 100.

| Category | Cap | Leak types (weight per event) |
|----------|----:|-------------------------------|
| Suspicious blocks | 30 | `PLACED_BLOCK` 1.0, `FUNCTIONAL_BLOCK` 3.0 |
| Block entities | 25 | `STORAGE_BLOCK_ENTITY` 5.0, `OTHER_BLOCK_ENTITY` 2.0 |
| Cavity geometry | 20 | `CAVITY` 4.0 |
| Update anomalies | 15 | `BLOCK_UPDATE` 0.5, `BLOCK_ENTITY_UPDATE` 1.0, `BLOCK_EVENT` 1.5 |
| Sounds | 10 | `BLOCK_SOUND` 1.0 |

Static leak types (blocks, block entities, cavities) are counted once per block position,
because the server re-sends chunks on its own. Dynamic types (updates, block events, sounds) are
counted every time they arrive, because how often they arrive is the signal. Category caps can
be changed in the module's *Scoring* settings group.

## Using it (Phase 1)

1. Open the Meteor GUI and find the **AntiCheat Lab** category.
2. Under *Safety*, add your test server's address to **allowed-servers**.
3. Turn on **chunk-auditor**. Its settings panel shows the collection status, store statistics
   and the top-scoring chunks, with *Refresh* and *Clear data* buttons.

Nothing produces events until the Phase 2 scanners exist. Scanners must check
`ChunkAuditor#isCollecting()` before they record anything.
