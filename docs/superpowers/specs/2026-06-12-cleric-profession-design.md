# Cleric Profession — design spec

Date: 2026-06-12. Status: approved by user, pending implementation.
Plan: `docs/superpowers/plans/2026-06-12-cleric-profession.md`.

## Concept

11th profession (id/typeNum **10**): a support villager. Heals and cleanses wounded villagers,
shadows the guards in combat, and blesses (enchants + fully repairs) their equipment as kills
accumulate. Powered by **essence** converted from hostile-mob drops at his guild's brewing stand.

## Guild hall

- Marker: item frame holding a **Bottle o' Enchanting** (`Items.EXPERIENCE_BOTTLE`) next to a door
  (`GuildHall.matchesProfession` case 10).
- Functional requirements inside the hall (scanned over `insideCoords` like `checkChests()`):
  ≥1 **chest**, ≥1 **enchanting table**, ≥1 **brewing stand**. New fields
  `GuildHall.enchantingTablePos` / `brewingStandPos` + `checkClericFacilities()`.
  Healing/cleansing requires the brewing stand (essence conversion); enchanting milestones
  require the enchanting table to exist.

## Resources: essence

- Drop whitelist (hostile-mob drops): rotten flesh, bone, string, gunpowder, spider eye, slime ball.
- Cleric collects drops himself (existing `pickupItems`) and **receives whitelisted drops from
  soldiers/archers** when within 3 blocks (pull-based, in the follow task).
- Conversion at the guild **brewing stand**: 1 whitelisted item → 1 essence, cap 64
  (`EntityCleric.essence`, NBT-persisted). Brew sound + particles. Non-whitelisted surplus and
  overflow drops are deposited into the guild chest.

## Behaviour 1 — heal & cleanse (`EntityAIClericSupport`, prio 2, mutex 3)

Scan village villagers (AbstractVillager, alive, within `actualBounds.grow(8)`):

- **Heal** (target health < **40%** max): approach to ~6 blocks + LOS, throw a real splash potion
  (`EntityPotion`, witch-style aim) with custom effects **Instant Health I (~3 HP)** +
  **Regeneration I 3 s (~2 HP)**. Cost **2 essence**, cooldown **15 s** (300 t).
- **Cleanse** (target has any `isBadEffect()` potion effect): approach to ~3 blocks, remove all
  negative effects, happy-villager particles + level-up sound. Cost **3 essence**, cooldown
  **30 s** (600 t).
- Heal takes priority over cleanse when both apply; nearest patient first. No essence → no service
  (cleric heads to restock instead).

## Behaviour 2 — combat support & blessing

- **Kill counter** (`EntityCleric.killCounter`, NBT): `LivingDeathEvent` in `CommonHooks` — every
  hostile (`IMob`) death within **16 blocks** of a cleric increments that cleric's counter.
- **Milestone ladder** (cumulative, then reset; `enchantTier` NBT 0/1/2):
  | kills | items | enchant power |
  |---|---|---|
  | 15 | 1 sword/bow | 5 |
  | 50 | 2 of armor/sword/bow | 15 |
  | 100 | 3 of armor/sword/bow | 30 — then counter and tier reset to 0 |
- Blessing happens **on the spot**: cleric approaches a nearby guard and enchants equipped items
  (held weapon slot 27, armor 28-31) via `EnchantmentHelper.addRandomEnchantment(rand, stack,
  power, false)`, **and repairs the item to full durability** (`setItemDamage(0)`). Candidate
  selection: only unenchanted items, highest quality first (diamond > iron > ... — ranked by
  ItemSword.getAttackDamage / ItemArmor.damageReduceAmount). Enchantment-table particles + sound.
  Milestone is deferred while the hall lacks an enchanting table.
- **Follow guards** (`EntityAIFollowGuards`, prio 4, mutex 1): when idle (no patient, no pending
  milestone), stay within ~6 blocks of the nearest Soldier/Archer (up to 24 blocks search);
  also performs the drop handover. Cleric has a *reduced* mob-avoidance radius (4 blocks instead
  of 8) so he stays near fights without standing inside them.

## Restock (`EntityAIClericRestock`, prio 3, mutex 3)

Triggers: essence < 10 and whitelisted drops in inventory, or inventory nearly full.
Goes to the brewing stand → converts drops to essence → deposits the rest into the guild chest.
Reuses the RETURN handoff to `EntityAIMoveIndoorsCustom` like `EntityAIGuardResupply`.

## Plumbing (Builder-delta checklist — all verified gaps from that phase covered)

- `entity/EntityCleric` (profession 10, no tool — `isValidTool` false, `canCraft` false; not a
  guard: no GuardResupply/Patrol; keeps anti-stuck, exit-water etc. from `addAI()`).
- Entity registration in `HelpfulVillagers` (next free mod-entity id) + **renderer registration in
  `ClientProxy.preInit`** (RenderVillagerCustom; lesson: preInit, never init).
- `AbstractVillager.changeProfession` case 10; `resetRecipes` handling for profession 10.
- `GuiProfessionDialog`: 11th button "Cleric" (id 10), third-row placement below the panel.
- `RenderVillagerCustom` case 10 → `cleric.png`; texture **generated** (PIL recolor of
  villager.png — purple/gold robe), user may replace the file later.
- lang: `entity.helpfulvillagers.cleric.name=Cleric`.
- NBT on EntityCleric: `Essence`, `KillCounter`, `EnchantTier`.

## Out of scope (YAGNI, flagged for later)

- Dedicated cleric GUI tab/stats; EnumMessage broadcasts for blessings; config knobs for
  costs/cooldowns/thresholds (constants for now); cleric in economy price graph.

## Risks

- `LivingDeathEvent` fires for all deaths — filter server-side + IMob early to keep it cheap.
- Splash potion AoE can graze hostiles (instant health damages undead — acceptable, free bonus).
- Profession dialog button #11 sits below the 230x130 background texture — cosmetic, polish later.
- Enchant power 30 at vanilla `addRandomEnchantment` yields strong results by design (user intent).
