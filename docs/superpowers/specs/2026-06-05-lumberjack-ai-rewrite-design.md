# Lumberjack AI Rewrite — Design Spec
_HelpVillagers 1.12.2 port · 2026-06-05_

## Problem

`EntityAILumberjack` never chops in practice:

1. **Village-range block** (`findTree:65`): logs only registered when `!isInRangeOfAnyVillage`.
   The guild hall is inside the village, so any nearby tree is ignored.
2. **Aggressive adjacency filter** (`getNewResource:98–110`): discards any cluster that has
   cobblestone, planks, fence, door, chest, crafting table, stairs, or farmland nearby.
   In a built-up village almost every tree fails this check.
3. **No nav-timeout / stuck detection**: pathfinding failures loop forever.
4. **Fragile scan trigger**: relies on `isMaterialInBB(WOOD)` + random wandering instead of
   deterministic "find nearest log".
5. **No connected-log BFS**: only chops the first block found; never climbs or fells a whole tree.

## Design

### Scope
Rewrite the internals of `gather()` in `EntityAILumberjack`.  Keep the inherited
`EntityAIWorker` cycle (`idle → STORE / CRAFT / RETURN`) untouched — `gather()` still ends
with `return this.idle()`.

Replace `plantSapling` / `shouldPlantSapling` in `EntityLumberjack.onUpdate` with
Thrall-style stump replanting inside the AI. Keep `pickupSaplings` and `SaplingPacket`.

### Sub-state machine (`WoodState` enum, private to `EntityAILumberjack`)

```
SEARCHING  ──(nearest log found outside village)──>  NAVIGATING
NAVIGATING ──(within reach)──>                       CHOPPING
CHOPPING   ──(log broken, connected logs queued)──>  NAVIGATING   (next log)
CHOPPING   ──(queue empty)──>                        SEARCHING
```

**SEARCHING**
- Throttled scan every `SCAN_INTERVAL` ticks (~50t; immediate on first call).
- `findNearestLog()`: MutableBlockPos scan, radius `SCAN_RANGE` (24 blocks),
  Y ± 5 / +20 from thrall Y. Accepts `BlockLog` instances + modded blocks whose registry
  name contains "log". **Skips positions inside `homeVillage.actualBounds`.**
- If no log found: call `getRandOutsideCoords` to wander toward forest; stay SEARCHING.
- If found: set `targetLog`, transition NAVIGATING, reset `navTimer`.

**NAVIGATING**
- Each tick: validate target is still a log; if not → SEARCHING.
- Increment `navTimer`; if `> NAV_TIMEOUT_TICKS` (~200t): abandon target → SEARCHING,
  `lastScanTime = 0` (allow immediate rescan).
- If `noPath()`: request path with `tryMoveToXYZ`; failure → abandon target → SEARCHING.
- If `distanceSq(targetLog) ≤ CLOSE_ENOUGH_SQ` (≈8²): transition CHOPPING.
- `setLookPosition` toward target every tick.

**CHOPPING**
- Validate target each tick; if gone → dequeue next connected log or SEARCHING.
- Walk within 3 blocks; `clearPath()` when within 1.5.
- Swing arm every 5 ticks.
- `miningTicks` incremented each tick; `miningTicksRequired` from `hardness × SPEED_MULT`.
- On break:
  1. `breakBlock(pos)` — play particle event, `getDrops` → `inventory.addItem`, `setBlockToAir`.
  2. `discoverConnectedLogs(pos)` — 26-neighbour BFS; push `dy > 0` to deque front (climb trunk
     first), rest to back.
  3. `tryReplantSapling(pos, minedState)` — if block below is `DIRT` or `GRASS`, consume
     matching sapling from inventory (by meta), place `SAPLING` state; fallback to any sapling.
  4. `consecutiveStuckBlocks` if block survived; abort tree after `MAX_STUCK_BLOCKS` (3).
  5. Poll `connectedLogs` for next target → NAVIGATING; or → SEARCHING.

### Key constants
| Name | Value | Notes |
|------|-------|-------|
| `SCAN_RANGE` | 24 | blocks radius |
| `SCAN_INTERVAL` | 50 | ticks between scans |
| `NAV_TIMEOUT_TICKS` | 200 | ~10 s |
| `CLOSE_ENOUGH_SQ` | 8² = 64 | reach |
| `SPEED_MULTIPLIER` | 15f | hardness → ticks |
| `MIN_MINING_TICKS` | 5 | floor |
| `MAX_STUCK_BLOCKS` | 3 | abandon threshold |

### EntityLumberjack changes
- Remove `plantSapling()`, `shouldPlantSapling()`, `shouldPlant` field, `SaplingPacket.sync()`
  call, `previousTime`/`currentTime` fields (used only by planting).
- Keep `pickupSaplings()`, `foundTree`, `dayCheck()`, `lastResource` reset.

### Debug logging
`[HV][LUMBER]` prefix, server-side only, deduped (emit only on text change).
Covers: state transitions, scan results, nav timeout, stuck abort, replant.
Mirrors the pattern added for `[HV][MINER]`. To be removed after smoke-test.

## Files changed
| File | Change |
|------|--------|
| `ai/EntityAILumberjack.java` | Full rewrite of gather() internals |
| `entity/EntityLumberjack.java` | Remove crude planting, keep pickup/dayCheck |

## Test criteria
1. `./gradlew.bat build` green.
2. Smoke-test in isolated world: lumberjack with guild hall (axe item-frame), axe in inventory,
   forest beyond village edge.
3. Przefiltrowane logi `[HV][LUMBER]` pokazują: SEARCHING→found log→NAVIGATING→CHOPPING→break→
   replant→next log; RETURN przy pełnym ekwipunku; STORE i powrót.
