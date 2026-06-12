# Cleric Profession Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this
> plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Testing note:** this Forge 1.12.2 repo has no unit-test framework (established convention,
> see notes/CLAUDE.md). Per-task verification = `.\gradlew.bat build --console=plain` exit 0;
> behavioural verification = the user's in-game smoke test with the `[HV]` log loop
> (`python notes\logs\filter_log.py <log>`). This overrides the default TDD step structure.

**Goal:** New support profession "Cleric" (id 10): heals/cleanses villagers using essence converted
from mob drops, shadows guards, and enchants+repairs their gear on kill-count milestones.

**Architecture:** One new entity (`EntityCleric`) + three new AI tasks (`EntityAIClericSupport`,
`EntityAIClericRestock`, `EntityAIFollowGuards`) layered on the existing AbstractVillager
state-machine conventions (EnumActivity, RETURN handoff to EntityAIMoveIndoorsCustom, mutex 3
work tasks). Guild facilities (chest+enchanting table+brewing stand) detected by GuildHall.
Kill counting via `LivingDeathEvent` in CommonHooks.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, mappings snapshot_20171003. Spec:
`docs/superpowers/specs/2026-06-12-cleric-profession-design.md`.

**Hard conventions (repo):** `ItemStack.EMPTY` never null; server-side mutations; comments EN;
commit trailer `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`; build green per task.

---

### Task 1: Texture + lang + renderer case

**Files:**
- Create: `src/main/resources/assets/helpfulvillagers/textures/entity/villager/cleric.png` (generated)
- Modify: `src/main/resources/assets/helpfulvillagers/lang/en_us.lang`
- Modify: `src/main/java/com/spege/helpfulvillagers/renderer/RenderVillagerCustom.java`

- [ ] **Step 1: Generate cleric.png** — one-off python (PIL) recolor of `villager.png` in the same
  directory: hue-shift the robe pixels toward purple with gold trim. Script sketch (run from repo
  root, `pip show pillow` first; if PIL missing: `pip install pillow`):

```python
from PIL import Image
src = Image.open("src/main/resources/assets/helpfulvillagers/textures/entity/villager/villager.png").convert("RGBA")
px = src.load()
for y in range(src.height):
    for x in range(src.width):
        r, g, b, a = px[x, y]
        if a == 0: continue
        # robe-ish pixels: brown/green dominant -> shift to purple; keep skin (high r+g, low b) light
        if abs(r - g) < 30 and b < max(r, g):   # desaturated cloth
            px[x, y] = (min(255, int(r*0.7)+40), int(g*0.5), min(255, int(b*0.6)+70), a)
src.save("src/main/resources/assets/helpfulvillagers/textures/entity/villager/cleric.png")
```
  Eyeball the result (open the png); tweak factors if it looks broken. User may replace later.

- [ ] **Step 2: lang** — append to `en_us.lang`:
```
entity.helpfulvillagers.cleric.name=Cleric
```

- [ ] **Step 3: renderer** — in `RenderVillagerCustom`: add constant
  `private static final ResourceLocation CLERIC = new ResourceLocation("helpfulvillagers", "textures/entity/villager/cleric.png");`
  and `case 10: return CLERIC;` in `textureFor(int)`.

- [ ] **Step 4: Build** — `.\gradlew.bat build --console=plain` → exit 0.
- [ ] **Step 5: Commit** — `feat(cleric): texture, lang and renderer case for profession 10`.

---

### Task 2: EntityCleric skeleton + full profession plumbing

**Files:**
- Create: `src/main/java/com/spege/helpfulvillagers/entity/EntityCleric.java`
- Modify: `src/main/java/com/spege/helpfulvillagers/main/HelpfulVillagers.java` (entity registration)
- Modify: `src/main/java/com/spege/helpfulvillagers/main/ClientProxy.java` (renderer registration — **preInit**, never init)
- Modify: `src/main/java/com/spege/helpfulvillagers/entity/AbstractVillager.java` (`changeProfession` case 10; check `resetRecipes` switch and add a no-recipes case 10 if it switches on profession)
- Modify: `src/main/java/com/spege/helpfulvillagers/village/GuildHall.java` (`matchesProfession` case 10)
- Modify: `src/main/java/com/spege/helpfulvillagers/gui/GuiProfessionDialog.java` (button 10)

- [ ] **Step 1: EntityCleric skeleton** (AI tasks come in later tasks):

```java
package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Cleric villager: heals/cleanses villagers with essence brewed from mob drops, follows the
 *  guards and blesses (enchants + repairs) their gear on kill-count milestones. */
@SuppressWarnings("null")
public class EntityCleric extends AbstractVillager {
    /** Hostile-mob drops convertible to essence (1 item = 1 essence). */
    public static final Set<Item> ESSENCE_ITEMS = new HashSet<Item>(Arrays.asList(
            Items.ROTTEN_FLESH, Items.BONE, Items.STRING, Items.GUNPOWDER,
            Items.SPIDER_EYE, Items.SLIME_BALL));
    public static final int ESSENCE_CAP = 64;
    public static final int HEAL_COST = 2;
    public static final int CLEANSE_COST = 3;
    /** Cumulative kill milestones -> (items, enchant power). Tier index persists in NBT. */
    public static final int[] MILESTONE_KILLS = { 15, 50, 100 };
    public static final int[] MILESTONE_ITEMS = { 1, 2, 3 };
    public static final int[] MILESTONE_POWER = { 5, 15, 30 };

    public int essence;
    public int killCounter;
    public int enchantTier; // 0..2, next milestone index

    public EntityCleric(World world) { super(world); this.init(); }
    public EntityCleric(AbstractVillager villager) { super(villager); this.init(); }

    private void init() {
        this.profession = 10;
        this.profName = "Cleric";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        // Reduced 4-block panic radius (others use 8): the cleric must stay near fights to
        // support the guards, fleeing only from point-blank danger.
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 4.0f, 0.5, 0.6));
        // Task 5/6/7 of the plan add: ClericSupport(2), ClericRestock(3), FollowGuards(4).
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    @Override public ArrayList<BlockPos> getValidCoords() { return null; }
    @Override public boolean isValidTool(ItemStack item) { return false; }
    @Override protected boolean canCraft() { return false; }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setInteger("Essence", this.essence);
        nbt.setInteger("KillCounter", this.killCounter);
        nbt.setInteger("EnchantTier", this.enchantTier);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        this.essence = nbt.getInteger("Essence");
        this.killCounter = nbt.getInteger("KillCounter");
        this.enchantTier = nbt.getInteger("EnchantTier");
    }
}
```
  NOTE: mirror the exact `writeEntityToNBT/readEntityFromNBT` override signatures used by
  EntityMiner (it persists Mineshaft NBT) — copy its `@Override` shape if it differs.

- [ ] **Step 2: registration** — in `HelpfulVillagers` find the `registerModEntity` block (builder
  is the pattern, registered with id 9); add cleric with the next free id (10; fish hook uses 100):
  name `"cleric"`, tracking args copied from the builder line.

- [ ] **Step 3: ClientProxy.preInit** — find the 10 `registerEntityRenderingHandler` calls; add
  `RenderingRegistry.registerEntityRenderingHandler(EntityCleric.class, RenderVillagerCustom::new);`
  in the same style (IRenderFactory / lambda — match the existing lines exactly).

- [ ] **Step 4: changeProfession** — in `AbstractVillager.changeProfession` switch add:
```java
case 10: {
    EntityCleric cleric = new EntityCleric(this);
    this.world.spawnEntity(cleric);   // copy the exact spawn+setDead pattern of case 9 (builder)
    break;
}
```
  Copy the surrounding boilerplate from case 9 verbatim (position copy, setDead, sync). Also grep
  `resetRecipes` for a profession switch — if present, add `case 10:` returning/clearing recipes
  like the merchant case.

- [ ] **Step 5: GuildHall.matchesProfession** — add:
```java
case 10:
    return itemStack.getItem().equals(Items.EXPERIENCE_BOTTLE);
```

- [ ] **Step 6: GuiProfessionDialog** — in `initGui()` add
  `this.buttonList.add(new GuiButton(10, posX + 65, posY + 130, 100, 20, "Cleric"));`
  (third row, centered below the panel; cosmetic texture gap accepted — flagged in spec).

- [ ] **Step 7: Build** — green.
- [ ] **Step 8: Commit** — `feat(cleric): entity, registration and profession plumbing (id 10)`.
  In-game checkpoint possible: frame with Bottle o' Enchanting unlocks hall, profession change works,
  cleric renders with new texture.

---

### Task 3: GuildHall cleric facilities (enchanting table + brewing stand)

**Files:**
- Modify: `src/main/java/com/spege/helpfulvillagers/village/GuildHall.java`

- [ ] **Step 1:** add fields + scan (pattern: `checkChests()` at GuildHall.java:333):

```java
public BlockPos enchantingTablePos;
public BlockPos brewingStandPos;

/** Locates the cleric guild facilities inside the hall (call before using the positions). */
public void checkClericFacilities() {
    this.enchantingTablePos = null;
    this.brewingStandPos = null;
    for (BlockPos currentCoords : this.insideCoords) {
        Block block = this.blockAt(currentCoords);
        if (block == Blocks.ENCHANTING_TABLE && this.enchantingTablePos == null) {
            this.enchantingTablePos = currentCoords;
        } else if (block == Blocks.BREWING_STAND && this.brewingStandPos == null) {
            this.brewingStandPos = currentCoords;
        }
    }
}

public boolean hasClericFacilities() {
    this.checkClericFacilities();
    return this.enchantingTablePos != null && this.brewingStandPos != null
            && this.getAvailableChest() != null;
}
```

- [ ] **Step 2: Build + Commit** — `feat(cleric): guild hall enchanting table / brewing stand detection`.

---

### Task 4: Essence + restock task (`EntityAIClericRestock`)

**Files:**
- Create: `src/main/java/com/spege/helpfulvillagers/ai/EntityAIClericRestock.java`
- Modify: `src/main/java/com/spege/helpfulvillagers/entity/EntityCleric.java` (register task, helpers)

- [ ] **Step 1: helpers on EntityCleric:**

```java
/** Number of essence-convertible items currently in the main inventory. */
public int countEssenceItems() {
    int count = 0;
    for (int i = 0; i < this.inventory.getSizeInventory(); ++i) {
        ItemStack stack = this.inventory.getStackInSlot(i);
        if (!stack.isEmpty() && ESSENCE_ITEMS.contains(stack.getItem())) {
            count += stack.getCount();
        }
    }
    return count;
}
```

- [ ] **Step 2: EntityAIClericRestock** (mutex 3, prio 3; follows the EntityAIGuardResupply shape —
  RETURN handoff to EntityAIMoveIndoorsCustom, 20t work throttle):
  - `shouldExecute` (server, activity IDLE/STORE, hall != null): `cleric.essence < 10 &&
    cleric.countEssenceItems() > 0`, **or** `cleric.inventory.isFull()`.
  - `startExecuting`: `currentActivity = STORE` + `[HV] ClericRestock` log.
  - `updateTask` every 20t: far from hall → `currentActivity = RETURN`; at hall:
    1. `hall.checkClericFacilities()`; if `brewingStandPos == null` → log, IDLE, bail.
    2. move to `brewingStandPos` until within 2 blocks (`AIHelper.findDistance` per axis, the
       resupply pattern), then **convert**: iterate main inventory, for each whitelisted stack
       move `min(stack.getCount(), ESSENCE_CAP - essence)` into essence, shrink/clear stacks;
       play `SoundEvents.BLOCK_BREWING_STAND_BREW` + `WorldServer.spawnParticle(EnumParticleTypes.SPELL_WITCH, ...)`;
       `[HV] ClericRestock: converted N drops -> essence=E`.
    3. deposit leftovers: `getAvailableChest()` → walk over → `inventory.dumpInventory(chest)`
       (essence items already consumed; whatever remains is surplus).
  - `shouldContinueExecuting`: STORE && (still has convertibles && essence < cap || inventory not
    empty); `resetTask`: STORE → IDLE.

- [ ] **Step 3: register** in `EntityCleric.addThisAI()`: `this.tasks.addTask(3, new EntityAIClericRestock(this));`
  (keep RestrictOpenDoor — bump it to prio 5 to avoid the slot clash).

- [ ] **Step 4: Build + Commit** — `feat(cleric): essence resource and brewing-stand restock task`.

---

### Task 5: Heal & cleanse (`EntityAIClericSupport`, part 1)

**Files:**
- Create: `src/main/java/com/spege/helpfulvillagers/ai/EntityAIClericSupport.java`
- Modify: `src/main/java/com/spege/helpfulvillagers/entity/EntityCleric.java` (register prio 2)

- [ ] **Step 1: task skeleton** (mutex 3, prio 2). Fields: `EntityCleric cleric`,
  `AbstractVillager patient`, `int healCooldown`, `int cleanseCooldown`, `int repathDelay`.
  Cooldowns tick down in `updateTask` regardless of patient.
  - patient search (every 20t, server): villagers via
    `world.getEntitiesWithinAABB(AbstractVillager.class, village.actualBounds.grow(8))`,
    skip self/dead; *heal candidates*: `getHealth() < 0.4f * getMaxHealth()` (needs
    `healCooldown <= 0 && essence >= HEAL_COST`); *cleanse candidates*: any active
    `effect.getPotion().isBadEffect()` (needs `cleanseCooldown <= 0 && essence >= CLEANSE_COST`).
    Heal beats cleanse; nearest wins.
  - `shouldExecute`: server && activity IDLE && found patient. `shouldContinueExecuting`: patient
    alive && still qualifies && activity IDLE.

- [ ] **Step 2: heal — splash potion throw** (witch pattern, in `updateTask` when patient is a
  heal case): approach with throttled `cleric.moveTo(patient, 0.6f)` until ≤ 6 blocks **and**
  `cleric.getEntitySenses().canSee(patient)`, then:

```java
ItemStack potion = PotionUtils.appendEffects(new ItemStack(Items.SPLASH_POTION), Arrays.asList(
        new PotionEffect(MobEffects.INSTANT_HEALTH, 1, 0),      // ~3 HP after splash falloff
        new PotionEffect(MobEffects.REGENERATION, 60, 0)));     // ~2 HP over 3 s
EntityPotion proj = new EntityPotion(this.cleric.world, this.cleric, potion);
proj.rotationPitch -= 20.0f;
double dX = patient.posX + patient.motionX - this.cleric.posX;
double dY = patient.posY + (double) patient.getEyeHeight() - 1.1 - this.cleric.posY;
double dZ = patient.posZ + patient.motionZ - this.cleric.posZ;
float horiz = MathHelper.sqrt((float) (dX * dX + dZ * dZ));
proj.shoot(dX, dY + (double) (horiz * 0.2f), dZ, 0.75f, 8.0f);
this.cleric.world.playSound(null, this.cleric.posX, this.cleric.posY, this.cleric.posZ,
        SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.NEUTRAL, 1.0f, 0.8f);
this.cleric.world.spawnEntity(proj);
this.cleric.swingArm(EnumHand.MAIN_HAND);
this.cleric.essence -= EntityCleric.HEAL_COST;
this.healCooldown = 300;
```
  + `[HV] Cleric: heals <name> (hp X/Y) essence=E` log.

- [ ] **Step 3: cleanse** — approach ≤ 3 blocks, then:

```java
List<Potion> bad = new ArrayList<Potion>();
for (PotionEffect effect : patient.getActivePotionEffects()) {
    if (effect.getPotion().isBadEffect()) bad.add(effect.getPotion());
}
for (Potion potion : bad) patient.removePotionEffect(potion);
((WorldServer) this.cleric.world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
        patient.posX, patient.posY + 1.0, patient.posZ, 12, 0.4, 0.6, 0.4, 0.05);
this.cleric.world.playSound(null, patient.posX, patient.posY, patient.posZ,
        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.6f, 1.4f);
this.cleric.essence -= EntityCleric.CLEANSE_COST;
this.cleanseCooldown = 600;
```
  + `[HV] Cleric: cleanses <name> (N effects)` log.

- [ ] **Step 4: register** `this.tasks.addTask(2, new EntityAIClericSupport(this));` in addThisAI.
- [ ] **Step 5: Build + Commit** — `feat(cleric): heal (splash potion) and cleanse behaviours`.

---

### Task 6: Kill counter + blessing milestones (`EntityAIClericSupport`, part 2)

**Files:**
- Modify: `src/main/java/com/spege/helpfulvillagers/main/CommonHooks.java`
- Modify: `src/main/java/com/spege/helpfulvillagers/ai/EntityAIClericSupport.java`

- [ ] **Step 1: kill counter hook** in CommonHooks (style of existing @SubscribeEvent methods):

```java
@SubscribeEvent
public void onLivingDeath(LivingDeathEvent event) {
    if (event.getEntity().world.isRemote || !(event.getEntity() instanceof IMob)) {
        return;
    }
    List<EntityCleric> clerics = event.getEntity().world.getEntitiesWithinAABB(
            EntityCleric.class, event.getEntity().getEntityBoundingBox().grow(16.0));
    for (EntityCleric cleric : clerics) {
        ++cleric.killCounter;
    }
}
```

- [ ] **Step 2: milestone logic** in EntityAIClericSupport — patient search extended: when no
  heal/cleanse patient and `cleric.killCounter >= MILESTONE_KILLS[cleric.enchantTier]` and
  `cleric.homeGuildHall != null && cleric.homeGuildHall.hasClericFacilities()`: blessing mode.
  - Gather candidates from Soldiers/Archers within 8 blocks (`getEntitiesWithinAABB` of
    EntitySoldier/EntityArcher... use AbstractVillager + `instanceof EntitySoldier || EntityArcher`):
    tier 0 → only held weapon (combined slot 27); tiers 1-2 → slots 27-31. Only stacks with
    `!stack.isEnchanted()` and of type ItemSword/ItemBow/ItemArmor.
  - Quality rank: `ItemSword.getAttackDamage()`, ItemBow = 5.0f flat, `ItemArmor.damageReduceAmount`;
    sort descending, take `MILESTONE_ITEMS[tier]`.
  - Approach the owner of the best candidate to ≤ 3 blocks, then for each chosen stack:

```java
EnchantmentHelper.addRandomEnchantment(this.cleric.getRNG(), stack, EntityCleric.MILESTONE_POWER[tier], false);
stack.setItemDamage(0);  // blessing also fully repairs the item
```
  one blessing event per chosen guard cluster; particles
  `EnumParticleTypes.ENCHANTMENT_TABLE` (20, spread 0.5) around each blessed guard + sound
  `SoundEvents.ENTITY_PLAYER_LEVELUP` 1.0f/0.8f; `owner.inventory.syncInventory()` after editing.
  - Then: `if (tier == 2) { killCounter = 0; enchantTier = 0; } else { ++enchantTier; }`
  - `[HV] Cleric: blessing tier N -> M items at power P (kills=K)` log.

- [ ] **Step 3: Build + Commit** — `feat(cleric): kill counter and gear-blessing milestones`.

---

### Task 7: Follow guards + drop handover (`EntityAIFollowGuards`)

**Files:**
- Create: `src/main/java/com/spege/helpfulvillagers/ai/EntityAIFollowGuards.java`
- Modify: `src/main/java/com/spege/helpfulvillagers/entity/EntityCleric.java` (register prio 4)

- [ ] **Step 1: task** (mutex 1, prio 4):
  - `shouldExecute` (server, IDLE, RNG 1-in-40 throttle): nearest EntitySoldier/EntityArcher within
    24 blocks exists and is farther than 6 → follow it.
  - `updateTask`: throttled `cleric.moveTo(guard, 0.6f)` (every 20t); stop path when ≤ 5 blocks.
    **Handover** every 40t when ≤ 3 blocks: iterate the guard's main inventory (slots 0-26), move
    stacks whose item is in `EntityCleric.ESSENCE_ITEMS` into `cleric.inventory.addItem(...)`,
    clear the guard slot (`setMainContents(i, ItemStack.EMPTY)`), `guard.inventory.syncInventory()`;
    `[HV] Cleric: collected N drops from <guard>` log.
  - `shouldContinueExecuting`: guard alive && IDLE && within 32 blocks; `resetTask`: clearPath.

- [ ] **Step 2: Build + Commit** — `feat(cleric): follow guards and mob-drop handover`.

---

### Task 8: Docs + smoke-test handoff

**Files:**
- Modify: `notes/CLAUDE.md` (changelog + POZOSTAŁO entry)
- Modify: `docs/superpowers/specs/2026-06-12-cleric-profession-design.md` (status → implemented)

- [ ] **Step 1:** changelog entry (architecture, new files, plumbing checklist confirmation,
  flagged risks: LivingDeathEvent cost, dialog button cosmetics, addRandomEnchantment power 30).
- [ ] **Step 2:** Build + Commit — `docs: cleric profession dev log + spec status`.
- [ ] **Step 3:** report to user with the in-game checklist:
  1. Hall: frame+Bottle o' Enchanting unlocks Cleric button; profession change works; texture ok.
  2. Hall without brewing stand/enchanting table → restock/blessing idle with [HV] log.
  3. Wounded villager (<40%) → splash potion arc, heal, 15 s cooldown, essence drops by 2.
  4. Villager with poison → particles + sound, effects gone, essence -3.
  5. Drops: cleric picks up mob drops; takes them from guards within 3 blocks; brewing-stand
     conversion sound/particles; surplus lands in hall chest.
  6. Kill 15 mobs near cleric → 1 weapon enchanted **and repaired**; 50 → 2 items; 100 → 3 items,
     counter resets ([HV] blessing logs).
  7. Idle cleric shadows guards on patrol; flees only point-blank mobs; anti-stuck doesn't
     fire during normal cleric work.

## Self-review notes

- Spec coverage: marker/facilities (T2/T3), heal/cleanse variants+costs+cooldowns (T5), essence
  conversion + chest surplus (T4), follow+handover (T7), kill ladder+enchant+repair (T6),
  texture/lang/render/registration/dialog (T1/T2), NBT persistence (T2). ✓
- Known unknowns to resolve at implementation time (verify, don't assume): exact
  `registerModEntity` call shape in HelpfulVillagers, `resetRecipes` switch shape,
  EntityMiner's NBT override signatures, ClientProxy registration style. All are
  read-and-mirror steps, flagged inline.
- Type consistency: EntityCleric constants referenced from tasks 4-7 are all declared in Task 2. ✓
