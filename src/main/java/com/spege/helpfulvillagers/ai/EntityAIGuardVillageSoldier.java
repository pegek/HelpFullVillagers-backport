package com.spege.helpfulvillagers.ai;

import java.util.Iterator;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;

/**
 * Soldier village-guard AI: targets hostile village aggressors and fights them in melee, retreating
 * to the guild hall to resupply tools/armour when needed.
 *
 * <p>1.12.2 migration: ItemArmor.armorType is now an {@link EntityEquipmentSlot} (was an int slot id);
 * tile coords via getPos(); ItemStack.EMPTY semantics.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIGuardVillageSoldier extends EntityAITarget {
    private EntitySoldier soldier;
    private EntityLivingBase villageAgressorTarget;
    private float speed;

    public EntityAIGuardVillageSoldier(EntitySoldier soldier) {
        super(soldier, false, false);
        this.soldier = soldier;
        this.speed = 0.75f;
        this.setMutexBits(2);
    }

    @Override
    public boolean shouldExecute() {
        if (this.soldier.currentActivity == EnumActivity.RETURN || this.soldier.currentActivity == EnumActivity.FOLLOW) {
            return false;
        }
        // NOTE: getHealth() < getHealth()/2 is always false - preserved verbatim from the 1.7.10
        // original (it likely intended getMaxHealth()); the low-health retreat therefore never fires.
        if (this.soldier.getHealth() < this.soldier.getHealth() / 2.0f) {
            this.soldier.currentActivity = EnumActivity.STORE;
            return true;
        }
        if (this.soldier.getRevengeTarget() != null && this.soldier.getRevengeTarget().isEntityAlive()
                && this.soldier.getRevengeTarget() instanceof IMob) {
            this.villageAgressorTarget = this.soldier.getRevengeTarget();
            return true;
        }
        if (!this.soldier.world.isRemote && this.soldier.homeVillage != null) {
            this.villageAgressorTarget = this.soldier.homeVillage.findNearestVillageAggressor(this.soldier);
            if (this.villageAgressorTarget != null) {
                return true;
            }
        }
        if (!this.soldier.hasTool) {
            this.soldier.currentActivity = EnumActivity.STORE;
            return true;
        }
        return false;
    }

    @Override
    public void startExecuting() {
        this.soldier.setAttackTarget(this.villageAgressorTarget);
        super.startExecuting();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.soldier.currentActivity == EnumActivity.RETURN || this.soldier.currentActivity == EnumActivity.FOLLOW) {
            return false;
        }
        if (this.soldier.currentActivity == EnumActivity.STORE) {
            return this.soldier.getHealth() < this.soldier.getHealth() / 2.0f || !this.soldier.hasTool;
        }
        return this.villageAgressorTarget != null && this.villageAgressorTarget.isEntityAlive();
    }

    @Override
    public void updateTask() {
        if (this.soldier.currentActivity == EnumActivity.STORE) {
            this.resupply();
        } else {
            this.attack();
        }
    }

    private void resupply() {
        if (this.soldier.homeGuildHall == null) {
            this.soldier.currentActivity = EnumActivity.IDLE;
            return;
        }
        if (!this.soldier.nearHall()) {
            this.soldier.currentActivity = EnumActivity.RETURN;
            return;
        }
        this.villageAgressorTarget = this.soldier.homeVillage.findNearestVillageAggressor(this.soldier);
        if (this.soldier.getHealth() >= this.soldier.getHealth() / 2.0f && this.villageAgressorTarget != null) {
            this.soldier.currentActivity = EnumActivity.IDLE;
        }
        if (!this.soldier.inventory.isEmpty() || !this.soldier.hasTool) {
            TileEntityChest chest = this.soldier.homeGuildHall.getAvailableChest();
            if (chest != null) {
                this.soldier.moveTo(chest.getPos(), this.speed);
            } else {
                this.soldier.changeGuildHall = true;
            }
            if (chest != null
                    && AIHelper.findDistance((int) this.soldier.posX, chest.getPos().getX()) <= 2
                    && AIHelper.findDistance((int) this.soldier.posY, chest.getPos().getY()) <= 2
                    && AIHelper.findDistance((int) this.soldier.posZ, chest.getPos().getZ()) <= 2) {
                try {
                    this.soldier.inventory.dumpInventory(chest);
                } catch (NullPointerException e) {
                    // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
                }
                if (!this.soldier.isFullyArmored()) {
                    Iterator<TileEntityChest> iterator = this.soldier.homeGuildHall.guildChests.iterator();
                    armorSearch: while (iterator.hasNext() && !this.soldier.isFullyArmored()) {
                        TileEntityChest armorChest = iterator.next();
                        for (int i = 0; i < armorChest.getSizeInventory(); ++i) {
                            ItemStack chestItem = armorChest.getStackInSlot(i);
                            if (!chestItem.isEmpty() && chestItem.getItem() instanceof ItemArmor) {
                                ItemArmor armor = (ItemArmor) chestItem.getItem();
                                EntityEquipmentSlot slot = armor.armorType;
                                if (slot == EntityEquipmentSlot.HEAD) {
                                    if (this.soldier.inventory.getStackInSlot(28).isEmpty()) {
                                        this.soldier.inventory.swapEquipment(armorChest, i, 1);
                                    }
                                } else if (slot == EntityEquipmentSlot.CHEST) {
                                    if (this.soldier.inventory.getStackInSlot(29).isEmpty()) {
                                        this.soldier.inventory.swapEquipment(armorChest, i, 2);
                                    }
                                } else if (slot == EntityEquipmentSlot.LEGS) {
                                    if (this.soldier.inventory.getStackInSlot(30).isEmpty()) {
                                        this.soldier.inventory.swapEquipment(armorChest, i, 3);
                                    }
                                } else if (slot == EntityEquipmentSlot.FEET) {
                                    if (this.soldier.inventory.getStackInSlot(31).isEmpty()) {
                                        this.soldier.inventory.swapEquipment(armorChest, i, 4);
                                    }
                                }
                            }
                            if (this.soldier.isFullyArmored()) {
                                continue armorSearch;
                            }
                        }
                    }
                }
                if (!this.soldier.hasTool) {
                    for (TileEntityChest toolChest : this.soldier.homeGuildHall.guildChests) {
                        int index = AIHelper.chestContains(toolChest, this.soldier);
                        if (index < 0) {
                            continue;
                        }
                        this.soldier.inventory.swapEquipment(toolChest, index, 0);
                    }
                }
                if (!this.soldier.hasTool && this.soldier.queuedTool.isEmpty()) {
                    int lowestPrice = Integer.MAX_VALUE;
                    ItemStack lowestItem = ItemStack.EMPTY;
                    for (int i = 0; i < this.soldier.getValidTools().length; ++i) {
                        ItemStack item = this.soldier.getValidTools()[i];
                        int price = this.soldier.homeVillage.economy.getPrice(item.getDisplayName());
                        if (price >= lowestPrice && !lowestItem.isEmpty()) {
                            continue;
                        }
                        lowestPrice = price;
                        lowestItem = item;
                    }
                    this.soldier.addCraftItem(new CraftItem(lowestItem, this.soldier));
                    this.soldier.queuedTool = lowestItem;
                } else if (this.soldier.hasTool) {
                    this.soldier.queuedTool = ItemStack.EMPTY;
                }
            }
        }
    }

    private void attack() {
        if (this.soldier.getRevengeTarget() != null && this.soldier.getRevengeTarget().isEntityAlive()
                && this.soldier.getRevengeTarget() instanceof IMob) {
            if (this.villageAgressorTarget != this.soldier.getRevengeTarget()) {
                this.villageAgressorTarget = this.soldier.getRevengeTarget();
            }
        } else if (this.soldier.homeVillage != null && this.soldier.homeVillage.lastAggressor != null
                && this.villageAgressorTarget != this.soldier.homeVillage.lastAggressor
                && this.soldier.homeVillage.lastAggressor.isEntityAlive()
                && this.soldier.homeVillage.lastAggressor instanceof IMob) {
            this.villageAgressorTarget = this.soldier.homeVillage.lastAggressor;
        }
        this.soldier.moveTo(this.villageAgressorTarget, this.speed);
        if (this.soldier.getDistanceSq(this.villageAgressorTarget) <= 5.0) {
            this.soldier.getNavigator().clearPath();
            this.soldier.swingArm(EnumHand.MAIN_HAND);
            if (this.villageAgressorTarget instanceof EntityCreeper) {
                boolean attackSuccess = this.villageAgressorTarget.attackEntityFrom(DamageSource.causeMobDamage(this.soldier), 20.0f);
                if (attackSuccess) {
                    this.soldier.damageItem();
                    this.soldier.damageItem();
                    this.soldier.damageItem();
                }
            } else {
                boolean attackSuccess = this.villageAgressorTarget.attackEntityFrom(DamageSource.causeMobDamage(this.soldier), 20.0f);
                if (attackSuccess) {
                    this.soldier.damageItem();
                }
            }
        }
    }
}
