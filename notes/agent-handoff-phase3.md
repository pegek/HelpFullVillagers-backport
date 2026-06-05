# Agent Handoff — Helpful Villagers 1.12.2 | Phase 3: Bugfix, Polish & Improvement

> Rozmowy z user po **polsku**; kod/komentarze/commity po **angielsku**.
> Zawsze czytaj `notes/CLAUDE.md` przed cokolwiek — ma pełny kontekst, decyzje API i historię.

---

## 0. Stan startowy

**Mod jest kompletnym forwardem portem Helpful Villagers (WeaselNinja, MIT) 1.7.10 → 1.12.2.**
Build zielony (`.\gradlew.bat build`, HEAD `36645cf`), 82 pliki źródłowe, przetestowany in-game:

- ✅ Villagerzy podmieniani, 9 profesji z AI, renderery z teksturami profesji
- ✅ Wszystkie 8 GUI ekranów (pełne), 27 packetów, komenda `/villagermessages`
- ✅ Fish hook custom (animacja bobera, fishing loot)
- ✅ Ekwipunek (narzędzia + zbroja) widoczny na modelach
- ✅ Village economy, crafting queue, guild halls

**Twoja misja: bugfix, polish i ulepszenia.** Port jest wierny oryginałowi, ale zawiera znane bugi
(zachowane verbatim z 1.7.10) + problemy 1.12.2, które ujawniły się dopiero in-game. Nie dodawaj
nowych ficzerow dopóki user nie poprosi.

---

## 1. Gdzie co jest

| | Ścieżka |
|---|---|
| Repo | `E:\Isuth\HelpVillBackport\` |
| Źródła 1.12.2 | `src/main/java/com/spege/helpfulvillagers/` |
| Referencja 1.7.10 (READ-ONLY) | `helpfulvillagers/decompiled_src/mods/helpfulvillagers/` |
| Dev log (aktualizuj!) | `notes/CLAUDE.md` |
| Reguły rodziny (OBOWIĄZKOWE) | `E:\Isuth\modDev\notes\agent_notes.md` |
| MCP SRG→1.12.2 | `C:\Users\spege\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_stable\39\fields.csv` |
| Logi gry | `C:\Users\spege\curseforge\minecraft\Instances\HelpfullVillTest\logs\latest.log` |
| Skrypt logów | `notes/logs/filter_log.py` → `python filter_log.py latest.log` |

---

## 2. Twarde reguły (niezmienne)

- **Forge 1.12.2 only**, Java 8, `setUnlocalizedName` (nie `setTranslationKey`), bez `var`/`record`.
- **Nie modyfikuj** `helpfulvillagers/` (READ-ONLY referencja).
- **ItemStack**: pusty = `ItemStack.EMPTY` (nigdy `null`), `isEmpty()`, `getCount()`, `shrink()`.
- **Multiplayer**: mutacje świata/encji server-side. Packet handlery przez `addScheduledTask`.
- `@SuppressWarnings("null","deprecation")` tam gdzie Forge @Nonnull/deprecated kąsa.
- modid `helpfulvillagers`, package `com.spege.helpfulvillagers.*`.
- Komentarze EN. Commit trailer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`.

---

## 3. Build & feedback loop

```powershell
# build (JDK 8 pinned w gradle.properties, ~5s incremental):
.\gradlew.bat build --console=plain

# filtruj logi gry po każdym teście:
python notes\logs\filter_log.py "C:\Users\spege\curseforge\minecraft\Instances\HelpfullVillTest\logs\latest.log"
```

Wszystkie debug-messages używają prefixu `[HV]` i są łapane przez skrypt.
Nie uruchamiaj gry — user to robi i wkleja log.

---

## 4. Znane bugi do naprawy (priorytet: zgłoszone przez user → flagowane w kodzie)

### 4.1 Bugi zgłoszone in-game (naprawa na żądanie usera)
Dodawaj tu to co user powie, że nie działa. Priorytety ustalane z userem.

### 4.2 Bugi zachowane verbatim z 1.7.10 (oznaczone `// NOTE:` w kodzie)

Każdy ma komentarz w źródle — szukaj `grep -rn "NOTE:\|preserved verbatim\|VERIFY"`.

| Plik | Linia | Problem | Rekomendacja |
|------|-------|---------|--------------|
| `ai/EntityAIGuardVillageSoldier.java:46` | `shouldExecute` | `getHealth() < getHealth()/2` zawsze false — strażnicy nigdy nie wracają po obrażeniach | Fix: `getHealth() < getMaxHealth() / 2.0f` |
| `ai/EntityAIGuardVillageArcher.java:58` | j.w. | To samo dla łucznika | Fix: j.w. |
| `ai/EntityAIGuardVillageArcher.java:272` | `attack()` | Arrow decrement tylko przy `infiniteArrows=true` — odwrócony warunek | Fix: zmień warunek |
| `ai/EntityAIGuardVillageArcher.java:137` | `resupply()` | `ItemStack.equals` = reference equality — zachowanie strzał nigdy nie działa | Fix: porównaj `item.getItem()` lub usuń ten martwy branch |
| `ai/EntityAIMiner.java:31` | `findMine()` | `miner.target` vs `this.target` rozjazd (dwa osobne pola) — miner może się nie poruszać | Zbadaj, czy naprawdę powoduje bug in-game |
| `inventory/InventoryVillager.java:314` | `addItem()` | Overflow items droppowane tylko na kliencie — serwer je cicho traci | Fix: dropuj server-side |
| `village/GuildHall.java:242,259` | flood-fill | Copy-paste bug w wykrywaniu sąsiadów hal — hale mogą się nie wykrywać | Zbadaj in-game (czy hale rozpoznawane poprawnie) |
| `village/HelpfulVillage.java:41` | `getDoorFromCoords` | Door orientation best-effort (VERIFY in-game) | Przetestuj pathfinding przez drzwi |
| `ai/EntityAIFarmer.java:35` | `harvestCrops` | BlockStem→melon/pumpkin przez skan sąsiadów (brak `getStemDirection`) | Zweryfikuj in-game czy rolnik zbiera melony/dynie |
| `econ/VillageEconomy.java:44` | `initPrices` | One-shot synchroniczny scan bloków — może hitch na dużej wiosce | Monitor in-game; jeśli problem, chunkovizuj |

#### STATUS UPDATE 2026-06-05 — naprawione / odroczone

**✅ NAPRAWIONE** (build zielony, wymaga smoke-testu in-game):
- **Guard health retreat** (Soldier + Archer, 3 miejsca każdy): `getHealth()/2` → `getMaxHealth()/2`.
  Root cause potwierdzony: `updateHealth()` leczy 0.5 HP/60t bezwarunkowo → brak soft-locka, strażnik wraca do walki.
- **Archer arrow decrement** (`attack()`): odwrócony warunek → `if (!infiniteArrows)`. Strzała konsumowana gdy flaga OFF;
  gdy ON pomijamy (brak ryzyka `decrementSlot(-1)`).
- **Archer arrow preservation** (`resupply()`): `ItemStack.equals` (reference) → `getItem().equals(Items.ARROW)`.
  Realizuje intencję: strzały zostają w ekwipunku, depozytuje się resztę.
- **InventoryVillager.addItem overflow**: `isRemote` → `!isRemote` (drop server-side, zgodnie z `dropFromInventory`).
- **Miner target** (`findMine()`): **to był bug WPROWADZONY przez port** (niedokończony rename), NIE verbatim.
  Referencja 1.4.0b5 używa `this.target` konsekwentnie; port zmienił większość na `miner.target` zostawiając
  `moveTo(this.target=null)` → miner stał w miejscu. Fix: `moveTo(this.miner.target)`. **Wysoki priorytet do smoke-testu —
  to oznacza, że miner prawdopodobnie nigdy nie kopał w grze.**

**⏸️ ODROCZONE** (świadoma decyzja — nie ruszać bez reprodukcji in-game):
- **GuildHall flood-fill copy-paste** (checkYDirection +X z `z+1`; checkZDirection +Z woła checkXDirection):
  dwie asymetrie, ale algorytm był **testowany jako działający**. Nie potwierdzono realnego błędu wykrywania hal;
  „naprawa" ryzykuje regresję WSZYSTKICH profesji (chesty/furnace/entrance). → Najpierw zbadać in-game czy hale
  są poprawnie wykrywane; fix tylko przy konkretnej obserwowanej awarii.
- **getDoorFromCoords / pathfinding przez drzwi**: verify item, brak root cause bez obserwacji.
- **Farmer stem melon/pumpkin** (`findAdjacentFruit`): logika sensowna (skan 4 sąsiadów), verify in-game czy zbiera.
- **VillageEconomy initPrices** scan: kwestia wydajności, monitor przy dużych wioskach.

### 4.3 Kwestie wydajnościowe (następne po bugach)

- `getNewHomeVillage()` — throttle do 40t działa, ale nadal iteruje wszystkie wioski. Przy dużej liczbie wiosek może warto cache + dirty flag.
- `ServerTickEvent` — merge-loop jest O(n²) po wioskach. OK przy małej liczbie, ale warto mieć na oku.
- `syncInventory()` wywoływane przy każdej zmianie ekwipunku villager'a — sprawdź czy nie jest nadmiarowe.

### 4.4 Jakość kodu / polish

- Dodaj `[HV]` logi dla każdego nowego bugu który znajdziesz (patrz wzorzec w AbstractVillager/CommonHooks).
- Gdzie `try { ... } catch (NullPointerException e) {}` — rozważ konkretną ochronę zamiast połykania.
- Docstring poprawki (szczególnie metody, które "łamią" własność Javadoca).

---

## 5. Workflow

1. **Zbierz info** — zanim cokolwiek zmienisz, przeczytaj `notes/CLAUDE.md` §6 i sam kod buga.
2. **Jeden bug = jeden commit** — małe, atomowe, łatwe do cofnięcia.
3. **Build zielony przed każdym commitem** — `.\gradlew.bat build`.
4. **Zaktualizuj `notes/CLAUDE.md`** — po naprawieniu każdego bugu.
5. **Nie dodawaj ficzerow** bez pytania usera — mamy listę bugów, nie backlog ficzerów.
6. **Raportuj do usera** co kilka napraw i zatrzymaj się na designowych decyzjach.

---

## 6. Szybki przegląd architektury

```
main/
  HelpfulVillagers.java       — @Mod, static collections, network (27 pkts), entities, GuiHandler
  CommonHooks.java            — server events: EntityJoinWorld (podmiana), ServerTick (village loop), WorldLoad
  ClientHooks.java            — client events: frames, config sync
  GuiHandler.java             — IDs 0-7 → GUI klasy/Container

entity/                       — 9 profesji extends AbstractVillager extends EntityVillager
  AbstractVillager.java       — 1246 linii: inventory, AI, crafting, economy, networking hub

ai/                           — 11 klas EntityAIBase/Target/Worker
  EntityAIWorker.java         — baza: IDLE→GATHER→RETURN→CRAFT→STORE state machine

village/                      — HelpfulVillage, GuildHall, RanchGuildHall, HelpfulVillageCollection (NBT persist)
econ/                         — VillageEconomy, ItemPrice (sync scan)
crafting/                     — VillagerRecipe, CraftItem, CraftTree, CraftQueue
inventory/                    — InventoryVillager (27+5 slots), ContainerInventoryVillager
network/                      — 27 IMessage + addScheduledTask handlers
gui/                          — 8 ekranów: VillagerDialog, ProfessionDialog, VillagerInventory, Nickname,
                                CraftingMenu, CraftStats, TeachRecipe, Barter
renderer/                     — RenderVillagerCustom (preInit!), RenderFishHookCustom
util/                         — AIHelper (chest/furnace ops, breakBlock), ResourceCluster (flood-fill cluster)
```

**Kluczowe mapowania SRG→1.12.2** (pełna lista w §7 `notes/CLAUDE.md`):
`posX/Y/Z`, `world`, `ticksExisted`, `rotationYaw/Pitch`, `isDead`, `getNavigator()`, `getLookHelper()`,
`setItemStackToSlot(EntityEquipmentSlot, ItemStack)`, `getEntityBoundingBox()`, `BlockPos`, `IBlockState`.

---

## 7. Ważne lekcje z poprzednich faz (nie powtarzaj tych błędów)

| Pułapka | Jak uniknąć |
|---------|------------|
| Renderery w `init()` zamiast `preInit()` | Zawsze: renderery → `ClientProxy.preInit()` |
| `syncEquipment()` co tick | Throttle lub usuń; tracker synchronizuje equipment automatycznie |
| `event.world` (prywatne w 1.12.2) | Używaj `event.getWorld()` |
| `getEntitiesWithinAABB(IMob.class, ...)` | IMob to interfejs → `EntityLivingBase` + `instanceof IMob` |
| `WorldSavedData` w złym pakiecie | `net.minecraft.world.storage.WorldSavedData` (nie `world.*`) |
| `updateArmor()` po obu stronach | Mutacja equipment slots = server-only; klient ma tracker |
| VillageSyncPacket co tick | Throttle: co 20 ticków |
| NPE przy null `actualBounds` | Nowe `HelpfulVillage` ma null bounds przed `updateVillageBox()` |
