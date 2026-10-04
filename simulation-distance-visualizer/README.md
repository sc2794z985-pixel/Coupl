# Simulation Distance Visualizer

Client-side Fabric mod for **Minecraft Java Edition 1.21.11**. It shows the simulation distance and view distance that the connected server actually sent, and draws the simulated chunk region in the world.

It is a read-only diagnostic tool: no packets are changed, blocked or sent, and nothing is needed on the server.

## Requirements

| Component    | Version            |
|--------------|--------------------|
| Minecraft    | 1.21.11            |
| Fabric Loader| 0.17.3 or newer (built against 0.19.5) |
| Fabric API   | 0.141.6+1.21.11 (or newer for 1.21.11) |
| Java         | 21                 |

## Build

```bash
./gradlew build
```

The mod JAR is `build/libs/simulation-distance-visualizer-1.0.0.jar`. Copy it with Fabric API into `.minecraft/mods`.

Toolchain: Fabric Loom 1.18 (`net.fabricmc.fabric-loom-remap`), Yarn `1.21.11+build.6`, Gradle 9.7.1 (wrapper included).

## Keys

| Key | Action |
|-----|--------|
| H | HUD on/off |
| J | Simulation boundary on/off |
| K | Display mode: Boundary Only / Chunk Grid |
| L | Intensity 25 / 50 / 75 / 100 % (vanilla uses L for Advancements; rebind one of them) |
| I | Debug panel on/off (off by default) |

All keys can be changed under Options → Controls → Key Binds → "Simulation Distance Visualizer".

## How the values are obtained

- **Simulation distance:** sent by the server in `GameJoinS2CPacket` on join and in `SimulationDistanceS2CPacket` when it changes. Vanilla stores it in `ClientPlayNetworkHandler.simulationDistance`; a mixin reads that field at the end (`TAIL`) of `onGameJoin` and `onSimulationDistance`.
- **View distance:** sent in `GameJoinS2CPacket.viewDistance()` and `ChunkLoadDistanceS2CPacket.getDistance()`, read at the end of `onGameJoin` and `onChunkLoadDistance`.
- The local video settings are never used. Values are reset when a connection starts or ends, so switching servers never shows stale numbers.

The simulated region is the square of chunks within the simulation distance (Chebyshev distance) of the player's chunk: `(2 × d + 1)²` chunks. The boundary is aligned to the 16×16 chunk grid, not drawn as a circle.

## Configuration

`config/simulation-distance-visualizer.json`:

```json
{
  "hudEnabled": true,
  "boundaryEnabled": true,
  "displayMode": "BOUNDARY_ONLY",
  "intensity": 50,
  "debugEnabled": false,
  "showWalls": true,
  "showCurrentChunk": true,
  "hudPosition": "TOP_LEFT",
  "notifyOnServerChange": true
}
```

`displayMode` is `BOUNDARY_ONLY` or `CHUNK_GRID`; `hudPosition` is `TOP_LEFT` or `TOP_RIGHT`. Server-sent distances are never written to this file.
