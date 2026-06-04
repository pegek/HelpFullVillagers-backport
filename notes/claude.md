# helpfulvillagers (backport) — Dev Notes for Agents

Żywa notatka dla siebie / przyszłych agentów Claude pracujących nad tym repo.
**Aktualizuj na bieżąco** po każdym ukończonym etapie (sekcja 6 — Progress).

Rozmowy z user po polsku; kod i commit messages po angielsku.

---

## 1. Czym jest ten projekt

Forward-port moda **Helpful Villagers** z Minecraft Forge **1.7.10 → 1.12.2**.

- **Oryginalny autor:** WeaselNinja (zachowujemy atrybucję).
- **Licencja:** MIT. Port robiony za zgodą autora.
- **Autor portu (tymczasowo):** Isuth.
- **Oryginalna wersja:** 1.3.1 (1.7.10).
- **Standalone** — NIE jest członkiem rodziny `insanetweaks`. Brak zależności od `itcore`.
  (Może zostać podłączony w przyszłości — wtedy migracja do konwencji rodziny.)
- **Greenfield:** brak wymogu save/NBT/config compat z oryginałem. Wewnętrzne ID/NBT/config można modernizować.

Pełny plan migracji (kontekst, API delta table, kolejność, ryzyka):
`C:\Users\spege\.claude\plans\backport-helpfulvillagers.md` — **źródło prawdy dla strategii**.

---

## 2. Co robi mod (krótko)

Podmienia vanilla villagerów na 9 profesji z własnym AI: Regular, Lumberjack, Miner, Farmer, Soldier,
Archer, Merchant, Fisherman, Rancher. Villagerzy zbierają surowce, walczą, łowią ryby, hodują, craftują i handlują.
Dochodzi system wiosek (`HelpfulVillage` + `HelpfulVillageCollection` jako `WorldSavedData`), ekonomia
(`VillageEconomy`, `ItemPrice`), crafting villagerów (`VillagerRecipe`, `CraftTree`, `CraftQueue`),
8 GUI interakcji gracz↔villager, customowy fish hook, 27 packetów sieciowych, config Forge, komenda `/villagermessages`.

**Brak bloków, itemów, tile entities** — mod operuje na vanilla itemach + własnych entity.

---

## 3. Struktura repo

```
E:\Isuth\HelpVillBackport\
├── helpfulvillagers/                      [READ-ONLY referencja 1.7.10 — NIE modyfikować]
│   ├── decompiled_src/mods/helpfulvillagers/...   (źródła zdekompilowane CFR, nazwy SRG func_*/field_*)
│   ├── unzipped_classes/                   (.class)
│   ├── classes.jar, extract_classes.py, summary.txt
├── helpfulvillagers-1.7.10-1.3.1.jar      [READ-ONLY oryginalny jar — zawiera assets/textures + mcmod.info]
├── notes/
│   └── claude.md                           [ten plik]
└── src/main/                               [NOWY kod 1.12.2 — TWORZONY]
    ├── java/com/spege/helpfulvillagers/
    │   ├── main/        (HelpfulVillagers @Mod, CommonProxy, ClientProxy, GuiHandler, CommonHooks, ClientHooks)
    │   ├── entity/      (AbstractVillager + 9 profesji + EntityFishHookCustom)
    │   ├── ai/          (11 klas EntityAI*)
    │   ├── renderer/    (RenderVillagerCustom, RenderFishHookCustom)
    │   ├── network/     (27 packetów IMessage/IMessageHandler)
    │   ├── gui/         (8 GuiScreen/Container)
    │   ├── inventory/   (InventoryVillager, ContainerInventoryVillager)
    │   ├── crafting/    (CraftItem, CraftQueue, CraftTree, VillagerRecipe)
    │   ├── econ/        (VillageEconomy, ItemPrice)
    │   ├── village/     (HelpfulVillage, HelpfulVillageCollection, GuildHall, RanchGuildHall)
    │   ├── command/     (VillagerMessagesCommand)
    │   ├── enums/       (EnumActivity, EnumMessage)
    │   └── util/        (AIHelper, ResourceCluster)
    └── resources/
        ├── mcmod.info
        └── assets/helpfulvillagers/
            ├── textures/entity/villager/*.png   (skopiowane 1:1 z oryginalnego jara)
            ├── textures/gui/**                  (jw.)
            └── lang/en_us.lang                  (NOWY — nazwy entity; oryginał nie miał .lang)
```

> **Mapowanie pakietów:** oryginał `mods.helpfulvillagers.*` → port `com.spege.helpfulvillagers.*`.
> Podpakiety zachowane 1:1. modid bez zmian (`helpfulvillagers`), więc ścieżki assetów reużyte verbatim.

---

## 4. Konwencje (twarde — z agent_notes.md rodziny)

- **Forge 1.12.2 ONLY**, Java 8 (`-source 1.8 -target 1.8`). Żadnych 1.13+ APIs.
  - `setUnlocalizedName(...)` — NIGDY `setTranslationKey(...)`.
  - Brak `var`, brak `record`.
- **modid `helpfulvillagers`** — lowercase, bez underscore. Resource paths lowercase (JSON loader inaczej pada).
- **Java package:** `com.spege.helpfulvillagers.*`.
- `-proc:none` + `-Xlint:all` w `compilerArgs`. Na entity/event handlerach/GUI dodawaj `@SuppressWarnings("null")`
  (Forge `@Nonnull` strict checking jest agresywny).
- **ItemStack 1.12.2:** pusty stack = `ItemStack.EMPTY`, nigdy `null`. `getCount()`/`setCount()`/`shrink()`/`grow()`,
  `isEmpty()`. Sloty inventory NIGDY nie zawierają `null` — `piece == null` to martwy kod, używaj `isEmpty()`.
- **Multiplayer:** mutacje świata/entity/NBT przez stronę serwera. Packet handlery NIE mutują na wątku netty —
  owijaj w `ctx.getServerHandler().player.getServerWorld().addScheduledTask(...)` (server) /
  `Minecraft.getMinecraft().addScheduledTask(...)` (client).
- **Komentarze po angielsku.** Commit messages po angielsku z trailerem
  `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`.
- **Atrybucja:** `mcmod.info` `authorList` zawiera `WeaselNinja` (oryginał) + credit dla portu.
- **NIE modyfikować** `helpfulvillagers/` ani oryginalnego jara — to read-only referencja.

---

## 5. Build setup (ForgeGradle 3, wzór z itcore)

- `build.gradle` / `settings.gradle` / wrapper skopiowane i dostosowane z `E:\Isuth\submod\itcore`.
- Różnice względem itcore: **brak MixinBooter** (mod nie używa mixinów), **brak `libs/` z zewn. modami**
  (standalone — żadnych compile-time dep na inne mody).
- Forge `1.12.2-14.23.5.2860`, mappings `snapshot 20171003-1.12`.
- `group = com.spege.helpfulvillagers`, `archivesBaseName = helpfulvillagers`.
- `jar.finalizedBy('reobfJar')` — prod jar reobfowany.
- Build: `./gradlew build`. Smoke test (`runClient`) robi **user**, agent nie blokuje na grze.
- Po każdym ukończonym etapie: `./gradlew build` zielony → commit.

---

## 6. Progress (AKTUALIZUJ NA BIEŻĄCO)

Kolejność etapów (z planu, sekcja 4). Status: ⬜ TODO / 🔄 in progress / ✅ done.

| # | Etap | Status | Notatki |
|---|---|---|---|
| 0 | Skeleton + build.gradle + mcmod.info + @Mod stub + proxies + assets copy | ✅ | build zielony (1m6s), jar+reobf OK. Wymaga JDK 8 (gradle.properties → jdk1.8.0_361) |
| 1 | enums (EnumActivity, EnumMessage) | ✅ | czyste liście, sama zmiana pakietu |
| 1b | util (AIHelper, ResourceCluster) | ⬜ | **PRZESUNIĘTE** — nie są liśćmi; zależą od entity/village/inventory/crafting + ChunkCoordinates→BlockPos. Port razem z etapem 6/8 |
| 2 | Config (Forge Configuration w preInit) | ✅ | API identyczne z 1.7.10; pola w klasie głównej |
| **CORE** | **Klaster SCC (jeden build na końcu)** — patrz REWIZJA niżej | ✅ ZIELONY | **Build + reobfJar SUCCESSFUL.** Fix-loop: tylko 2 poprawki — `WorldSavedData` import (→`net.minecraft.world.storage`) i `getEntitiesWithinAABB(IMob.class)` (IMob to interfejs, nie Entity → query EntityLivingBase + filtr instanceof). + 14 core-packetów + EntityFishHookCustom stub + wiring klasy głównej |
| ↳ C1 | crafting/econ data (VillagerRecipe, CraftItem, ItemPrice, CraftTree, CraftQueue) | ✅(kod) | sportowane; build dopiero po całym CORE. getIngredients()+ItemStack.EMPTY |
| ↳ C2 | InventoryVillager + ContainerInventoryVillager | ✅(kod) | IInventory pełna migracja; ItemStack.EMPTY; ItemArmor.armorType→EntityEquipmentSlot. Zależy od InventoryPacket → packet wchodzi do CORE |
| ↳ C3 | village (HelpfulVillage, GuildHall, RanchGuildHall, HelpfulVillageCollection) | ✅(kod) | ChunkCoordinates→BlockPos; isSideSolid(EnumFacing); door API best-effort (VERIFY in-game); WorldSavedData.writeToNBT zwraca NBTTagCompound, getPerWorldStorage() |
| ↳ C4 | VillageEconomy | ✅(kod) | **Thread→synchroniczne** (off-thread world access niebezpieczny w 1.12.2); CFR rozwalił initPrices (zrekonstruowane); canSilkHarvest protected→pominięte; CraftingManager.REGISTRY; getName() dla kont; getDrops(NonNullList,...) |
| ↳ C5 | AbstractVillager (1246) + 9 profesji | ✅(kod) | wszystkie 9: Regular/Lumberjack już były; Miner/Farmer/Soldier/Archer/Merchant/Fisherman/Rancher dodane. SRG itemy/bloki z fields.csv. Rancher: osobne `getValidAnimals()` (ArrayList<EntityAnimal>) zamiast raw ArrayList; `getValidCoords()`→null. Fisherman: usunięto client-only `getItemIcon`/`IIcon` (brak w 1.12.2, render w rendererze). Miner: ChunkCoordinates→BlockPos w shaft/dig/tunnel/return, DamageSource.IN_WALL, Mineshaft NBT |
| ↳ C6 | util/AIHelper + ResourceCluster | ✅(kod) | ChunkCoordinates→BlockPos; chest/furnace transfer na ItemStack.EMPTY/getCount/setInventorySlotContents; breakBlock → getDrops(NonNullList, world, pos, IBlockState, 0)+setBlockToAir; Block.getBlockFromItem/getIdFromBlock; TileEntityFurnace.getItemBurnTime; merge/remove na shrink/grow. actualBounds.minX/maxX/minZ/maxZ |
| ↳ C7 | AI (11 klas EntityAIBase) | ✅(kod) | Worker baza + Lumberjack/Fisherman/Farmer/Rancher/Miner; FollowLeader/MoveIndoors/VillagerMate (EntityAIBase); GuardSoldier/GuardArcher (EntityAITarget). SRG metody EntityAIBase (shouldExecute/shouldContinueExecuting/updateTask/startExecuting/resetTask/setMutexBits). getDestroySpeed(stack,IBlockState); world.isSideSolid(pos,EnumFacing.UP); EntityArrow abstr.→EntityTippedArrow+shoot(); ItemArmor.armorType=EntityEquipmentSlot; Vec3d; ChunkCoordinates→BlockPos (Miner mutacje→nowe BlockPos). Flagi: getHealth<getHealth/2 dead branch, BlockStem→skan sąsiadów melon/pumpkin, Miner findMine target/miner.target rozjazd, infiniteArrows decrement |
| 3 | Network layer (27 packetów, rename + main-thread scheduling) | ✅ | cpw.mods.fml→net.minecraftforge.fml; addScheduledTask wszędzie; player.mcServer/player.world; ItemStack.EMPTY guards |
| 8 | CommonHooks + ClientHooks + proxy wiring | ✅ | EntityJoinWorldEvent→getWorld(), AxisAlignedBB.intersects; event.world prywatne→getWorld(); preInit rejestruje EVENT_BUS |
| 9 | GUI (8 ekranów + GuiHandler) — pełne | ✅ | Pełne wersje 8 ekranów GUI; migracja 1.7.10 API na 1.12.2. |
| 10 | Fish hook (EntityFishHookCustom + render + packet) | ✅ | User sprawdził in-game, działa. |
| 11 | Renderers + ClientProxy registration (IRenderFactory) | ✅ TESTED | RenderVillagerCustom extends RenderBiped<AbstractVillager>(renderManager,ModelBiped,0.5f); getEntityTexture per profession; 10 registerEntityRenderingHandler. **KRYTYCZNE: rejestracja w ClientProxy.preInit (NIE init — za późno, RenderManager już zbuforował mapę → fallback do vanilla RenderVillager).** RenderFishHookCustom stub |
| 12 | Command (CommandBase rewrite) | ✅ | getName/getUsage/execute(MinecraftServer,ICommandSender,String[])/checkPermission/getTabCompletions; TextComponentString+TextFormatting |
| 13 | Lang + final assets | ✅ | en_us.lang entity names (entity.helpfulvillagers.*) |
| 14 | Full build + reobf jar → user smoke test | ✅ ZDANY | **Mod działa w grze 1.12.2.** Villagerzy podmieniani, modele profesji renderują się, PPM otwiera GUI (stuby), `/summon helpfulvillagers:*` OK, `/villagermessages` OK, brak crashy. Logi czyste. |

### Smoke-test fix-loop (po pierwszym uruchomieniu w grze)
- **Packet flood** „Can't keep up 2457ms" — `syncVillage()` wysyłał VillageSyncPacket co tick (20×/s × villager). Throttle: syncVillage co 20t, getNewHomeVillage co 40t. + null-guard homeVillage.
- **Modele zostawały vanilla** (też `/summon`'owane) — renderery były w `init()` (za późno). → przeniesione do `ClientProxy.preInit()`.
- **NPE co tick `CommonHooks:122`** — `currentVillage.actualBounds.intersects(...)`; świeża wioska ma null bounds zanim updateVillageBox się wykona (merge-loop biegł pierwszy). → guard na null bounds.
- **Debug `[HV]`** dodany w init/onUpdate(tick1)/processInteract/entityJoinWorld/worldLoad/registerRenderers. Skrypt `notes/logs/filter_log.py` filtruje logi (`python filter_log.py latest.log`).
- Logi gry: `C:\Users\spege\curseforge\minecraft\Instances\HelpfullVillTest\logs\latest.log`

### ⬜ POZOSTAŁO (Post-port parity)
- **Opcjonalnie**: spawn eggs dla profesji.
- **Weryfikacja "flag in-game"**: drzwi/pathfinding, flood-fill hal, addItem overflow client-only, ekonomia synchroniczna (hitch na dużych wioskach), getHealth<getHealth/2 dead branch w strażnikach, BlockStem→skan sąsiadów, Miner findMine target rozjazd, infiniteArrows decrement.
- **Ekspansja (faza 3)**: nowe profesje, rozbudowana ekonomia, refactor struktury pod dodawanie modyfikacji.

### Decyzje podjęte
- modid `helpfulvillagers`, package `com.spege.helpfulvillagers` (reużycie assetów).
- Greenfield: brak compat z save 1.7.10.
- Standalone, bez itcore, bez mixinów.
- Spawn eggs dla 9 profesji — opcjonalne (do decyzji na etapie 13).
- Fish hook: własny `EntityFishHookCustom` od zera; serwer liczy cykl połowu i loot, klient renderuje bobber/linkę przez normalny Forge entity tracking.
- **`ChunkCoordinates` (1.7.10) → `BlockPos` (1.12.2)** w całym kodzie. Uwaga: `BlockPos` jest **immutable**
  (brak setterów x/y/z jak w `ChunkCoordinates`). Tam gdzie oryginał mutował współrzędne — użyć `BlockPos.MutableBlockPos`
  lub tworzyć nowe `BlockPos`. Dotyczy AIHelper, ResourceCluster, village, AI.
- **Re-sekwencjonowanie:** `util/AIHelper` + `util/ResourceCluster` NIE są klasami-liśćmi (zależą od entity/village/
  inventory/crafting). Portowane dopiero gdy te typy istnieją (ok. etap 6–8), nie w etapie 1.

### REWIZJA KOLEJNOŚCI (po analizie zależności — 2026-06-02)
Rdzeń moda to **silnie spójny komponent (SCC)** — `AbstractVillager`, `InventoryVillager`, `crafting/*`,
`econ/*`, `village/*`, `util/AIHelper` zależą od siebie wzajemnie (cykle). **Nie da się portować ich
pojedynczo z zielonym buildem między klasami.** Skutki:
- Etapy 3 (network), 4 (entities), 5 (AI), 6 (inventory), 7 (crafting/econ), 8 (village), 1b (util)
  z planu zwijają się w **jeden duży klaster "CORE"**, portowany jako jednostka. Build zielony dopiero
  po całym klastrze; commit dopiero wtedy.
- **Network, GUI, Render, Command, FishHook** siedzą NA rdzeniu → idą PO nim (packety odwołują się do
  `AbstractVillager` itd., więc nie skompilują się wcześniej).
- Kolejność wewnątrz CORE (do fix-loopa buildem): crafting/econ data (VillagerRecipe, CraftItem, ItemPrice,
  CraftTree, CraftQueue) → InventoryVillager → village (HelpfulVillage, GuildHall, RanchGuildHall, Collection)
  → VillageEconomy → AbstractVillager + 9 profesji → AIHelper/ResourceCluster → AI (11 klas).

### Decyzje API podjęte w trakcie
- **Recepty:** `recipe.getIngredients()` (`NonNullList<Ingredient>`, `Ingredient.getMatchingStacks()`)
  zamiast osobnych gałęzi ShapedRecipes/ShapelessRecipes/ShapedOreRecipe/ShapelessOreRecipe.
- **ItemStack:** `ItemStack.EMPTY` zamiast `null`; `getCount()/setCount()`, `copy()`, `getDisplayName()`,
  `getHasSubtypes()`, `writeToNBT()`, `new ItemStack(nbt)`. Callery sprawdzające `== null` → `isEmpty()`.
- **EntityPlayer.getDisplayName()** zwraca `ITextComponent` w 1.12.2 → używać `getName()` (String).

### Otwarte pytania / ryzyka aktywne
- `EntityFishHookCustom` 1.12.2 — kod/build zielony, ale największa niepewność zostaje in-game: bobber/linka, timing brania, loot do inventory i sprzątanie hooka.
- Thread-safety packetów — ✅ rozwiązane (addScheduledTask). Dedykowany serwer: client-handlery
  referują `Minecraft` w bloku `Side.CLIENT` (anon Runnable ładuje się leniwie, więc OK na serwerze,
  ale klasa Handler referuje Minecraft.getMinecraft() w onMessage → potencjalny NoClassDefFoundError
  na dedykowanym serwerze; do utwardzenia przez proxy jeśli kiedyś potrzebne). Testowane tylko client/integrated.
- Podmiana vanilla villagera (`EntityJoinWorldEvent`) — ✅ działa w grze, bez konfliktów w teście.
- Flagi do weryfikacji w grze (z §8): drzwi/pathfinding, flood-fill hal, addItem overflow client-only,
  ekonomia synchroniczna (hitch na dużych wioskach), getHealth<getHealth/2 dead branch w strażnikach,
  BlockStem→skan sąsiadów, Miner findMine target rozjazd, infiniteArrows decrement.

---

## 7. Powiązane notatki
- **Plan migracji (strategia):** `C:\Users\spege\.claude\plans\backport-helpfulvillagers.md`
- **Reguły rodziny (CRITICAL RULES, MULTIPLAYER, IDE warnings):** `E:\Isuth\modDev\notes\agent_notes.md`
- **Wzór build/struktury:** `E:\Isuth\submod\itcore\` (+ jego `notes/claude.md`)
- **Guide dekompilacji:** `E:\Isuth\modDev\notes\decompilation_guide.md`

---

## 8. Changelog tego pliku
- **2026-06-02** — wstępna wersja. Faza Discovery + plan zakończone, skeleton (etap 0) jeszcze nie zaczęty.
- **2026-06-02** — etap 0 ukończony (skeleton + build zielony). Dodano `gradle.properties` z `org.gradle.java.home`
  na JDK 8 (`C:/Program Files/Java/jdk1.8.0_361`) — Gradle 4.9 nie startuje na domyślnym JDK 24.
- **2026-06-03** — **CORE zielony** (cały SCC + 14 packetów + wiring, fix-loop 2 poprawki). Następnie sportowane:
  pozostałe 13 packetów (27/27), CommonHooks/ClientHooks, GuiHandler + 8 GUI stubów, VillagerMessagesCommand,
  RenderVillagerCustom + ClientProxy, en_us.lang. **Smoke-test ZDANY** — mod działa w grze. Fix-loop in-game:
  packet-flood throttle, renderery → preInit, NPE merge-loop guard, debug `[HV]` + `filter_log.py`.
  Pozostała faza 2: pełne GUI + pełny fish hook (patrz `notes/agent-handoff-phase2.md`).
- **2026-06-03** — Fish hook ported in code: `EntityFishHookCustom` ma server-authoritative cast/bobber/bite/catch,
  vanilla fishing loot table, rod enchant bonuses, spawn data owner/target; renderer rysuje bobber + linkę.
  `FishHookPacket` nie jest już używany do ręcznego spawnu/despawnu, żeby nie dublować Forge entity tracking.
  `./gradlew build --console=plain` zielony. Wymaga smoke-testu w grze.
