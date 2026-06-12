# Guard Combat Redesign — Soldier & Archer (design spec)

Date: 2026-06-12. Status: **implemented** (commits `2f11500`..`52ba0d8`), pending in-game smoke test.

## Problem

Soldiers and archers moved and fought erratically. Root causes found in analysis:

1. Monolithic `EntityAIGuardVillageSoldier/Archer` (targeting + movement + attack + resupply in
   one task) with mutex 2 only — `EntityAIWander`/`EntityAIMoveTowardsRestriction` (mutex 1) ran
   concurrently and fought over the navigator mid-combat.
2. Re-path every tick (`moveTo(target)` per tick restarts the path).
3. Flat 20 damage regardless of weapon, no attack cooldown, 3x sword wear vs creepers.
4. Bow: no draw animation, buggy `previousTime/currentTime` shot timer, fixed velocity/spread,
   no enchantments, no kiting (shot point-blank while being hit).
5. Sticky `HelpfulVillage.lastAggressor` (returned unconditionally while alive) — all guards
   chased one mob forever, even outside the village; no pursuit limit.
6. No patrol; no offhand slot, so no shields.

## Decisions (user-approved)

- Full vanilla-style rewrite (target task / attack task split), not point fixes.
- Weapon-attribute damage + 20t cooldown (wooden sword 5, diamond 7); gear quality matters.
- Shields for soldiers, sourced like tools: guild chests, craft-queue fallback.
- Patrol = round of village doors.
- Creepers: archer priority target; soldier fights them hit-and-run.
- **Generalization requirement:** resupply/patrol/attack AIs are generic over `AbstractVillager`
  (hooks instead of instanceof) — they will later be reused by other professions.
- New trigger: a guard missing armor (or freshly professioned, i.e. no tool) actively travels to
  its guild hall to equip, with a 600t cooldown after an unsuccessful chest search.

## Architecture

| Task | Registry | Prio | Mutex | Role |
|---|---|---|---|---|
| `EntityAIVillageGuardTarget` | targetTasks | 1 | 1 | revenge > nearest IMob in `actualBounds.grow(8)`, LOS preferred, priority predicate (creepers for archer); leash >16 blocks outside bounds or unseen+stuck ~5s |
| `EntityAIGuardResupply` | tasks | 2 | 3 | retreat <50% HP (only thing that interrupts combat) + deposit/tool/armor/ammo/offhand at hall; RETURN handoff to `EntityAIMoveIndoorsCustom` |
| `EntityAIGuardMeleeAttack` | tasks | 3 (archer: 4 fallback) | 3 | EntityAIAttackMelee-style: throttled re-path, 20t cooldown, `getAttackDamage()`, creeper hit-and-run, shield raise/lower |
| `EntityAIGuardBowAttack` | tasks | 3 | 3 | EntityAIAttackRangedBow-style: setActiveHand draw (~20t), charge velocity, difficulty inaccuracy, Power/Punch/Flame via `setEnchantmentEffectsFromEntity`, strafe + kite <5 blocks |
| `EntityAIPatrolVillage` | tasks | 5 | 1 | nearest-neighbour route over `villageDoors` (≤16 waypoints + hall door), 3-5s look-around pauses, 200t waypoint timeout, aborts on attack target |

Removed: `EntityAIGuardVillageSoldier`, `EntityAIGuardVillageArcher`, `EntityArcher.ARROW_TIME`.

`HelpfulVillage.lastAggressor` is now a hint with `lastAggressorTime` + 200t expiry and a bounds
check, not a hard override.

## Shields (offhand)

- `InventoryVillager`: equipment 5→6 slots; combined index 32 = offhand (`OFFHAND_SLOT`,
  `EQUIPMENT_SIZE` constants). Slot validity via `AbstractVillager.acceptsOffhandItem` hook
  (soldier: `ItemShield`). `addItem` auto-equips. NBT: combined-index format, no migration needed.
- Mirrored to `EntityEquipmentSlot.OFFHAND` in `updateArmor` (tracker syncs + renders it).
- Blocking: melee task raises shield (`setActiveHand(OFF_HAND)`) within 6 blocks between swings;
  vanilla `EntityLivingBase` blocking reduces damage. `damageShield` override wears/breaks the
  shield (vanilla is a player-only no-op); blocked hits skip the custom armor-wear (armor wear
  moved after `super.attackEntityFrom` behind a flag).
- Sourcing: `EntityAIGuardResupply.equipOffhand` from guild chests; else craft-queue via
  `getDesiredOffhandItem` + `queuedOffhand` (mirrors `queuedTool`).
- GUI: 6th equipment slot in container (x=133); frame blitted from the tool-slot graphic.
- `InventoryPacket` sized from the shared constants.

## Rendering

`ModelVillagerBiped` (client) sets arm poses from synced hand-active state: BOW_AND_ARROW while
drawing, BLOCK while shielding, ITEM/EMPTY otherwise. Used by `RenderVillagerCustom`.

## Known risks / follow-ups

- Mob shield blocking is generic in `EntityLivingBase` but unused by vanilla mobs — verify
  in-game that blocks register and the pose renders.
- `EntityAIFollowLeader` still uses the legacy instant-shot combat (flagged in code) — port
  follow-mode combat onto the new attack tasks later.
- Patrol on hard terrain relies on the 200t waypoint timeout; observe.
- Vanilla-like damage means a lone soldier can lose to a group — intended.
