package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * Vanilla-style bow combat task for guards (modelled on {@code EntityAIAttackRangedBow} +
 * {@code AbstractSkeleton}): the guard visibly draws the bow ({@code setActiveHand} — the client
 * gets the aiming animation via the synced hand-active flag), fires after a full ~20-tick draw
 * with charge-based arrow velocity, strafes sideways while in range and backs off (kites) when the
 * target closes in. Bow enchantments (Power/Punch/Flame) apply via
 * {@link EntityArrow#setEnchantmentEffectsFromEntity} — the held bow is mirrored into the MAINHAND
 * equipment slot, which is what the enchantment lookup reads.
 *
 * <p>Out of ammunition this task stops executing and the lower-priority
 * {@link EntityAIGuardMeleeAttack} takes over (both mutex 3).
 */
@SuppressWarnings("null")
public class EntityAIGuardBowAttack extends EntityAIBase {
    private static final double MAX_ATTACK_DISTANCE_SQ = 15.0 * 15.0;
    /** Squared distance below which the guard strafes backwards (kites) instead of holding ground. */
    private static final double KITE_DISTANCE_SQ = 5.0 * 5.0;
    private static final int DRAW_TIME = 20;
    private static final int SHOT_COOLDOWN = 20;

    private final AbstractVillager guard;
    private final float speed;
    private int attackTime = -1;
    private int seeTime;
    private int repathDelay;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public EntityAIGuardBowAttack(AbstractVillager guard) {
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
        return target != null && target.isEntityAlive() && this.canShoot();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.shouldExecute() || !this.guard.getNavigator().noPath() && this.canShoot();
    }

    @Override
    public void startExecuting() {
        this.attackTime = -1;
        this.seeTime = 0;
        this.strafingTime = -1;
        this.repathDelay = 0;
    }

    @Override
    public void resetTask() {
        this.seeTime = 0;
        this.strafingTime = -1;
        this.guard.resetActiveHand();
        this.guard.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.guard.getAttackTarget();
        if (target == null) {
            return;
        }
        double distSq = this.guard.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        boolean canSee = this.guard.getEntitySenses().canSee(target);
        boolean hasSeen = this.seeTime > 0;
        if (canSee != hasSeen) {
            this.seeTime = 0;
        }
        this.seeTime += canSee ? 1 : -1;

        if (distSq <= MAX_ATTACK_DISTANCE_SQ && this.seeTime >= 20) {
            this.guard.getNavigator().clearPath();
            ++this.strafingTime;
        } else {
            if (--this.repathDelay <= 0) {
                this.repathDelay = 4 + this.guard.getRNG().nextInt(7);
                this.guard.moveTo(target, this.speed);
            }
            this.strafingTime = -1;
        }

        if (this.strafingTime >= 20) {
            if (this.guard.getRNG().nextFloat() < 0.3f) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (this.guard.getRNG().nextFloat() < 0.3f) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }
        if (this.strafingTime > -1) {
            if (distSq > MAX_ATTACK_DISTANCE_SQ * 0.75) {
                this.strafingBackwards = false;
            } else if (distSq < KITE_DISTANCE_SQ) {
                this.strafingBackwards = true;
            }
            this.guard.getMoveHelper().strafe(this.strafingBackwards ? -0.5f : 0.5f,
                    this.strafingClockwise ? 0.5f : -0.5f);
            this.guard.faceEntity(target, 30.0f, 30.0f);
        } else {
            this.guard.getLookHelper().setLookPositionWithEntity(target, 30.0f, 30.0f);
        }

        if (this.guard.isHandActive()) {
            if (!canSee && this.seeTime < -60) {
                this.guard.resetActiveHand();
            } else if (canSee) {
                int useCount = this.guard.getItemInUseMaxCount();
                if (useCount >= DRAW_TIME) {
                    this.guard.resetActiveHand();
                    this.shoot(target, ItemBow.getArrowVelocity(useCount));
                    this.attackTime = SHOT_COOLDOWN;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.guard.setActiveHand(EnumHand.MAIN_HAND);
        }
    }

    private boolean canShoot() {
        ItemStack held = this.guard.getCurrentItem();
        return !held.isEmpty() && held.getItem() instanceof ItemBow && !this.guard.needsCombatAmmo();
    }

    private void shoot(EntityLivingBase target, float velocity) {
        if (this.guard.world.isRemote) {
            return;
        }
        EntityTippedArrow arrow = new EntityTippedArrow(this.guard.world, this.guard);
        double d0 = target.posX - this.guard.posX;
        double d1 = target.getEntityBoundingBox().minY + (double) (target.height / 3.0f) - arrow.posY;
        double d2 = target.posZ - this.guard.posZ;
        double d3 = (double) MathHelper.sqrt(d0 * d0 + d2 * d2);
        float inaccuracy = 14 - this.guard.world.getDifficulty().getDifficultyId() * 4;
        arrow.shoot(d0, d1 + d3 * 0.2, d2, velocity * 3.0f, inaccuracy);
        // Reads Power/Punch/Flame off the shooter's equipment (the mirrored held bow).
        arrow.setEnchantmentEffectsFromEntity(this.guard, velocity);
        if (!HelpfulVillagers.infiniteArrows) {
            arrow.pickupStatus = EntityArrow.PickupStatus.ALLOWED;
        }
        this.guard.world.spawnEntity(arrow);
        this.guard.world.playSound(null, this.guard.posX, this.guard.posY, this.guard.posZ,
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.NEUTRAL, 1.0f,
                1.0f / (this.guard.getRNG().nextFloat() * 0.4f + 0.8f));
        this.guard.damageItem();
        if (!HelpfulVillagers.infiniteArrows) {
            int index = this.guard.inventory.containsItem(this.guard.getCombatAmmoItem());
            if (index >= 0) {
                this.guard.inventory.decrementSlot(index);
            }
        }
    }
}
