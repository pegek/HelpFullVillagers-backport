package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * Follow-leader AI for recruited guards. Walks the villager to its leader and, for soldiers/archers,
 * attacks the leader's (or its own) hostile aggressor.
 *
 * <p>1.12.2 migration: EntityArrow is abstract -> spawn an {@link EntityTippedArrow} and aim it with
 * {@code shoot(...)} (vanilla skeleton pattern); sound via {@link SoundEvents}; revenge target via
 * getRevengeTarget; swingArm(EnumHand).
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIFollowLeader extends EntityAIBase {
    // NOTE: follow-mode combat below still uses the legacy instant-shot mechanics. The village
    // guard duty moved to EntityAIGuardBowAttack/EntityAIGuardMeleeAttack (draw animation,
    // cooldowns, enchantments); porting follow-mode onto those is a flagged future improvement.
    private static final int ARROW_TIME = 20;

    private AbstractVillager villager;
    private EntityLivingBase leader;
    private EntityLivingBase threatTarget;
    private int count;
    private float speed;
    private int previousTime;
    private int currentTime;

    public EntityAIFollowLeader(AbstractVillager abstractEntity) {
        this.villager = abstractEntity;
        this.speed = 0.8f;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        this.leader = this.villager.getLeader();
        return this.leader != null;
    }

    @Override
    public void startExecuting() {
        this.count = 0;
    }

    @Override
    public void updateTask() {
        if (AIHelper.findDistance((int) this.villager.posX, (int) this.leader.posX) <= 1
                && AIHelper.findDistance((int) this.villager.posY, (int) this.leader.posY) <= 1
                && AIHelper.findDistance((int) this.villager.posZ, (int) this.leader.posZ) <= 1) {
            this.villager.getNavigator().clearPath();
        } else if (--this.count <= 0) {
            this.count = 10;
            this.speed = 0.8f;
            this.villager.moveTo(this.leader, this.speed);
        }
        if (this.villager instanceof EntitySoldier || this.villager instanceof EntityArcher
                && this.villager.inventory.containsItem(new ItemStack(Items.ARROW)) < 0) {
            this.protectLeaderMelee();
        } else if (this.villager instanceof EntityArcher) {
            this.protectLeaderRanged();
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        this.leader = this.villager.getLeader();
        return this.leader != null && (this.leader == null || this.leader.isEntityAlive());
    }

    @Override
    public void resetTask() {
        this.speed = 0.8f;
        this.villager.getNavigator().tryMoveToXYZ(this.villager.posX, this.villager.posY, this.villager.posZ, 0.3);
        this.villager.setLeader(null);
        this.villager.currentActivity = EnumActivity.IDLE;
    }

    private void protectLeaderMelee() {
        this.threatTarget = this.villager.getRevengeTarget() != null && this.villager.getRevengeTarget().isEntityAlive()
                && this.villager.getRevengeTarget() instanceof IMob
                        ? this.villager.getRevengeTarget()
                        : (this.leader.getRevengeTarget() != null && this.leader.getRevengeTarget().isEntityAlive()
                                && this.leader.getRevengeTarget() instanceof IMob ? this.leader.getRevengeTarget() : null);
        if (this.threatTarget != null) {
            boolean canMove = this.villager.getNavigator().tryMoveToEntityLiving(this.threatTarget, this.speed);
            if (!canMove) {
                this.villager.getNavigator().tryMoveToEntityLiving(this.leader, this.speed);
            }
            if (this.villager.getDistanceSq(this.threatTarget) <= 5.0) {
                this.villager.getNavigator().clearPath();
                this.villager.swingArm(EnumHand.MAIN_HAND);
                boolean attackSuccess = this.threatTarget.attackEntityFrom(DamageSource.causeMobDamage(this.villager),
                        this.villager.getAttackDamage());
                if (attackSuccess) {
                    this.villager.damageItem();
                }
            }
        }
    }

    private void protectLeaderRanged() {
        this.threatTarget = this.villager.getRevengeTarget() != null && this.villager.getRevengeTarget().isEntityAlive()
                && this.villager.getRevengeTarget() instanceof IMob
                        ? this.villager.getRevengeTarget()
                        : (this.leader.getRevengeTarget() != null && this.leader.getRevengeTarget().isEntityAlive()
                                && this.leader.getRevengeTarget() instanceof IMob ? this.leader.getRevengeTarget() : null);
        EntityArcher archer = (EntityArcher) this.villager;
        if (this.threatTarget != null) {
            boolean canMove = this.villager.getNavigator().tryMoveToEntityLiving(this.threatTarget, this.speed);
            if (!canMove) {
                this.villager.getNavigator().tryMoveToEntityLiving(this.leader, this.speed);
            }
            if (archer.canEntityBeSeen(this.threatTarget)) {
                archer.getNavigator().clearPath();
                archer.getLookHelper().setLookPositionWithEntity(this.threatTarget, 30.0f, 30.0f);
                if (this.previousTime < 0) {
                    this.previousTime = archer.ticksExisted;
                } else if (this.currentTime - this.previousTime >= ARROW_TIME) {
                    if (!archer.world.isRemote) {
                        EntityTippedArrow arrow = new EntityTippedArrow(archer.world, archer);
                        double d0 = this.threatTarget.posX - archer.posX;
                        double d1 = this.threatTarget.getEntityBoundingBox().minY + (double) (this.threatTarget.height / 3.0f)
                                - arrow.posY;
                        double d2 = this.threatTarget.posZ - archer.posZ;
                        double d3 = (double) MathHelper.sqrt(d0 * d0 + d2 * d2);
                        arrow.shoot(d0, d1 + d3 * 0.20000000298023224, d2, 1.6f, 2.0f);
                        arrow.pickupStatus = EntityArrow.PickupStatus.ALLOWED;
                        archer.world.spawnEntity(arrow);
                    }
                    archer.world.playSound(null, archer.posX, archer.posY, archer.posZ, SoundEvents.ENTITY_ARROW_SHOOT,
                            SoundCategory.NEUTRAL, 1.0f, 1.0f / (archer.getRNG().nextFloat() * 0.4f + 0.8f));
                    archer.damageItem();
                    archer.inventory.decrementSlot(archer.inventory.containsItem(new ItemStack(Items.ARROW)));
                    this.previousTime = -1;
                } else {
                    this.currentTime = archer.ticksExisted;
                }
            }
        }
    }
}
