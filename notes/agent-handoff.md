# Agent Handoff Prompt — Helpful Villagers backport (1.7.10 → 1.12.2)

> Paste everything below (or point the new agent at this file) to continue the work cold.
> Conversations with the user are in **Polish**; code/comments/commits in **English**.

---

## 0. Your mission

You are an expert Java / Minecraft Forge developer. You are **forward-porting** the mod
**"Helpful Villagers"** (originally by **WeaselNinja**, MIT, port done with permission) from
**Forge 1.7.10 → Forge 1.12.2**. A previous agent did Discovery, the plan, the build skeleton,
and most of the tightly-coupled core. **Your job is to finish the port, reach a green build, then
hand it back for in-game testing.** After the port is done the user will *expand* the mod (new
professions, richer economy, etc.) — so keep the architecture clean and extensible, don't hard-code
where a small abstraction would help, but **do not add new features during the port** unless asked.

---

## 1. Where everything is

| Thing | Path |
|---|---|
| Repo root (1.12.2 target) | `E:\Isuth\HelpVillBackport\` |
| New source (what you write) | `E:\Isuth\HelpVillBackport\src\main\java\com\spege\helpfulvillagers\` |
| **READ-ONLY** 1.7.10 reference (decompiled) | `E:\Isuth\HelpVillBackport\helpfulvillagers\decompiled_src\mods\helpfulvillagers\` |
| Original jar (assets, mcmod.info) | `E:\Isuth\HelpVillBackport\helpfulvillagers-1.7.10-1.3.1.jar` |
| Living dev log (UPDATE IT) | `E:\Isuth\HelpVillBackport\notes\claude.md` |
| Migration plan / strategy | `C:\Users\spege\.claude\plans\backport-helpfulvillagers.md` |
| Family-wide rules (MUST follow) | `E:\Isuth\modDev\notes\agent_notes.md` |
| Build template reference | `E:\Isuth\submod\itcore\` |
| **MCP SRG→name mapping (critical, see §6)** | `C:\Users\spege\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_stable\39\fields.csv` |

**Read first, in order:** `notes/claude.md` (progress table + decisions) → the migration plan →
`E:\Isuth\modDev\notes\agent_notes.md` (CRITICAL RULES). Then read the already-ported files under
`src/main` to absorb the established patterns before writing new ones.

---

## 2. Hard rules (non-negotiable)

- **Forge 1.12.2 ONLY.** `setUnlocalizedName(...)` NOT `setTranslationKey(...)`. No `var`, no `record`. Java 8 max.
- **Never modify** the `helpfulvillagers/` reference tree or the original jar — read-only.
- **ItemStack:** empty = `ItemStack.EMPTY`, never `null`. Use `isEmpty()`, `getCount()`, `setCount()`,
  `shrink()`, `grow()`, `copy()`, `getDisplayName()`. Inventory slots are never null.
- **Multiplayer / threading:** mutate world/entities/NBT server-side. Packet handlers must NOT touch the
  world on the netty thread — wrap in `ctx.getServerHandler().player.getServerWorld().addScheduledTask(...)`
  (server) / `Minecraft.getMinecraft().addScheduledTask(...)` (client). (See §9 — packets not yet ported.)
- **`@SuppressWarnings("null")`** on entities/handlers/GUIs/etc. (Forge `@Nonnull` lint is aggressive).
  Add `"deprecation"` too where you override/call deprecated vanilla (e.g. `getProfession`, `getStateFromMeta`).
- **modid** `helpfulvillagers`, **package** `com.spege.helpfulvillagers.*`. lowercase resources.
- Comments in English. Commit trailer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`.
- Keep WeaselNinja attribution in `mcmod.info` / notes.

---

## 3. Build & verification

- Requires **JDK 8** (Gradle 4.9 / ForgeGradle 3 can't run on the system default JDK 24).
  `gradle.properties` already pins `org.gradle.java.home=C:/Program Files/Java/jdk1.8.0_361`.
- Build: from repo root run `.\gradlew.bat build --console=plain` (PowerShell). First build is cached now,
  incremental builds ~1 min. Exit code 0 = green (ignore the JVM "restricted method" WARNINGs — harmless).
- **Agent does NOT run the game.** `runClient`/in-game smoke testing is the **user's** job. Never block on it.
- Forge `1.12.2-14.23.5.2860`, mappings `snapshot 20171003-1.12`.

---

## 4. THE KEY STRATEGIC FACT: the core is one big SCC

The mod's core is a **strongly-connected dependency cluster** — these all reference each other cyclically:

```
AbstractVillager ↔ InventoryVillager ↔ VillageEconomy ↔ crafting/* ↔ village/* ↔ util/AIHelper
```

**Consequence:** you cannot get a green build until the *entire* CORE cluster (plus the packets it
references) is ported. So:
- Port files in dependency-ish order, but **expect red builds** until CORE is complete.
- **Commit WIP checkpoints** anyway (every few files) with messages prefixed
  `WIP CORE/<sub>: ... (does not compile yet)` so work isn't lost. The user explicitly approved this.
- When CORE + its packets are all present, run the build and **enter the fix-loop**: build → read the
  first batch of `javac` errors → fix → rebuild → repeat until green. This is where most real bugs
  surface (the SRG decompile + manual translation hides type errors until the compiler sees them).
- Only after CORE is green do the on-top layers (remaining packets, GUI, render, command, fish hook),
  which *can* build incrementally.

---

## 5. Exact current status (update `notes/claude.md` as you go)

**Green & committed (build passes):** Step 0 skeleton+build, Step 1 enums, Step 2 config.

**Ported in code, WIP-committed, NOT yet compiling (part of CORE):**
- `crafting/`: `VillagerRecipe` (modernized to `getIngredients()`), `CraftItem`, `CraftTree`, `CraftQueue`
- `econ/`: `ItemPrice`, `VillageEconomy` (price calc made SYNCHRONOUS — see §8)
- `inventory/`: `InventoryVillager` (full IInventory migration), `ContainerInventoryVillager`
- `village/`: `HelpfulVillage`, `GuildHall`, `RanchGuildHall`, `HelpfulVillageCollection` (WorldSavedData)
- `entity/`: `AbstractVillager` (the 1246-line hub), `EntityRegularVillager`, `EntityLumberjack`

**STILL TO PORT for CORE:**
1. `entity/`: **EntityMiner** (245), **EntityFarmer** (153), **EntitySoldier** (85), **EntityArcher** (89),
   **EntityMerchant** (68), **EntityFisherman** (160), **EntityRancher** (125).
   (Reference: `helpfulvillagers/decompiled_src/mods/helpfulvillagers/entity/*.java`.)
2. `util/`: **AIHelper** (341), **ResourceCluster** — these are NOT leaves; they reference entity/village/
   inventory/crafting + use ChunkCoordinates→BlockPos. Port them with the rest of CORE.
3. `ai/`: **11 classes** `EntityAIBase` subclasses (EntityAIFarmer, EntityAIFisherman, EntityAIFollowLeader,
   EntityAIGuardVillageArcher, EntityAIGuardVillageSoldier, EntityAILumberjack, EntityAIMiner,
   EntityAIMoveIndoorsCustom, EntityAIRancher, EntityAIVillagerMateCustom, EntityAIWorker).
4. **CORE-referenced packets** (~15): the code already calls these, so they must exist before CORE builds:
   `LeaderPacket, SwingPacket, InventoryPacket, SaplingPacket, CustomRecipesPacket, CraftItemClientPacket,
   CraftQueueClientPacket, ItemPriceClientPacket, ItemPriceServerPacket, PlayerAccountClientPacket,
   PlayerMessagePacket, UnlockedHallsPacket, VillageSyncPacket` (+ any others referenced once entities land).
5. **Main class wiring:** add `public static SimpleNetworkWrapper network;` + the static collections the
   reference `HelpfulVillagers` used (`villages`, `villager_id`, `player_guard`, `checkedFrames`,
   `villageCollection`, the per-profession recipe lists `lumberjackRecipes`/`minerRecipes`/`farmerRecipes`/
   `fishermanRecipes`/`rancherRecipes`/`allCrafting`/`allSmelting`, `vanillaRecipes`), the entity
   registration, the network channel + `registerMessage` for all 27 packets, the GUI handler registration,
   and `initVillagerRecipes()`. Use the reference `main/HelpfulVillagers.java` as the spec.

**AFTER CORE is green (on-top layers, can build incrementally):**
- Remaining packets (27 total — see reference `network/`), each `IMessage`+`IMessageHandler`, **with
  main-thread scheduling** (the 1.7.10 originals mutate entities directly on the netty thread).
- **GUI** (8 screens + `GuiHandler`): `GuiVillagerDialog, GuiProfessionDialog, GuiVillagerInventory,
  GuiNickname, GuiCraftingMenu, GuiCraftStats, GuiTeachRecipe, GuiBarter`. `IGuiHandler` still exists in
  1.12.2. `GuiScreen`/`GuiContainer` mapped names, button objects, `StatCollector`→`I18n`.
- **Renderers** (`RenderVillagerCustom`, `RenderFishHookCustom`) + `ClientProxy` registration via
  `RenderingRegistry.registerEntityRenderingHandler` + `IRenderFactory` (in client preInit / `ModelRegistryEvent`).
  Note: villager render uses vanilla `ModelBiped` + `RenderBiped<T>` (now generic) — no custom models.
- **Command** `VillagerMessagesCommand`: rewrite to `CommandBase` (`getName/getUsage/execute(MinecraftServer,
  ICommandSender,String[]) throws CommandException`), register in `serverStart`.
- **Fish hook (RISKIEST, do last):** `EntityFishHookCustom` + `RenderFishHookCustom` + `FishHookPacket`.
  1.12.2 rewrote `EntityFishHook` entirely — likely implement a custom hook from scratch. The reference
  even (oddly) registered vanilla `EntityFishHook.class` as a mod entity (id 100) — that won't translate.
- **Lang:** add `assets/helpfulvillagers/lang/en_us.lang` (lowercase!) for entity names. Original shipped no
  lang; most text is hardcoded English.

---

## 6. ⭐ SRG → 1.12.2 name resolution (use this, don't guess)

The reference sources use SRG names (`func_*`, `field_*`) and the entities contain big arrays of obfuscated
block/item IDs. **Resolve them deterministically** from the MCP mapping:

```
fields.csv  : C:\Users\spege\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_stable\39\fields.csv
```
Format: `srg_name,readable_name,side,comment`. The readable names returned match 1.12.2 `Blocks`/`Items`
constants (e.g. `field_150400_ck → ACACIA_STAIRS`, `field_151056_x → DIAMOND_AXE`).

Use Grep/Bash to look up many at once, e.g.:
```
for f in field_150344_f field_151055_y ...; do grep "^$f," ".../fields.csv"; done
```
A handful of IDs aren't in that csv (defined on a superclass) — fill those from knowledge, e.g.:
`field_150422_aJ → OAK_FENCE`, `field_150396_be → OAK_FENCE_GATE`, `field_151135_aq → OAK_DOOR`.
If a constant name turns out wrong, the CORE build will flag it — fix in the fix-loop.

There is **no methods.csv handy** for SRG method names, but the established cheat-sheet (§7) covers what this
mod uses; for anything new, infer from vanilla knowledge and verify at compile time.

---

## 7. Established SRG→1.12.2 cheat-sheet (already applied — stay consistent)

**Entity / world:** `field_70170_p`→`world`, `field_70165_t/u/v`→`posX/Y/Z`, `field_70177_z/70125_A`→
`rotationYaw/rotationPitch`, `field_70173_aa`→`ticksExisted`, `field_70128_L`→`isDead`,
`field_70138_W`→`stepHeight`, `field_70733_aJ`→`swingProgress`, `field_72995_K`→`isRemote`.
`func_70071_h_`→`onUpdate`, `func_70645_a`→`onDeath`, `func_70097_a`→`attackEntityFrom`,
`func_70014_b`→`writeEntityToNBT`, `func_70037_a`→`readEntityFromNBT`, `func_145782_y`→`getEntityId`,
`func_70106_y`→`setDead`, `func_72838_d`→`world.spawnEntity`, `func_73045_a`→`world.getEntityByID`,
`func_70080_a`→`setLocationAndAngles`, `func_70631_g_`→`isChild`, `func_70089_S`→`isEntityAlive`,
`func_70661_as`→`getNavigator`, `func_70681_au`→`getRNG`, `func_70691_i`→`heal`,
`func_72935_r`→`world.isDaytime`, `func_72825_h(x,z)`→`world.getHeight(x,z)`,
`func_72907_a(class)`→`world.countEntities(class)`, `func_72872_a`→`world.getEntitiesWithinAABB`.

**Entity-villager:** `func_70946_n`→`getProfession`, `func_70932_a_`→`setCustomer`, `func_70931_l_`→`getCustomer`,
`func_70934_b`→`getRecipes`. **Interaction**: 1.7.10 `func_70085_c(player)` → 1.12.2
`processInteract(EntityPlayer, EnumHand)` (guard `hand == MAIN_HAND`). **Swing**: `func_71038_i()` →
`swingArm(EnumHand)`. **Equipment**: `func_70062_b(slot,stack)` → `setItemStackToSlot(EntityEquipmentSlot,..)`
(0/1/2/3/4 → held + FEET/LEGS/CHEST/HEAD). **Home**: `func_110171_b(x,y,z,r)` → `setHomePosAndDistance(BlockPos,r)`.
**Navigator**: `func_75492_a`→`tryMoveToXYZ`, `func_75497_a`→`tryMoveToEntityLiving`; `PathNavigateGround`
`func_75491_a`→`setBreakDoors`, `func_75498_b`→`setEnterDoors` (best-effort; 1.7.10 setAvoidsWater has no
direct equivalent — flagged).

**Coords:** `ChunkCoordinates` → **`BlockPos`** (immutable! reassign, use `MutableBlockPos` only if truly needed).
`field_71574_a/71572_b/71573_c` → `getX()/getY()/getZ()`. `func_82371_e/func_71569_e` distance → `distanceSq`.
`Vec3`/`Vec3.func_72443_a` → `Vec3d`/`new Vec3d(x,y,z)`; `field_72450_a/b/c` → `.x/.y/.z`.
`RandomPositionGenerator.func_75464_a` → `findRandomTargetBlockTowards` (returns `Vec3d`).

**Blocks/AABB:** `world.func_147439_a(x,y,z)` → `world.getBlockState(pos).getBlock()`,
`func_147438_o`→`getTileEntity(pos)`, `func_147437_c`→`isAirBlock(pos)`, `func_72937_j`→`canSeeSky(pos)`,
`func_147465_d(x,y,z,block,meta,flag)` → `setBlockState(pos, block.getStateFromMeta(meta), flag)`,
`func_147468_f`→`setBlockToAir(pos)`. `AxisAlignedBB.func_72330_a(...)` → `new AxisAlignedBB(...)` (immutable;
`func_72324_b` setBounds is gone → reassign). AABB fields `field_72340_a..field_72334_f` →
`.minX/.minY/.minZ/.maxX/.maxY/.maxZ`. `func_72326_a`→`intersects`. `World.isSideSolid(x,y,z,ForgeDirection)`
→ `isSideSolid(BlockPos, EnumFacing)`. `Block.func_149634_a(item)`→`Block.getBlockFromItem`,
`Item.func_150898_a(block)`→`Item.getItemFromBlock`. Block solidity `func_149721_r` →
`state.isOpaqueCube()`.

**ItemStack:** `field_77994_a`→`getCount()/setCount()`, `func_77946_l`→`copy`, `func_82833_r`→`getDisplayName`,
`func_77973_b`→`getItem`, `func_77960_j`→`getMetadata`/`getItemDamage`, `func_77964_b`→`setItemDamage`,
`func_77958_k`→`getMaxDamage`, `func_77976_d`→`getMaxStackSize`, `func_77981_g`→`getHasSubtypes`,
`func_77979_a`→`splitStack`, `func_77955_b(nbt)`→`writeToNBT(nbt)`, `ItemStack.func_77949_a(nbt)`→
`new ItemStack(nbt)`, `ItemStack.func_77989_b`→`areItemStacksEqual`. `func_92059_d` (EntityItem)→`getItem`.

**Inventory/Container:** `func_70302_i_`→`getSizeInventory`, `func_70301_a`→`getStackInSlot`,
`func_70299_a`→`setInventorySlotContents`, `func_70298_a`→`decrStackSize`, `func_70304_b`→`removeStackFromSlot`,
`func_94041_b`→`isItemValidForSlot`, `func_70300_a`→`isUsableByPlayer`, `func_145825_b`→`getName`,
`func_70295_k_`/`func_70305_f`→`open/closeInventory(EntityPlayer)`. IInventory in 1.12.2 ALSO requires
`isEmpty()/getField/setField/getFieldCount/clear()/getDisplayName()`. Container: `func_75146_a`→
`addSlotToContainer`, `func_82846_b`→`transferStackInSlot` (return `ItemStack.EMPTY`), `func_75145_c`→
`canInteractWith`, `func_75134_a`→`onContainerClosed`, `field_75151_b`→`inventorySlots`,
`slot.func_75216_d/func_75211_c/func_75215_d/func_75218_e`→`getHasStack/getStack/putStack/onSlotChanged`,
`func_75135_a`→`mergeItemStack`. `ItemArmor.field_77881_a` (int) → `armorType` (`EntityEquipmentSlot`).

**NBT:** `func_74782_a`→`setTag`, `func_74778_a`→`setString`, `func_74768_a`→`setInteger`,
`func_74774_a`→`setByte`, `func_74757_a`→`setBoolean`, `func_74780_a`→`setDouble`, `func_74783_a`→`setIntArray`,
`func_74742_a`→`appendTag`; reads `func_74762_e`→`getInteger`, `func_74779_i`→`getString`,
`func_74767_n`→`getBoolean`, `func_74771_c`→`getByte`, `func_74769_h`→`getDouble`, `func_74759_k`→`getIntArray`,
`func_74764_b`→`hasKey`, `func_74775_l`→`getCompoundTag`, `func_74781_a`→`getTag`,
`func_150295_c("k",10)`→`getTagList("k",10)`, `func_74745_c`→`tagCount`, `func_150305_b`→`getCompoundTagAt`.

**Recipes:** `CraftingManager.func_77594_a().func_77592_b()` → iterate `CraftingManager.REGISTRY`
(`IForgeRegistry<IRecipe>`). `FurnaceRecipes.func_77602_a().func_77599_b()` →
`FurnaceRecipes.instance().getSmeltingList()`. `IRecipe.func_77571_b`→`getRecipeOutput`. Per-recipe-class
parsing → `recipe.getIngredients()` (`NonNullList<Ingredient>`, `Ingredient.getMatchingStacks()`).

**WorldSavedData:** `world.perWorldStorage`→`world.getPerWorldStorage()`, `storage.func_75742_a(C,k)`→
`getOrLoadData(C,k)`, `func_75745_a`→`setData`, `func_76185_a`→`markDirty`. **`writeToNBT` now RETURNS
`NBTTagCompound`** (was void). Constructor `(String name)` required (reflection-instantiated).

**Text:** `ChatComponentText`→`TextComponentString`, `IChatComponent`→`ITextComponent`,
`player.func_145747_a(...)`→`player.sendMessage(...)`. `EntityPlayer.getDisplayName()` now returns
`ITextComponent` → use `getName()` (String) for account keys etc. `StatCollector`→`I18n`.

**Network:** package `cpw.mods.fml.*` → `net.minecraftforge.fml.*`. `ctx.getServerHandler().field_147369_b`
→ `ctx.getServerHandler().player`. Channel API (`NetworkRegistry.INSTANCE.newSimpleChannel`, `registerMessage`,
`sendToAll/sendTo/sendToServer`) is the same.

---

## 8. Flagged risks to verify in-game (tell the user)

1. **Door orientation / village door detection** (`HelpfulVillage.getDoorFromCoords`) — translated from
   1.7.10 metadata logic to `BlockDoor.FACING`; best-effort, verify villagers path through doors.
2. **GuildHall interior flood-fill** — the 1.7.10 original had copy/paste neighbour bugs; preserved verbatim
   (commented). Verify hall detection / chest-finding works.
3. **VillageEconomy price calc is now synchronous** (was a background thread doing off-main-thread world
   reads — unsafe). One-time per village; could hitch on huge villages (TODO: chunk it if needed).
4. **InventoryVillager.addItem** preserves a suspicious 1.7.10 condition that only drops overflow items on
   the client side (items silently lost server-side when full) — flagged, left verbatim. Consider fixing.
5. **Vanilla villager replacement** on `EntityJoinWorldEvent` (in `CommonHooks`, not yet ported) is intrusive
   — verify no conflicts with vanilla trading / other mods in 1.12.2.
6. **Packet thread-safety** — when porting packets, DO add `addScheduledTask` (the originals don't).

---

## 9. Workflow expectations

- Use a task list (TaskCreate) and keep `notes/claude.md` §6 progress table current after each sub-step.
- Small commits; WIP allowed during CORE (`WIP CORE/...: ... (does not compile yet)`), green commits after.
- Don't spawn subagents unless the user asks. Don't run the game. Web search OK for Forge 1.12.2/1.7.10 API.
- Report to the user every few steps and stop at genuine design decisions or blockers.

---

## 10. Forward-looking note (post-port expansion — keep in mind, don't build yet)

After the port reaches parity, the user plans to **expand** the mod: add **new professions**, **deepen the
economy**, and likely more. So while porting:
- Keep profession logic uniform — the 9 subclasses share a clear shape (set `profession`/`profName`/tools,
  override `getValidCoords`/`isValidTool`/`canCraft`, add a profession AI). A future 10th profession should
  drop in the same way. Don't entrench assumptions that there are exactly 9 (e.g. the `profession`
  int-switches in `AbstractVillager.changeProfession` / `resetRecipes`, the `unlockedHalls[13]` size, the
  GuildHall `typeNum` 1–13 range). Note these switch points; a clean enum/registry refactor may be wanted
  later (flag it, don't do it unprompted).
- Economy (`VillageEconomy`) currently derives prices from block scans + recipe graphs. The user wants to
  extend it — keep `ItemPrice`/recipe-graph logic readable and avoid baking in magic constants where a field
  would do. But again: **finish the faithful port first**, expansions are a separate phase.
