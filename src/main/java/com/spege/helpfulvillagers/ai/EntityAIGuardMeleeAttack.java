package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.inventory.InventoryVillager;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;

/**
 * Vanilla-style melee combat task for guards (modelled on {@code EntityAIAttackMelee}): paths to
 * the attack target with a throttled re-path (no per-tick navigator restarts), swings on a
 * 20-tick cooldown and deals weapon-based damage via {@link AbstractVillager#getAttackDamage()}.
 *
 * <p>Never engages creepers — melee vs creeper means explosions in the village, so the target
 * task filters them out for melee guards and this task refuses them as defence in depth
 * (creepers are the archers' job).
 *
 * <p>Mutex 3 (movement + look): wander/restriction AIs can no longer hijack the navigator
 * mid-fight, which caused the old jittery combat movement.
 */
@SuppressWarnings("null")
public class EntityAIGuardMeleeAttack extends EntityAIBase {
    private static final int ATTACK_INTERVAL = 20;

    private final AbstractVillager guard;
    private final float speed;
    private int delayCounter;
    private int attackCooldown;

    public EntityAIGuardMeleeAttack(AbstractVillager guard) {
        this.guard = guard;
        this.speed = 0.75f;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.guard.world.isRemote || this.guard.currentActivity != EnumActivity.IDLE) {
            return false;
        }
        EntityLivingBase target = this.guard.getAttackTarget();
        return target != null && target.isEntityAlive() && !(target instanceof EntityCreeper);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.shouldExecute();
    }

    @Override
    public void startExecuting() {
        this.delayCounter = 0;
        this.attackCooldown = 0;
    }

    @Override
    public void resetTask() {
        this.guard.getNavigator().clearPath();
        this.lowerShield();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.guard.getAttackTarget();
        if (target == null) {
            return;
        }
        this.guard.getLookHelper().setLookPositionWithEntity(target, 30.0f, 30.0f);
        if (this.attackCooldown > 0) {
            --this.attackCooldown;
        }
        if (--this.delayCounter <= 0) {
            this.delayCounter = 4 + this.guard.getRNG().nextInt(7);
            this.guard.moveTo(target, this.speed);
        }
        double distSq = this.guard.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        this.updateShield(distSq);
        if (distSq <= this.attackReachSq(target) && this.attackCooldown <= 0) {
            this.attackCooldown = ATTACK_INTERVAL;
            this.lowerShield();
            this.guard.getNavigator().clearPath();
            this.guard.swingArm(EnumHand.MAIN_HAND);
            boolean success = target.attackEntityFrom(
                    DamageSource.causeMobDamage(this.guard), this.guard.getAttackDamage());
            if (success) {
                this.guard.damageItem();
            }
        }
    }

    /** Vanilla melee reach: own width based, measured to the target's feet. */
    private double attackReachSq(EntityLivingBase target) {
        return this.guard.width * 2.0f * this.guard.width * 2.0f + target.width;
    }

    /**
     * Holds the offhand shield up while closing in and between swings (the vanilla blocking
     * mechanics in EntityLivingBase do the damage reduction once the hand is active), and lowers
     * it for the swing itself.
     */
    private void updateShield(double distSq) {
        ItemStack offhand = this.guard.inventory.getStackInSlot(InventoryVillager.OFFHAND_SLOT);
        boolean hasShield = !offhand.isEmpty() && offhand.getItem() instanceof ItemShield;
        // Raise within 6 blocks of the target while the swing is still cooling down.
        if (hasShield && distSq <= 36.0 && this.attackCooldown > 5) {
            if (!this.guard.isHandActive()) {
                this.guard.setActiveHand(EnumHand.OFF_HAND);
            }
        } else {
            this.lowerShield();
        }
    }

    private void lowerShield() {
        if (this.guard.isHandActive() && this.guard.getActiveHand() == EnumHand.OFF_HAND) {
            this.guard.resetActiveHand();
        }
    }
}
