package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;
import java.util.Iterator;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * Archer village-guard AI: shoots hostile village aggressors from range (falls back to melee when out
 * of arrows), and resupplies arrows/tools/armour at the guild hall.
 *
 * <p>1.12.2 migration: EntityArrow is abstract -> EntityTippedArrow + shoot(); ItemArmor.armorType is
 * an EntityEquipmentSlot; tile coords via getPos(); ItemStack.EMPTY semantics.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIGuardVillageArcher extends EntityAITarget {
    private EntityArcher archer;
    private EntityLivingBase villageAgressorTarget;
    private float speed;
    private int previousTime;
    private int currentTime;

    public EntityAIGuardVillageArcher(EntityArcher archer) {
        super(archer, false, false);
        this.archer = archer;
        this.speed = 0.75f;
        this.previousTime = -1;
        this.currentTime = 0;
        this.setMutexBits(2);
    }

    @Override
    public boolean shouldExecute() {
        if (this.archer.currentActivity == EnumActivity.RETURN || this.archer.currentActivity == EnumActivity.FOLLOW) {
            return false;
        }
        // NOTE: getHealth() < getHealth()/2 is always false - preserved verbatim from the 1.7.10
        // original (likely intended getMaxHealth()); the low-health retreat therefore never fires.
        if (this.archer.getHealth() < this.archer.getHealth() / 2.0f) {
            this.archer.currentActivity = EnumActivity.STORE;
            return true;
        }
        if (this.archer.getRevengeTarget() != null && this.archer.getRevengeTarget().isEntityAlive()
                && this.archer.getRevengeTarget() instanceof IMob) {
            this.villageAgressorTarget = this.archer.getRevengeTarget();
            return true;
        }
        if (!this.archer.world.isRemote && this.archer.homeVillage != null) {
            this.villageAgressorTarget = this.archer.homeVillage.findNearestVillageAggressor(this.archer);
            if (this.villageAgressorTarget != null) {
                return true;
            }
        }
        if (!this.archer.hasTool || this.archer.inventory.containsItem(new ItemStack(Items.ARROW)) < 0) {
            this.archer.currentActivity = EnumActivity.STORE;
            return true;
        }
        return false;
    }

    @Override
    public void startExecuting() {
        this.archer.setAttackTarget(this.villageAgressorTarget);
        super.startExecuting();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.archer.currentActivity == EnumActivity.RETURN || this.archer.currentActivity == EnumActivity.FOLLOW) {
            return false;
        }
        if (this.archer.currentActivity == EnumActivity.STORE) {
            return this.archer.getHealth() < this.archer.getHealth() / 2.0f || !this.archer.hasTool;
        }
        return this.villageAgressorTarget != null && this.villageAgressorTarget.isEntityAlive();
    }

    @Override
    public void updateTask() {
        if (this.archer.currentActivity == EnumActivity.STORE) {
            this.resupply();
        } else {
            this.attack();
        }
    }

    private void resupply() {
        if (this.archer.homeGuildHall == null) {
            this.archer.currentActivity = EnumActivity.IDLE;
            return;
        }
        if (!this.archer.nearHall()) {
            this.archer.currentActivity = EnumActivity.RETURN;
            return;
        }
        this.villageAgressorTarget = this.archer.homeVillage.findNearestVillageAggressor(this.archer);
        if (this.archer.getHealth() >= this.archer.getHealth() / 2.0f && this.villageAgressorTarget != null) {
            this.archer.currentActivity = EnumActivity.IDLE;
        }
        int arrowIndex = this.archer.inventory.containsItem(new ItemStack(Items.ARROW));
        if (!this.archer.inventory.isEmpty() || !this.archer.hasTool || arrowIndex < 0 && !HelpfulVillagers.infiniteArrows) {
            TileEntityChest chest = this.archer.homeGuildHall.getAvailableChest();
            if (chest != null) {
                this.archer.moveTo(chest.getPos(), this.speed);
                this.archer.changeGuildHall = false;
            } else {
                this.archer.changeGuildHall = true;
            }
            if (chest != null
                    && AIHelper.findDistance((int) this.archer.posX, chest.getPos().getX()) <= 2
                    && AIHelper.findDistance((int) this.archer.posY, chest.getPos().getY()) <= 2
                    && AIHelper.findDistance((int) this.archer.posZ, chest.getPos().getZ()) <= 2) {
                ArrayList<ItemStack> arrows = new ArrayList<ItemStack>();
                if (!HelpfulVillagers.infiniteArrows) {
                    for (int i = 0; i < this.archer.inventory.getSizeInventory(); ++i) {
                        // NOTE: ItemStack.equals is reference equality - preserved verbatim from the
                        // 1.7.10 original (this arrow-preservation branch therefore never matched).
                        if (this.archer.inventory.getStackInSlot(i).isEmpty()
                                || !this.archer.inventory.getStackInSlot(i).equals(new ItemStack(Items.ARROW))) {
                            continue;
                        }
                        arrows.add(this.archer.inventory.getStackInSlot(i));
                        this.archer.inventory.setMainContents(i, ItemStack.EMPTY);
                    }
                }
                try {
                    this.archer.inventory.dumpInventory(chest);
                } catch (NullPointerException e) {
                    // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
                }
                if (!HelpfulVillagers.infiniteArrows) {
                    for (int i = 0; i < arrows.size(); ++i) {
                        this.archer.inventory.addItem(arrows.get(i));
                    }
                    arrows.clear();
                }
                if (!this.archer.isFullyArmored()) {
                    Iterator<TileEntityChest> iterator = this.archer.homeGuildHall.guildChests.iterator();
                    armorSearch: while (iterator.hasNext() && !this.archer.isFullyArmored()) {
                        TileEntityChest armorChest = iterator.next();
                        for (int i = 0; i < armorChest.getSizeInventory(); ++i) {
                            ItemStack chestItem = armorChest.getStackInSlot(i);
                            if (!chestItem.isEmpty() && chestItem.getItem() instanceof ItemArmor) {
                                ItemArmor armor = (ItemArmor) chestItem.getItem();
                                EntityEquipmentSlot slot = armor.armorType;
                                if (slot == EntityEquipmentSlot.HEAD) {
                                    if (this.archer.inventory.getStackInSlot(28).isEmpty()) {
                                        this.archer.inventory.swapEquipment(armorChest, i, 1);
                                    }
                                } else if (slot == EntityEquipmentSlot.CHEST) {
                                    if (this.archer.inventory.getStackInSlot(29).isEmpty()) {
                                        this.archer.inventory.swapEquipment(armorChest, i, 2);
                                    }
                                } else if (slot == EntityEquipmentSlot.LEGS) {
                                    if (this.archer.inventory.getStackInSlot(30).isEmpty()) {
                                        this.archer.inventory.swapEquipment(armorChest, i, 3);
                                    }
                                } else if (slot == EntityEquipmentSlot.FEET) {
                                    if (this.archer.inventory.getStackInSlot(31).isEmpty()) {
                                        this.archer.inventory.swapEquipment(armorChest, i, 4);
                                    }
                                }
                            }
                            if (this.archer.isFullyArmored()) {
                                continue armorSearch;
                            }
                        }
                    }
                }
                if (!this.archer.hasTool) {
                    for (TileEntityChest toolChest : this.archer.homeGuildHall.guildChests) {
                        int index = AIHelper.chestContains(toolChest, this.archer);
                        if (index < 0) {
                            continue;
                        }
                        this.archer.inventory.swapEquipment(toolChest, index, 0);
                    }
                }
                if (!this.archer.hasTool && this.archer.queuedTool.isEmpty()) {
                    int lowestPrice = Integer.MAX_VALUE;
                    ItemStack lowestItem = ItemStack.EMPTY;
                    for (int i = 0; i < this.archer.getValidTools().length; ++i) {
                        ItemStack item = this.archer.getValidTools()[i];
                        int price = this.archer.homeVillage.economy.getPrice(item.getDisplayName());
                        if (price >= lowestPrice && !lowestItem.isEmpty()) {
                            continue;
                        }
                        lowestPrice = price;
                        lowestItem = item;
                    }
                    this.archer.addCraftItem(new CraftItem(lowestItem, this.archer));
                    this.archer.queuedTool = lowestItem;
                } else if (this.archer.hasTool) {
                    this.archer.queuedTool = ItemStack.EMPTY;
                }
                if (arrowIndex < 0 && !HelpfulVillagers.infiniteArrows) {
                    for (TileEntityChest arrowChest : this.archer.homeGuildHall.guildChests) {
                        if (AIHelper.chestContains(arrowChest, new ItemStack(Items.ARROW)) < 0) {
                            continue;
                        }
                        int index = AIHelper.chestContains(arrowChest, new ItemStack(Items.ARROW));
                        this.archer.inventory.swapEquipment(arrowChest, index, 0);
                        this.archer.inventory.addItem(arrowChest.getStackInSlot(index));
                        arrowChest.setInventorySlotContents(index, ItemStack.EMPTY);
                        this.archer.currentActivity = EnumActivity.IDLE;
                        break;
                    }
                }
            }
        }
    }

    private void attack() {
        if (this.archer.getRevengeTarget() != null && this.archer.getRevengeTarget().isEntityAlive()
                && this.archer.getRevengeTarget() instanceof IMob) {
            if (this.villageAgressorTarget != this.archer.getRevengeTarget()) {
                this.villageAgressorTarget = this.archer.getRevengeTarget();
            }
        } else if (this.archer.homeVillage != null && this.archer.homeVillage.lastAggressor != null
                && this.villageAgressorTarget != this.archer.homeVillage.lastAggressor
                && this.archer.homeVillage.lastAggressor.isEntityAlive()
                && this.archer.homeVillage.lastAggressor instanceof IMob) {
            this.villageAgressorTarget = this.archer.homeVillage.lastAggressor;
        }
        this.archer.moveTo(this.villageAgressorTarget, this.speed);
        if (this.archer.hasTool && this.archer.inventory.containsItem(new ItemStack(Items.ARROW)) >= 0
                || HelpfulVillagers.infiniteArrows) {
            if (this.archer.canEntityBeSeen(this.villageAgressorTarget)) {
                this.archer.getNavigator().clearPath();
                this.archer.getLookHelper().setLookPositionWithEntity(this.villageAgressorTarget, 30.0f, 30.0f);
                if (this.previousTime < 0) {
                    this.previousTime = this.archer.ticksExisted;
                } else if (this.currentTime - this.previousTime >= this.archer.ARROW_TIME) {
                    if (!this.archer.world.isRemote) {
                        EntityTippedArrow arrow = new EntityTippedArrow(this.archer.world, this.archer);
                        double d0 = this.villageAgressorTarget.posX - this.archer.posX;
                        double d1 = this.villageAgressorTarget.getEntityBoundingBox().minY
                                + (double) (this.villageAgressorTarget.height / 3.0f) - arrow.posY;
                        double d2 = this.villageAgressorTarget.posZ - this.archer.posZ;
                        double d3 = (double) MathHelper.sqrt(d0 * d0 + d2 * d2);
                        arrow.shoot(d0, d1 + d3 * 0.20000000298023224, d2, 1.6f, 2.0f);
                        if (!HelpfulVillagers.infiniteArrows) {
                            arrow.pickupStatus = EntityArrow.PickupStatus.ALLOWED;
                        }
                        this.archer.world.spawnEntity(arrow);
                    }
                    this.archer.world.playSound(null, this.archer.posX, this.archer.posY, this.archer.posZ,
                            SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.NEUTRAL, 1.0f,
                            1.0f / (this.archer.getRNG().nextFloat() * 0.4f + 0.8f));
                    this.archer.damageItem();
                    // NOTE: preserved verbatim - the 1.7.10 original only decrements arrows when
                    // infiniteArrows is on (likely an inverted condition). VERIFY / consider fixing.
                    if (HelpfulVillagers.infiniteArrows) {
                        this.archer.inventory.decrementSlot(this.archer.inventory.containsItem(new ItemStack(Items.ARROW)));
                    }
                    this.previousTime = -1;
                } else {
                    this.currentTime = this.archer.ticksExisted;
                }
            }
        } else if (this.archer.getDistanceSq(this.villageAgressorTarget) <= 5.0) {
            this.archer.getNavigator().clearPath();
            this.archer.swingArm(EnumHand.MAIN_HAND);
            if (this.villageAgressorTarget instanceof EntityCreeper) {
                boolean attackSuccess = this.villageAgressorTarget.attackEntityFrom(DamageSource.causeMobDamage(this.archer), 20.0f);
                if (attackSuccess) {
                    this.archer.damageItem();
                    this.archer.damageItem();
                    this.archer.damageItem();
                }
            } else {
                boolean attackSuccess = this.villageAgressorTarget.attackEntityFrom(DamageSource.causeMobDamage(this.archer),
                        this.archer.getAttackDamage());
                if (attackSuccess) {
                    this.archer.damageItem();
                }
            }
        }
    }
}
