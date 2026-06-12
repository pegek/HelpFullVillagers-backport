package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;

/**
 * Shared guard retreat/resupply AI (mutex 3 so it preempts the attack tasks): retreats to the
 * guild hall below half health, and visits the hall to deposit loot and (re)equip tool, armour,
 * ammunition and offhand gear. Replaces the resupply halves of the old soldier/archer guard AIs.
 *
 * <p>Generic over {@link AbstractVillager} (profession specifics live in the
 * needsCombatAmmo/getCombatAmmoItem/needsOffhandEquipment/shouldKeepInInventory hooks) so it can
 * later be reused by other professions.
 *
 * <p>Navigation handoff: when far from the hall this sets {@link EnumActivity#RETURN}, which the
 * existing {@link EntityAIMoveIndoorsCustom} picks up; once near, this task re-triggers and does
 * the chest work. Equipment needs never interrupt combat — only low health does. Needs that the
 * hall cannot satisfy (no armour/ammo in chests) are suppressed for {@link #FAILED_SEARCH_COOLDOWN}
 * ticks so guards don't ping-pong to empty chests.
 */
@SuppressWarnings("null")
public class EntityAIGuardResupply extends EntityAIBase {
    /** Ticks to wait before retrying a need the guild chests could not satisfy. */
    private static final int FAILED_SEARCH_COOLDOWN = 600;
    /** Chest work / re-path runs at most every this many ticks while the task is active. */
    private static final int WORK_INTERVAL = 20;

    /** Occupied main-inventory slots (kept items excluded) that justify a deposit trip. */
    private static final int DEPOSIT_SLOT_THRESHOLD = 6;

    private final AbstractVillager villager;
    private final float speed;
    private long armorRetryAt;
    private long ammoRetryAt;
    private long offhandRetryAt;
    private int workCooldown;
    /** Cleared when all guild chests are full, so deposit-only trips end instead of looping. */
    private boolean depositPossible = true;

    public EntityAIGuardResupply(AbstractVillager villager) {
        this.villager = villager;
        this.speed = 0.75f;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.villager.world.isRemote || this.villager.isChild()) {
            return false;
        }
        EnumActivity activity = this.villager.currentActivity;
        if (activity != EnumActivity.IDLE && activity != EnumActivity.STORE) {
            return false;
        }
        if (this.villager.homeGuildHall == null) {
            return false;
        }
        if (this.needsHealing()) {
            return true;
        }
        // Equipment/deposit trips never interrupt an ongoing fight.
        if (this.villager.getAttackTarget() != null) {
            return false;
        }
        return this.hasEquipmentNeeds() || this.shouldDeposit();
    }

    @Override
    public void startExecuting() {
        this.villager.currentActivity = EnumActivity.STORE;
        this.workCooldown = 0;
        this.depositPossible = true;
        HelpfulVillagers.logger.info(
                "[HV] Resupply: {} id={} starts (health={} tool={} armored={} ammoNeed={} offhandNeed={} loot={})",
                this.villager.getClass().getSimpleName(), this.villager.getEntityId(),
                this.needsHealing() ? "LOW" : "ok", this.villager.hasTool, this.villager.isFullyArmored(),
                this.villager.needsCombatAmmo(), this.villager.needsOffhandEquipment(),
                this.shouldDeposit());
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.villager.currentActivity != EnumActivity.STORE) {
            // RETURN = handed off to EntityAIMoveIndoorsCustom; FOLLOW/IDLE = preempted/done.
            return false;
        }
        if (this.villager.homeGuildHall == null) {
            return false;
        }
        return this.needsHealing() || this.hasEquipmentNeeds() || this.shouldDeposit() && this.depositPossible;
    }

    @Override
    public void resetTask() {
        if (this.villager.currentActivity == EnumActivity.STORE) {
            this.villager.currentActivity = EnumActivity.IDLE;
        }
    }

    @Override
    public void updateTask() {
        if (--this.workCooldown > 0) {
            return;
        }
        this.workCooldown = WORK_INTERVAL;
        if (!this.villager.nearHall()) {
            // Hand navigation to EntityAIMoveIndoorsCustom; we re-trigger once it arrives.
            HelpfulVillagers.logger.info("[HV] Resupply: {} id={} heading to guild hall",
                    this.villager.getClass().getSimpleName(), this.villager.getEntityId());
            this.villager.currentActivity = EnumActivity.RETURN;
            return;
        }
        // Gateway is the EXISTENCE of guild chests, not a free slot: equipping armor/tools/ammo
        // works fine from completely full chests. (The old getAvailableChest() gateway made a
        // well-stocked, full chest lock the guard out of all resupply — observed in-game as an
        // endless "no available chest, will switch halls" loop on unarmed archers.)
        this.villager.homeGuildHall.checkChests();
        if (this.villager.homeGuildHall.guildChests.isEmpty()) {
            HelpfulVillagers.logger.info("[HV] Resupply: {} id={} hall has no chests, will switch halls",
                    this.villager.getClass().getSimpleName(), this.villager.getEntityId());
            this.villager.changeGuildHall = true;
            return;
        }
        this.villager.changeGuildHall = false;
        TileEntityChest chest = this.nearestGuildChest();
        if (AIHelper.findDistance((int) this.villager.posX, chest.getPos().getX()) > 2
                || AIHelper.findDistance((int) this.villager.posY, chest.getPos().getY()) > 2
                || AIHelper.findDistance((int) this.villager.posZ, chest.getPos().getZ()) > 2) {
            this.villager.moveTo(chest.getPos(), this.speed);
            return;
        }
        // Depositing does need free space — skip it (and stop deposit-only trips) when every
        // chest is full; equipping below still proceeds.
        TileEntityChest depositChest = this.villager.homeGuildHall.getAvailableChest();
        if (depositChest != null) {
            this.depositInventory(depositChest);
        } else if (this.shouldDeposit()) {
            this.depositPossible = false;
            HelpfulVillagers.logger.info("[HV] Resupply: {} id={} all chests full, skipping deposit",
                    this.villager.getClass().getSimpleName(), this.villager.getEntityId());
        }
        if (!this.villager.isFullyArmored()) {
            this.equipArmorFromChests();
            if (!this.villager.isFullyArmored()) {
                HelpfulVillagers.logger.info("[HV] Resupply: {} id={} armor incomplete after chest search, retry in {}t",
                        this.villager.getClass().getSimpleName(), this.villager.getEntityId(), FAILED_SEARCH_COOLDOWN);
                this.armorRetryAt = this.now() + FAILED_SEARCH_COOLDOWN;
            }
        }
        this.equipTool();
        if (this.villager.needsCombatAmmo()) {
            this.restockAmmo();
            if (this.villager.needsCombatAmmo()) {
                HelpfulVillagers.logger.info("[HV] Resupply: {} id={} no ammo found in chests, retry in {}t",
                        this.villager.getClass().getSimpleName(), this.villager.getEntityId(), FAILED_SEARCH_COOLDOWN);
                this.ammoRetryAt = this.now() + FAILED_SEARCH_COOLDOWN;
            }
        }
        if (this.villager.needsOffhandEquipment()) {
            this.equipOffhand();
            if (this.villager.needsOffhandEquipment()) {
                this.offhandRetryAt = this.now() + FAILED_SEARCH_COOLDOWN;
            }
        } else {
            this.villager.queuedOffhand = ItemStack.EMPTY;
        }
        HelpfulVillagers.logger.info("[HV] Resupply: {} id={} chest pass done (tool={} armored={} ammoNeed={} offhandNeed={})",
                this.villager.getClass().getSimpleName(), this.villager.getEntityId(),
                this.villager.hasTool || this.villager.isValidTool(this.villager.getCurrentItem()),
                this.villager.isFullyArmored(), this.villager.needsCombatAmmo(), this.villager.needsOffhandEquipment());
    }

    private boolean needsHealing() {
        return this.villager.getHealth() < this.villager.getMaxHealth() / 2.0f;
    }

    /**
     * Deposit trips run when the bag is full or meaningfully loaded — not for every single picked
     * up item. Per-item trips kept guards in the STORE state (combat suspended) almost
     * permanently, which read in-game as "guards wander around and don't fight back".
     */
    private boolean shouldDeposit() {
        if (this.villager.inventory.isFull()) {
            return true;
        }
        int occupied = 0;
        for (int i = 0; i < this.villager.inventory.getSizeInventory(); ++i) {
            ItemStack stack = this.villager.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && !this.villager.shouldKeepInInventory(stack)) {
                ++occupied;
            }
        }
        return occupied >= DEPOSIT_SLOT_THRESHOLD;
    }

    private TileEntityChest nearestGuildChest() {
        TileEntityChest nearest = null;
        double best = Double.MAX_VALUE;
        for (TileEntityChest chest : this.villager.homeGuildHall.guildChests) {
            double dist = this.villager.getDistanceSq(chest.getPos());
            if (dist < best) {
                best = dist;
                nearest = chest;
            }
        }
        return nearest;
    }

    private boolean hasEquipmentNeeds() {
        if (!this.villager.hasTool) {
            return true;
        }
        long now = this.now();
        if (!this.villager.isFullyArmored() && now >= this.armorRetryAt) {
            return true;
        }
        if (this.villager.needsCombatAmmo() && now >= this.ammoRetryAt) {
            return true;
        }
        return this.villager.needsOffhandEquipment() && now >= this.offhandRetryAt;
    }

    private long now() {
        return this.villager.world.getTotalWorldTime();
    }

    /** Dumps loot into the chest, keeping items the profession wants on it (e.g. arrows). */
    private void depositInventory(TileEntityChest chest) {
        if (this.villager.inventory.isEmpty()) {
            return;
        }
        ArrayList<ItemStack> kept = new ArrayList<ItemStack>();
        for (int i = 0; i < this.villager.inventory.getSizeInventory(); ++i) {
            ItemStack stack = this.villager.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && this.villager.shouldKeepInInventory(stack)) {
                kept.add(stack);
                this.villager.inventory.setMainContents(i, ItemStack.EMPTY);
            }
        }
        try {
            this.villager.inventory.dumpInventory(chest);
        } catch (NullPointerException e) {
            // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
        }
        for (int i = 0; i < kept.size(); ++i) {
            this.villager.inventory.addItem(kept.get(i));
        }
    }

    private void equipArmorFromChests() {
        for (TileEntityChest armorChest : this.villager.homeGuildHall.guildChests) {
            if (this.villager.isFullyArmored()) {
                return;
            }
            for (int i = 0; i < armorChest.getSizeInventory(); ++i) {
                ItemStack chestItem = armorChest.getStackInSlot(i);
                if (chestItem.isEmpty() || !(chestItem.getItem() instanceof ItemArmor)) {
                    continue;
                }
                int invSlot = armorInventorySlot(((ItemArmor) chestItem.getItem()).armorType);
                if (invSlot >= 0 && this.villager.inventory.getStackInSlot(invSlot).isEmpty()) {
                    // Equipment indices are 1-4 (slot 27 is the tool), hence invSlot - 27.
                    this.villager.inventory.swapEquipment(armorChest, i, invSlot - 27);
                }
            }
        }
    }

    private static int armorInventorySlot(EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:  return 28;
            case CHEST: return 29;
            case LEGS:  return 30;
            case FEET:  return 31;
            default:    return -1;
        }
    }

    private void equipTool() {
        // hasTool only refreshes in AbstractVillager.resetTool() next tick, so check the slot too.
        boolean equipped = this.villager.hasTool || this.villager.isValidTool(this.villager.getCurrentItem());
        if (!equipped) {
            for (TileEntityChest toolChest : this.villager.homeGuildHall.guildChests) {
                int index = AIHelper.chestContains(toolChest, this.villager);
                if (index < 0) {
                    continue;
                }
                this.villager.inventory.swapEquipment(toolChest, index, 0);
                equipped = true;
                break;
            }
        }
        if (!equipped && this.villager.queuedTool.isEmpty()) {
            // Queue the cheapest valid tool with the village crafters (delivered to a guild chest).
            int lowestPrice = Integer.MAX_VALUE;
            ItemStack lowestItem = ItemStack.EMPTY;
            for (int i = 0; i < this.villager.getValidTools().length; ++i) {
                ItemStack item = this.villager.getValidTools()[i];
                int price = this.villager.homeVillage.economy.getPrice(item.getDisplayName());
                if (price >= lowestPrice && !lowestItem.isEmpty()) {
                    continue;
                }
                lowestPrice = price;
                lowestItem = item;
            }
            this.villager.addCraftItem(new CraftItem(lowestItem, this.villager));
            this.villager.queuedTool = lowestItem;
        } else if (equipped) {
            this.villager.queuedTool = ItemStack.EMPTY;
        }
    }

    /** Pulls the profession's offhand item (e.g. a shield) from guild chests, else queues a craft. */
    private void equipOffhand() {
        for (TileEntityChest chest : this.villager.homeGuildHall.guildChests) {
            for (int i = 0; i < chest.getSizeInventory(); ++i) {
                ItemStack chestItem = chest.getStackInSlot(i);
                if (!chestItem.isEmpty() && this.villager.acceptsOffhandItem(chestItem)) {
                    // Equipment index 5 = the offhand slot (combined slot 32).
                    this.villager.inventory.swapEquipment(chest, i, 5);
                    this.villager.queuedOffhand = ItemStack.EMPTY;
                    return;
                }
            }
        }
        ItemStack desired = this.villager.getDesiredOffhandItem();
        if (!desired.isEmpty() && this.villager.queuedOffhand.isEmpty()) {
            this.villager.addCraftItem(new CraftItem(desired, this.villager));
            this.villager.queuedOffhand = desired;
        }
    }

    private void restockAmmo() {
        ItemStack ammo = this.villager.getCombatAmmoItem();
        if (ammo.isEmpty()) {
            return;
        }
        for (TileEntityChest ammoChest : this.villager.homeGuildHall.guildChests) {
            int index = AIHelper.chestContains(ammoChest, ammo);
            if (index < 0) {
                continue;
            }
            this.villager.inventory.addItem(ammoChest.getStackInSlot(index));
            ammoChest.setInventorySlotContents(index, ItemStack.EMPTY);
            return;
        }
    }
}
