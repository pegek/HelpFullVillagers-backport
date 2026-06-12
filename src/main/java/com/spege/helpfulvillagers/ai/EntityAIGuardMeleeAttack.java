package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

/**
 * Vanilla-style melee combat task for guards (modelled on {@code EntityAIAttackMelee}): paths to
 * the attack target with a throttled re-path (no per-tick navigator restarts), swings on a
 * 20-tick cooldown and deals weapon-based damage via {@link AbstractVillager#getAttackDamage()}.
 *
 * <p>Creepers are fought hit-and-run: after each blow — and whenever the creeper starts its fuse —
 * the guard backs off out of blast range before re-engaging, so fights no longer level the village.
 *
 * <p>Mutex 3 (movement + look): wander/restriction AIs can no longer hijack the navigator
 * mid-fight, which caused the old jittery combat movement.
 */
@SuppressWarnings("null")
public class EntityAIGuardMeleeAttack extends EntityAIBase {
    private static final int ATTACK_INTERVAL = 20;
    private static final int RETREAT_TICKS = 40;
    /** Squared distance considered safely outside a creeper blast. */
    private static final double SAFE_CREEPER_DISTANCE_SQ = 36.0;

    private final AbstractVillager guard;
    private final float speed;
    private int delayCounter;
    private int attackCooldown;
    private int retreatTicks;

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
        return target != null && target.isEntityAlive();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.shouldExecute();
    }

    @Override
    public void startExecuting() {
        this.delayCounter = 0;
        this.attackCooldown = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void resetTask() {
        this.guard.getNavigator().clearPath();
        this.retreatTicks = 0;
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
        if (this.retreatTicks > 0) {
            this.continueRetreat(target);
            return;
        }
        if (isPrimedCreeper(target)) {
            this.startRetreat(target);
            return;
        }
        if (--this.delayCounter <= 0) {
            this.delayCounter = 4 + this.guard.getRNG().nextInt(7);
            this.guard.moveTo(target, this.speed);
        }
        double distSq = this.guard.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        if (distSq <= this.attackReachSq(target) && this.attackCooldown <= 0) {
            this.attackCooldown = ATTACK_INTERVAL;
            this.guard.getNavigator().clearPath();
            this.guard.swingArm(EnumHand.MAIN_HAND);
            boolean success = target.attackEntityFrom(
                    DamageSource.causeMobDamage(this.guard), this.guard.getAttackDamage());
            if (success) {
                this.guard.damageItem();
                if (target instanceof EntityCreeper) {
                    this.startRetreat(target);
                }
            }
        }
    }

    /** Vanilla melee reach: own width based, measured to the target's feet. */
    private double attackReachSq(EntityLivingBase target) {
        return this.guard.width * 2.0f * this.guard.width * 2.0f + target.width;
    }

    private static boolean isPrimedCreeper(EntityLivingBase target) {
        return target instanceof EntityCreeper && ((EntityCreeper) target).getCreeperState() == 1;
    }

    private void startRetreat(EntityLivingBase target) {
        this.retreatTicks = RETREAT_TICKS;
        this.fleeFrom(target);
    }

    private void continueRetreat(EntityLivingBase target) {
        --this.retreatTicks;
        boolean safeDistance = this.guard.getDistanceSq(target) > SAFE_CREEPER_DISTANCE_SQ;
        if (safeDistance && !isPrimedCreeper(target)) {
            this.retreatTicks = 0;
            return;
        }
        if (this.retreatTicks <= 0 && isPrimedCreeper(target)) {
            // Still fizzing — keep our distance for another round.
            this.retreatTicks = RETREAT_TICKS;
        }
        if (this.guard.getNavigator().noPath()) {
            this.fleeFrom(target);
        }
    }

    private void fleeFrom(EntityLivingBase target) {
        Vec3d threat = new Vec3d(target.posX, target.posY, target.posZ);
        Vec3d flee = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.guard, 8, 4, threat);
        if (flee != null) {
            this.guard.getNavigator().tryMoveToXYZ(flee.x, flee.y, flee.z, 1.0);
        }
    }
}
