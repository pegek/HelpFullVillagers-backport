# Agent Handoff — Helpful Villagers backport, PHASE 2 (GUI + Fish Hook)

> Paste this (or point the agent here) to continue. Conversations with the user are in **Polish**;
> code/comments/commits in **English**. Read `notes/CLAUDE.md` first (full progress + decisions).

---

## 0. Where we are

The **forward-port of "Helpful Villagers" (WeaselNinja, MIT) from Forge 1.7.10 → 1.12.2** is
**functionally complete and verified in-game**. As of 2026-06-03:

- ✅ Full build green: `.\gradlew.bat build --console=plain` → `BUILD SUCCESSFUL` (+ reobfJar).
- ✅ **Smoke-test PASSED in 1.12.2:** vanilla villagers are replaced by mod villagers, profession models
  render (biped + per-profession texture), right-click opens the interaction GUI, `/summon helpfulvillagers:*`
  works for all 10 entities, `/villagermessages` works, no crashes, logs clean.
- The whole CORE (entities, AI, village, economy, crafting, inventory), all 27 network packets,
  CommonHooks/ClientHooks, command, renderers, and lang are **done**.

**Phase 2 = the two pieces that were left as compilable stubs:**
1. **Full 8-screen GUI** (currently empty stubs that open but draw nothing).
2. **Full custom fish hook entity + renderer** (currently a no-op stub).

Plus optionally: address the in-game verification flags in `notes/CLAUDE.md` §"Otwarte pytania / ryzyka".

---

## 1. Where everything is

| Thing | Path |
|---|---|
| Repo root (1.12.2 target) | `E:\Isuth\HelpVillBackport\` |
| New source (what you edit) | `E:\Isuth\HelpVillBackport\src\main\java\com\spege\helpfulvillagers\` |
| **READ-ONLY** 1.7.10 reference (decompiled) | `E:\Isuth\HelpVillBackport\helpfulvillagers\decompiled_src\mods\helpfulvillagers\` |
| Original jar (assets, mcmod.info) | `E:\Isuth\HelpVillBackport\helpfulvillagers-1.7.10-1.3.1.jar` |
| Living dev log (UPDATE IT) | `E:\Isuth\HelpVillBackport\notes\CLAUDE.md` |
| Family-wide rules (MUST follow) | `E:\Isuth\modDev\notes\agent_notes.md` |
| **MCP SRG→name mapping** | `C:\Users\spege\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_stable\39\fields.csv` |
| **Game logs (after user runs)** | `C:\Users\spege\curseforge\minecraft\Instances\HelpfullVillTest\logs\latest.log` |
| Log filter script | `E:\Isuth\HelpVillBackport\notes\logs\filter_log.py` |

**Read first:** `notes/CLAUDE.md` (§6 progress table + all the SRG→1.12.2 decisions), then skim the already-ported
`gui/` stubs and `main/GuiHandler.java` to see the wiring you'll be filling in.

---

## 2. Hard rules (non-negotiable — unchanged)

- **Forge 1.12.2 ONLY.** `setUnlocalizedName(...)` NOT `setTranslationKey(...)`. No `var`, no `record`. Java 8.
- **Never modify** the `helpfulvillagers/` reference tree or the original jar — read-only.
- **ItemStack:** empty = `ItemStack.EMPTY`, never `null`. `isEmpty()/getCount()/setCount()/shrink()/grow()/copy()/getDisplayName()`.
- **Multiplayer / threading:** mutate world/entities server-side. Packet handlers already use `addScheduledTask` —
  keep that pattern for any new packets. GUI buttons send packets (client→server) that already exist.
- `@SuppressWarnings("null")` / `"deprecation"` on GUIs/entities where Forge `@Nonnull` lint or deprecated
  vanilla calls bite.
- modid `helpfulvillagers`, package `com.spege.helpfulvillagers.*`, lowercase resources.
- Comments in English. Commit trailer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`.
- Keep WeaselNinja attribution.

---

## 3. Build & verification

- **Requires JDK 8** (pinned in `gradle.properties` → `org.gradle.java.home`). Gradle 4.9 / ForgeGradle 3.
- Build: `.\gradlew.bat build --console=plain` (PowerShell, from repo root). ~5–16 s incremental.
  Exit 0 = green (ignore the JVM "restricted method" WARNINGs).
- **Agent does NOT run the game.** The **user** runs it and pastes the log. Then:
  `python notes\logs\filter_log.py <path>\latest.log` — extracts only `[HV]` lines, errors, exceptions, warnings.
- Add `[HV]`-prefixed `HelpfulVillagers.logger.info(...)` debug where useful — it makes the filter script
  immediately useful and is the primary feedback loop (you can't see the game).

---

## 4. ⭐ TASK A — Full 8-screen GUI (etap 9)

Currently `src/main/java/com/spege/helpfulvillagers/gui/*.java` are **compilable stubs** (constructors + empty
`drawGuiContainerBackgroundLayer`). `main/GuiHandler.java` already routes GUI ids 0–7 to them and is wired
(`NetworkRegistry.INSTANCE.registerGuiHandler` in `HelpfulVillagers.init`). Right-click already calls
`player.openGui(instance, 0, world, entityId, 0, 0)` (see `AbstractVillager.processInteract`). **`posX` carries
the villager entity id** by convention.

Port each screen from the reference (READ-ONLY): `helpfulvillagers/decompiled_src/.../gui/`. Sizes & ids:

| id | Class | ref lines | base | textures (already in jar) |
|----|-------|-----------|------|---------------------------|
| 0 | GuiVillagerDialog | 83 | GuiScreen | `gui/dialog_background.png` |
| 1 | GuiProfessionDialog | 91 | GuiScreen | `gui/dialog_background.png` |
| 2 | GuiVillagerInventory | 81 | GuiContainer (uses `ContainerInventoryVillager`, already ported) | generic |
| 3 | GuiNickname | 82 | GuiScreen | `gui/dialog_background.png` |
| 4 | GuiCraftingMenu | 436 | GuiContainer | `gui/craft_menu.png` |
| 5 | GuiCraftStats | 310 | GuiContainer | `gui/craft_details.png` |
| 6 | GuiTeachRecipe | 304 | GuiContainer (+ inner `VillagerContainerWorkbench extends ContainerWorkbench`, already stubbed) | vanilla crafting |
| 7 | GuiBarter | 899 | GuiContainer | `gui/villager_trade.png`, `gui/barter_inventory/*` |

Total ~2.3k lines. Buttons send the **server packets that already exist**: `GUICommandPacket` (id→command),
`ProfessionChangePacket`, `NicknamePacket`, `AddRecipePacket`, `ResetRecipesPacket`, `CraftItemServerPacket`,
`CraftQueueServerPacket`, `PlayerInventoryPacket`, `PlayerItemStackPacket`, `PlayerCraftMatrixResetPacket`,
`PlayerAccountServerPacket`, `ItemPriceServerPacket`. The client display state arrives via the client packets
(already ported), so the GUI mostly **reads** entity/village fields and **sends** on button press.

### 1.7.10 → 1.12.2 GUI API deltas (the cheat-sheet you'll need)
- `GuiScreen`: `func_73863_a(int,int,float)` → `drawScreen(int mouseX,int mouseY,float partialTicks)`;
  `func_73866_w_` → `initGui`; `func_146284_a(GuiButton)` → `actionPerformed(GuiButton) throws IOException`;
  `field_146292_n` (button list) → `this.buttonList`; `func_73732_a`/`func_73731_b` (font draw) →
  `this.fontRenderer.drawString(...)` / `this.drawCenteredString(fontRenderer,...)`;
  `field_146289_q` (fontRenderer) → `this.fontRenderer`. `func_146276_q_` → `drawDefaultBackground`.
- `GuiContainer`: `func_146976_a(float,int,int)` → `drawGuiContainerBackgroundLayer(float,int,int)`;
  `func_146979_b(int,int)` → `drawGuiContainerForegroundLayer(int,int)`;
  `field_146297_k.func_110434_K().func_110577_a(rl)` → `this.mc.getTextureManager().bindTexture(rl)`;
  `func_73729_b` → `drawTexturedModalRect`. Background size fields `field_146999_f/147000_g` → `xSize/ySize`,
  `field_147003_i/147009_r` → `guiLeft/guiTop`.
- `GuiButton`: ctor `(id,x,y,w,h,text)` same; `field_146126_j` → `displayString`; `field_146124_l` → `enabled`;
  `field_146125_m` → `visible`; `func_146116_c(mc,x,y)` → `mousePressed(mc,x,y)`. Custom button subclasses in
  GuiCraftingMenu/GuiCraftStats override `func_146112_a` → `drawButton(Minecraft, int, int, float)`.
- `GuiTextField` (Nickname): ctor now `(componentId, fontRenderer, x, y, w, h)`; `func_146180_a` → `setText`,
  `func_146179_b` → `getText`, `func_146195_b` → `setFocused`, `func_146192_a` → `mouseClicked`,
  `func_146201_a(char,int)` → `textboxKeyTyped(char,int)`, `func_146194_f` → `drawTextBox`,
  `func_146178_a` → `updateCursorCounter`.
- `StatCollector.func_74838_a` → `I18n.format(...)` (`net.minecraft.client.resources.I18n` for client GUI).
- `Container`: see the already-ported `inventory/ContainerInventoryVillager.java` for the exact 1.12.2 shape
  (`transferStackInSlot` returns `ItemStack.EMPTY`, `canInteractWith`, slot APIs). Reuse that as the template.
- Mouse/keys: `mouseClicked(int,int,int) throws IOException`, `keyTyped(char,int) throws IOException`,
  `handleMouseInput()` for scroll (GuiBarter has a scrolling item list — use `Mouse.getEventDWheel()`).
- `drawHoveringText` / `renderToolTip(ItemStack, x, y)` for tooltips.

### Suggested order (cheap → expensive, build after each)
1. GuiNickname (3) + GuiVillagerInventory (2) — smallest, exercise GuiTextField + Container.
2. GuiVillagerDialog (0) + GuiProfessionDialog (1) — button grids that send GUICommand/ProfessionChange.
3. GuiCraftStats (5) → GuiCraftingMenu (4) → GuiTeachRecipe (6).
4. GuiBarter (7) — biggest (899), scrolling list + economy; do last.

After each: `.\gradlew.bat build`, commit (`feat(gui): port GuiX`), ask the user to smoke-test that one screen.

---

## 5. ⭐ TASK B — Full fish hook (etap 10, riskiest)

Files: `entity/EntityFishHookCustom.java` (currently a 40-line stub — ctors + Entity contract only),
`renderer/RenderFishHookCustom.java` (no-op stub). Reference (READ-ONLY): same names under
`helpfulvillagers/decompiled_src/.../entity/` (475 lines) and `.../renderer/` (112 lines).

**Why it was deferred:** 1.12.2 rewrote vanilla `EntityFishHook` entirely; the 1.7.10 reference extends `Entity`
and reimplements bobber motion/water detection/catch by hand. Port that hand-rolled logic rather than fighting
vanilla's hook. Key migration points you'll hit:
- `EntityFishHook` references → none; it's a standalone `Entity` (good — already the case in the stub).
- ChunkCoordinates→BlockPos; `world.func_147439_a`→`getBlockState(pos).getBlock()`; motion fields
  `field_70159_w/70181_x/70179_y` → `motionX/motionY/motionZ`; `func_70091_d` → `move(MoverType.SELF, x,y,z)`.
- `setSize`, `entityInit` (DataManager if you sync anything), `writeEntityToNBT/readEntityFromNBT`.
- The fisherman already creates/removes it: `EntityFisherman.fishEntity`, `EntityAIFisherman.fish()`,
  and `FishHookPacket` (spawn/remove). Those call `new EntityFishHookCustom(world, x, y, z, fisherman)` and
  `fishEntity.setDead()` — keep that constructor/contract.
- Renderer: 1.7.10 used a line + bobber quad. `RenderFishHookCustom extends Render<EntityFishHookCustom>`,
  `doRender(...)` with `Tessellator`/`BufferBuilder` (`bufferBuilder.begin(...)`, `pos().tex().endVertex()`).
  `RenderManager` passed via ctor (already stubbed). Register stays in `ClientProxy.preInit` (already there).

This is the highest-uncertainty piece — **flag anything unclear to the user and verify in-game** (the fisherman
casting over water, the bobber appearing, fish/squid harvest). Note: the fisherman's gather AI is already ported,
so once the hook entity behaves, the loop should close.

---

## 6. Critical lessons from phase 1 (don't repeat these)

- **Entity renderers MUST be registered in `ClientProxy.preInit`, not `init`** — else the RenderManager has
  already cached its map and entities fall back to the vanilla renderer (this exact bug cost a test cycle).
- **Don't send sync packets every tick.** `syncVillage` was throttled to every 20 ticks; a per-tick
  `sendToAll` flooded the client ("Can't keep up"). Any new per-villager packet you add → throttle it.
- **Guard null `actualBounds`** on freshly created villages (set lazily in `updateVillageBox`).
- The `[HV]` debug + `filter_log.py` loop is how you see the game — use it.

---

## 7. Forward-looking (post-port expansion — the user's real goal)

After GUI + fish hook, the port reaches **full parity**. The user then wants to **expand**: new professions,
deeper economy, more. While finishing phase 2, keep the architecture clean for that:
- The 9 professions share a clear shape (`profession` int, `profName`, tools, `getValidCoords`/`isValidTool`/
  `canCraft`, a profession AI). A 10th should drop in the same way. Watch the hard-coded switch points flagged
  in the original handoff: `AbstractVillager.changeProfession`/`resetRecipes` int-switches, `unlockedHalls[13]`,
  GuildHall `typeNum` 1–13. A clean enum/registry refactor may be wanted later — **flag it, don't do it
  unprompted during the port.**
- Economy (`VillageEconomy`/`ItemPrice`) derives prices from block scans + recipe graphs; keep it readable,
  avoid baking magic constants where a field would do.
- **Finish faithful parity first; expansions are a separate phase.**

---

## 8. Workflow

- Use a task list. Update `notes/CLAUDE.md` §6 after each screen / sub-step.
- Small commits; build green before each commit. Don't run the game; don't spawn subagents unless asked.
- Report to the user every few screens and stop at genuine design decisions or blockers.
- Web search OK for 1.12.2 GuiScreen/GuiContainer/Tessellator API.
