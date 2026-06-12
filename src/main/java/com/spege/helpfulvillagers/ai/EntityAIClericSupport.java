package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityCleric;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;

/**
 * Cleric support AI: scans the village for villagers below 40% health (throws a real splash
 * healing potion at them — instant ~3 HP + regeneration ~2 HP, witch-style aim) or suffering a
 * negative status effect (cleanses it with particles + sound up close). Both services consume
 * essence (see {@link EntityCleric#HEAL_COST}/{@link EntityCleric#CLEANSE_COST}) and have
 * independent cooldowns (15 s / 30 s). Healing outranks cleansing; nearest patient first.
 */
@SuppressWarnings("null")
public class EntityAIClericSupport extends EntityAIBase {
    private static final int HEAL_COOLDOWN = 300;
    private static final int CLEANSE_COOLDOWN = 600;
    private static final float HEAL_THRESHOLD = 0.4f;
    private static final double HEAL_RANGE_SQ = 36.0;
    private static final double CLEANSE_RANGE_SQ = 9.0;
    /** 1-in-N tick chance to run the patient scan, like vanilla target tasks. */
    private static final int SCAN_CHANCE = 10;

    private final EntityCleric cleric;
    private final float speed;
    private AbstractVillager patient;
    private boolean healMode;
    private long healReadyAt;
    private long cleanseReadyAt;
    private int repathDelay;

    public EntityAIClericSupport(EntityCleric cleric) {
        this.cleric = cleric;
        this.speed = 0.6f;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.cleric.world.isRemote || this.cleric.isChild()
                || this.cleric.currentActivity != EnumActivity.IDLE || this.cleric.homeVillage == null) {
            return false;
        }
        if (this.cleric.getRNG().nextInt(SCAN_CHANCE) != 0) {
            return false;
        }
        return this.findPatient();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.cleric.currentActivity != EnumActivity.IDLE
                || this.patient == null || !this.patient.isEntityAlive()) {
            return false;
        }
        if (this.healMode) {
            return this.canHeal() && isWounded(this.patient);
        }
        return this.canCleanse() && hasBadEffect(this.patient);
    }

    @Override
    public void startExecuting() {
        this.repathDelay = 0;
    }

    @Override
    public void resetTask() {
        this.patient = null;
        this.cleric.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        if (this.patient == null) {
            return;
        }
        this.cleric.getLookHelper().setLookPositionWithEntity(this.patient, 30.0f, 30.0f);
        double distSq = this.cleric.getDistanceSq(this.patient);
        if (this.healMode) {
            if (distSq <= HEAL_RANGE_SQ && this.cleric.getEntitySenses().canSee(this.patient)) {
                this.cleric.getNavigator().clearPath();
                this.throwHealingPotion(this.patient);
                return;
            }
        } else if (distSq <= CLEANSE_RANGE_SQ) {
            this.cleric.getNavigator().clearPath();
            this.cleanse(this.patient);
            return;
        }
        if (--this.repathDelay <= 0) {
            this.repathDelay = 4 + this.cleric.getRNG().nextInt(7);
            this.cleric.moveTo(this.patient, this.speed);
        }
    }

    private boolean canHeal() {
        return this.cleric.essence >= EntityCleric.HEAL_COST
                && this.cleric.world.getTotalWorldTime() >= this.healReadyAt;
    }

    private boolean canCleanse() {
        return this.cleric.essence >= EntityCleric.CLEANSE_COST
                && this.cleric.world.getTotalWorldTime() >= this.cleanseReadyAt;
    }

    private static boolean isWounded(AbstractVillager villager) {
        return villager.getHealth() < HEAL_THRESHOLD * villager.getMaxHealth();
    }

    private static boolean hasBadEffect(AbstractVillager villager) {
        for (PotionEffect effect : villager.getActivePotionEffects()) {
            if (effect.getPotion().isBadEffect()) {
                return true;
            }
        }
        return false;
    }

    /** Nearest wounded villager first (heal), else nearest one with a negative effect (cleanse). */
    private boolean findPatient() {
        if (this.cleric.homeVillage.actualBounds == null) {
            return false;
        }
        boolean canHeal = this.canHeal();
        boolean canCleanse = this.canCleanse();
        if (!canHeal && !canCleanse) {
            return false;
        }
        List<AbstractVillager> villagers = this.cleric.world.getEntitiesWithinAABB(
                AbstractVillager.class, this.cleric.homeVillage.actualBounds.grow(8.0));
        AbstractVillager bestHeal = null;
        AbstractVillager bestCleanse = null;
        double distHeal = Double.MAX_VALUE;
        double distCleanse = Double.MAX_VALUE;
        for (AbstractVillager curr : villagers) {
            if (curr == this.cleric || !curr.isEntityAlive()) {
                continue;
            }
            double dist = this.cleric.getDistanceSq(curr);
            if (canHeal && isWounded(curr) && dist < distHeal) {
                distHeal = dist;
                bestHeal = curr;
            }
            if (canCleanse && hasBadEffect(curr) && dist < distCleanse) {
                distCleanse = dist;
                bestCleanse = curr;
            }
        }
        if (bestHeal != null) {
            this.patient = bestHeal;
            this.healMode = true;
            return true;
        }
        if (bestCleanse != null) {
            this.patient = bestCleanse;
            this.healMode = false;
            return true;
        }
        return false;
    }

    /** Witch-style splash potion throw: Instant Health I (~3 HP after splash falloff) + short regen. */
    private void throwHealingPotion(AbstractVillager target) {
        ItemStack potion = PotionUtils.appendEffects(new ItemStack(Items.SPLASH_POTION), Arrays.asList(
                new PotionEffect(MobEffects.INSTANT_HEALTH, 1, 0),
                new PotionEffect(MobEffects.REGENERATION, 60, 0)));
        EntityPotion projectile = new EntityPotion(this.cleric.world, this.cleric, potion);
        projectile.rotationPitch -= 20.0f;
        double dX = target.posX + target.motionX - this.cleric.posX;
        double dY = target.posY + (double) target.getEyeHeight() - 1.1 - this.cleric.posY;
        double dZ = target.posZ + target.motionZ - this.cleric.posZ;
        float horizontal = MathHelper.sqrt((float) (dX * dX + dZ * dZ));
        projectile.shoot(dX, dY + (double) (horizontal * 0.2f), dZ, 0.75f, 8.0f);
        this.cleric.world.playSound(null, this.cleric.posX, this.cleric.posY, this.cleric.posZ,
                SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.NEUTRAL, 1.0f, 0.8f);
        this.cleric.world.spawnEntity(projectile);
        this.cleric.swingArm(EnumHand.MAIN_HAND);
        this.cleric.essence -= EntityCleric.HEAL_COST;
        this.healReadyAt = this.cleric.world.getTotalWorldTime() + HEAL_COOLDOWN;
        HelpfulVillagers.logger.info("[HV] Cleric: id={} heals {} (hp {}/{}) essence={}",
                this.cleric.getEntityId(), target.getClass().getSimpleName(),
                String.format("%.1f", target.getHealth()), String.format("%.1f", target.getMaxHealth()),
                this.cleric.essence);
    }

    /** Strips all negative potion effects with happy-villager particles and a chime. */
    private void cleanse(AbstractVillager target) {
        List<Potion> bad = new ArrayList<Potion>();
        for (PotionEffect effect : target.getActivePotionEffects()) {
            if (effect.getPotion().isBadEffect()) {
                bad.add(effect.getPotion());
            }
        }
        for (Potion potion : bad) {
            target.removePotionEffect(potion);
        }
        ((WorldServer) this.cleric.world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                target.posX, target.posY + 1.0, target.posZ, 12, 0.4, 0.6, 0.4, 0.05);
        this.cleric.world.playSound(null, target.posX, target.posY, target.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.6f, 1.4f);
        this.cleric.swingArm(EnumHand.MAIN_HAND);
        this.cleric.essence -= EntityCleric.CLEANSE_COST;
        this.cleanseReadyAt = this.cleric.world.getTotalWorldTime() + CLEANSE_COOLDOWN;
        HelpfulVillagers.logger.info("[HV] Cleric: id={} cleanses {} ({} effects) essence={}",
                this.cleric.getEntityId(), target.getClass().getSimpleName(), bad.size(), this.cleric.essence);
    }
}
