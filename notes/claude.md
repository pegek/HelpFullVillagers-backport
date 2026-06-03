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
| **CORE** | **Klaster SCC (jeden build na końcu)** — patrz REWIZJA niżej | 🔄 | crafting/econ → inventory → village → economy → AbstractVillager+9 → AIHelper/ResourceCluster → AI |
| ↳ C1 | crafting/econ data (VillagerRecipe, CraftItem, ItemPrice, CraftTree, CraftQueue) | ✅(kod) | sportowane; build dopiero po całym CORE. getIngredients()+ItemStack.EMPTY |
| ↳ C2 | InventoryVillager + ContainerInventoryVillager | ✅(kod) | IInventory pełna migracja; ItemStack.EMPTY; ItemArmor.armorType→EntityEquipmentSlot. Zależy od InventoryPacket → packet wchodzi do CORE |
| ↳ C3 | village (HelpfulVillage, GuildHall, RanchGuildHall, HelpfulVillageCollection) | ✅(kod) | ChunkCoordinates→BlockPos; isSideSolid(EnumFacing); door API best-effort (VERIFY in-game); WorldSavedData.writeToNBT zwraca NBTTagCompound, getPerWorldStorage() |
| ↳ C4 | VillageEconomy | ✅(kod) | **Thread→synchroniczne** (off-thread world access niebezpieczny w 1.12.2); CFR rozwalił initPrices (zrekonstruowane); canSilkHarvest protected→pominięte; CraftingManager.REGISTRY; getName() dla kont; getDrops(NonNullList,...) |
| ↳ C5 | AbstractVillager (1246) + 9 profesji | ⬜ | DataWatcher → EntityDataManager |
| ↳ C6 | util/AIHelper + ResourceCluster | ⬜ | BlockPos; getDrops sig |
| ↳ C7 | AI (11 klas EntityAIBase) | ⬜ | navigator/pathfinding |
| 3 | Network layer (27 packetów, rename + main-thread scheduling) — **po CORE** | ⬜ | |
| 9 | GUI (8 ekranów + GuiHandler) — po CORE | ⬜ | button objects, I18n |
| 10 | Fish hook (EntityFishHookCustom + render + packet) — **risky, last** | ⬜ | EntityFishHook przepisany w 1.12.2 |
| 11 | Renderers + ClientProxy registration (IRenderFactory) | ⬜ | |
| 12 | Command (CommandBase rewrite) | ⬜ | |
| 13 | Lang + final assets + (opcjonalnie) spawn eggs | ⬜ | en_us.lang nazwy entity |
| 14 | Full build + reobf jar → user smoke test | ⬜ | |

### Decyzje podjęte
- modid `helpfulvillagers`, package `com.spege.helpfulvillagers` (reużycie assetów).
- Greenfield: brak compat z save 1.7.10.
- Standalone, bez itcore, bez mixinów.
- Spawn eggs dla 9 profesji — opcjonalne (do decyzji na etapie 13).
- Fish hook: własny `EntityFishHookCustom` od zera (do potwierdzenia na etapie 10).
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
- `EntityFishHook` 1.12.2 — największa niepewność (etap 10).
- Thread-safety wszystkich 27 packetów (etap 3).
- Podmiana vanilla villagera na `EntityJoinWorldEvent` — sprawdzić konflikty w 1.12.2 (etap 8).

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
