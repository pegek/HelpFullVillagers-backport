package com.spege.helpfulvillagers.ai;

import java.util.List;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntityCleric;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemStack;

/**
 * Idle behaviour of the cleric: shadow the nearest Soldier/Archer (the cleric's place is with the
 * fighters — that is where kills are counted and wounds happen), staying a respectful few blocks
 * away. While close, the guards hand over their essence-convertible mob drops to the cleric.
 */
@SuppressWarnings("null")
public class EntityAIFollowGuards extends EntityAIBase {
    private static final double SEARCH_RANGE = 24.0;
    private static final double FOLLOW_START_SQ = 6.0 * 6.0;
    private static final double FOLLOW_STOP_SQ = 5.0 * 5.0;
    private static final double GIVE_UP_RANGE_SQ = 32.0 * 32.0;
    private static final double HANDOVER_RANGE_SQ = 3.0 * 3.0;
    private static final int HANDOVER_INTERVAL = 40;
    /** 1-in-N tick chance to start following while idle. */
    private static final int START_CHANCE = 40;

    private final EntityCleric cleric;
    private final float speed;
    private AbstractVillager guard;
    private int repathDelay;
    private int handoverCooldown;

    public EntityAIFollowGuards(EntityCleric cleric) {
        this.cleric = cleric;
        this.speed = 0.6f;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (this.cleric.world.isRemote || this.cleric.isChild()
                || this.cleric.currentActivity != EnumActivity.IDLE) {
            return false;
        }
        if (this.cleric.getRNG().nextInt(START_CHANCE) != 0) {
            return false;
        }
        this.guard = this.findNearestGuard();
        return this.guard != null && this.cleric.getDistanceSq(this.guard) > FOLLOW_START_SQ;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.cleric.currentActivity == EnumActivity.IDLE
                && this.guard != null && this.guard.isEntityAlive()
                && this.cleric.getDistanceSq(this.guard) <= GIVE_UP_RANGE_SQ;
    }

    @Override
    public void startExecuting() {
        this.repathDelay = 0;
        this.handoverCooldown = 0;
    }

    @Override
    public void resetTask() {
        this.guard = null;
        this.cleric.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        double distSq = this.cleric.getDistanceSq(this.guard);
        if (distSq <= FOLLOW_STOP_SQ) {
            this.cleric.getNavigator().clearPath();
            this.cleric.getLookHelper().setLookPositionWithEntity(this.guard, 30.0f, 30.0f);
        } else if (--this.repathDelay <= 0) {
            this.repathDelay = 20;
            this.cleric.moveTo(this.guard, this.speed);
        }
        if (--this.handoverCooldown <= 0) {
            this.handoverCooldown = HANDOVER_INTERVAL;
            if (distSq <= HANDOVER_RANGE_SQ) {
                this.collectDropsFrom(this.guard);
            }
        }
    }

    private AbstractVillager findNearestGuard() {
        List<AbstractVillager> villagers = this.cleric.world.getEntitiesWithinAABB(
                AbstractVillager.class, this.cleric.getEntityBoundingBox().grow(SEARCH_RANGE));
        AbstractVillager nearest = null;
        double best = Double.MAX_VALUE;
        for (AbstractVillager curr : villagers) {
            if (!(curr instanceof EntitySoldier || curr instanceof EntityArcher) || !curr.isEntityAlive()) {
                continue;
            }
            double dist = this.cleric.getDistanceSq(curr);
            if (dist < best) {
                best = dist;
                nearest = curr;
            }
        }
        return nearest;
    }

    /** Pulls essence-convertible mob drops out of the guard's main inventory into the cleric's. */
    private void collectDropsFrom(AbstractVillager donor) {
        int collected = 0;
        for (int i = 0; i < donor.inventory.getSizeInventory(); ++i) {
            ItemStack stack = donor.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !EntityCleric.ESSENCE_ITEMS.contains(stack.getItem())) {
                continue;
            }
            collected += stack.getCount();
            this.cleric.inventory.addItem(stack);
            donor.inventory.setMainContents(i, ItemStack.EMPTY);
        }
        if (collected > 0) {
            donor.inventory.syncInventory();
            this.cleric.inventory.syncInventory();
            HelpfulVillagers.logger.info("[HV] Cleric: id={} collected {} drops from {} id={}",
                    this.cleric.getEntityId(), collected, donor.getClass().getSimpleName(), donor.getEntityId());
        }
    }
}
