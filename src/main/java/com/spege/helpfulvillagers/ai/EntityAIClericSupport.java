package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntityCleric;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;

/**
 * Cleric support AI, three services in priority order:
 * <ol>
 *   <li><b>Heal</b> — villagers below 40% health get a real splash healing potion thrown at them
 *       (witch-style aim; Instant Health I ~3 HP + Regeneration ~2 HP). 2 essence, 15 s cooldown.</li>
 *   <li><b>Cleanse</b> — villagers under a negative status effect get it stripped up close
 *       (particles + chime). 3 essence, 30 s cooldown.</li>
 *   <li><b>Bless</b> — when the kill counter reaches a milestone (15/50/100 hostiles slain near
 *       the cleric) and his guild has an enchanting table, he walks to the guards and enchants
 *       their best unenchanted gear (1/2/3 items at power 5/15/30), also repairing it to full.</li>
 * </ol>
 */
@SuppressWarnings("null")
public class EntityAIClericSupport extends EntityAIBase {
    private static final int MODE_HEAL = 0;
    private static final int MODE_CLEANSE = 1;
    private static final int MODE_BLESS = 2;

    private static final int HEAL_COOLDOWN = 300;
    private static final int CLEANSE_COOLDOWN = 600;
    private static final float HEAL_THRESHOLD = 0.4f;
    private static final double HEAL_RANGE_SQ = 36.0;
    private static final double CLEANSE_RANGE_SQ = 9.0;
    private static final double BLESS_RANGE_SQ = 9.0;
    /** Radius around the cleric in which guards' gear is gathered for one blessing. */
    private static final double BLESS_GATHER_RANGE = 8.0;
    /** Retry delay after a milestone could not be performed (no candidates/facilities). */
    private static final int BLESS_RETRY = 200;
    /** 1-in-N tick chance to run the patient scan, like vanilla target tasks. */
    private static final int SCAN_CHANCE = 10;

    private final EntityCleric cleric;
    private final float speed;
    private AbstractVillager patient;
    private int mode;
    private long healReadyAt;
    private long cleanseReadyAt;
    private long blessRetryAt;
    private int repathDelay;

    /** A guard's enchantable item with its quality rank. */
    private static final class BlessCandidate {
        final AbstractVillager owner;
        final ItemStack stack;
        final float rank;

        BlessCandidate(AbstractVillager owner, ItemStack stack, float rank) {
            this.owner = owner;
            this.stack = stack;
            this.rank = rank;
        }
    }

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
        return this.findWork();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.cleric.currentActivity != EnumActivity.IDLE
                || this.patient == null || !this.patient.isEntityAlive()) {
            return false;
        }
        switch (this.mode) {
            case MODE_HEAL:
                return this.canHeal() && isWounded(this.patient);
            case MODE_CLEANSE:
                return this.canCleanse() && hasBadEffect(this.patient);
            default:
                return this.milestonePending();
        }
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
        switch (this.mode) {
            case MODE_HEAL: {
                if (distSq <= HEAL_RANGE_SQ && this.cleric.getEntitySenses().canSee(this.patient)) {
                    this.cleric.getNavigator().clearPath();
                    this.throwHealingPotion(this.patient);
                    return;
                }
                break;
            }
            case MODE_CLEANSE: {
                if (distSq <= CLEANSE_RANGE_SQ) {
                    this.cleric.getNavigator().clearPath();
                    this.cleanse(this.patient);
                    return;
                }
                break;
            }
            default: {
                if (distSq <= BLESS_RANGE_SQ) {
                    this.cleric.getNavigator().clearPath();
                    this.performBlessing();
                    return;
                }
                break;
            }
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

    private boolean milestonePending() {
        return this.cleric.killCounter >= EntityCleric.MILESTONE_KILLS[this.cleric.enchantTier]
                && this.cleric.world.getTotalWorldTime() >= this.blessRetryAt;
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

    private static boolean isGuard(AbstractVillager villager) {
        return villager instanceof EntitySoldier || villager instanceof EntityArcher;
    }

    /** Heal > cleanse > blessing; nearest patient first. Sets {@link #patient} and {@link #mode}. */
    private boolean findWork() {
        if (this.cleric.homeVillage.actualBounds == null) {
            return false;
        }
        boolean canHeal = this.canHeal();
        boolean canCleanse = this.canCleanse();
        List<AbstractVillager> villagers = this.cleric.world.getEntitiesWithinAABB(
                AbstractVillager.class, this.cleric.homeVillage.actualBounds.grow(8.0));
        AbstractVillager bestHeal = null;
        AbstractVillager bestCleanse = null;
        AbstractVillager nearestGuard = null;
        double distHeal = Double.MAX_VALUE;
        double distCleanse = Double.MAX_VALUE;
        double distGuard = Double.MAX_VALUE;
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
            if (isGuard(curr) && dist < distGuard) {
                distGuard = dist;
                nearestGuard = curr;
            }
        }
        if (bestHeal != null) {
            this.patient = bestHeal;
            this.mode = MODE_HEAL;
            return true;
        }
        if (bestCleanse != null) {
            this.patient = bestCleanse;
            this.mode = MODE_CLEANSE;
            return true;
        }
        if (nearestGuard != null && this.milestonePending()
                && this.cleric.homeGuildHall != null && this.cleric.homeGuildHall.hasClericFacilities()) {
            this.patient = nearestGuard;
            this.mode = MODE_BLESS;
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

    /**
     * Enchants (and fully repairs) the best unenchanted gear of guards around the cleric:
     * tier 0 = 1 held weapon at power 5, tier 1 = 2 items at 15, tier 2 = 3 items at 30, after
     * which the counter and tier reset.
     */
    private void performBlessing() {
        int tier = this.cleric.enchantTier;
        List<BlessCandidate> candidates = this.gatherCandidates(tier);
        if (candidates.isEmpty()) {
            // Nothing enchantable around right now — retry later, milestone is kept.
            this.blessRetryAt = this.cleric.world.getTotalWorldTime() + BLESS_RETRY;
            HelpfulVillagers.logger.info("[HV] Cleric: id={} blessing tier {} deferred (no unenchanted gear nearby)",
                    this.cleric.getEntityId(), tier);
            return;
        }
        Collections.sort(candidates, new Comparator<BlessCandidate>() {
            @Override
            public int compare(BlessCandidate a, BlessCandidate b) {
                return Float.compare(b.rank, a.rank);
            }
        });
        int count = Math.min(EntityCleric.MILESTONE_ITEMS[tier], candidates.size());
        Set<AbstractVillager> blessed = new HashSet<AbstractVillager>();
        for (int i = 0; i < count; ++i) {
            BlessCandidate candidate = candidates.get(i);
            EnchantmentHelper.addRandomEnchantment(this.cleric.getRNG(), candidate.stack,
                    EntityCleric.MILESTONE_POWER[tier], false);
            // The blessing also restores the item to full durability.
            candidate.stack.setItemDamage(0);
            blessed.add(candidate.owner);
        }
        for (AbstractVillager owner : blessed) {
            ((WorldServer) this.cleric.world).spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE,
                    owner.posX, owner.posY + 1.2, owner.posZ, 20, 0.5, 0.7, 0.5, 0.1);
            owner.inventory.syncInventory();
        }
        this.cleric.world.playSound(null, this.cleric.posX, this.cleric.posY, this.cleric.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 1.0f, 0.8f);
        this.cleric.swingArm(EnumHand.MAIN_HAND);
        HelpfulVillagers.logger.info("[HV] Cleric: id={} blessing tier {} -> {} items at power {} (kills={})",
                this.cleric.getEntityId(), tier, count, EntityCleric.MILESTONE_POWER[tier], this.cleric.killCounter);
        if (tier >= EntityCleric.MILESTONE_KILLS.length - 1) {
            this.cleric.killCounter = 0;
            this.cleric.enchantTier = 0;
        } else {
            ++this.cleric.enchantTier;
        }
    }

    /** Unenchanted gear of guards within {@link #BLESS_GATHER_RANGE} blocks; tier 0 = weapons only. */
    private List<BlessCandidate> gatherCandidates(int tier) {
        List<BlessCandidate> candidates = new ArrayList<BlessCandidate>();
        List<AbstractVillager> guards = this.cleric.world.getEntitiesWithinAABB(
                AbstractVillager.class, this.cleric.getEntityBoundingBox().grow(BLESS_GATHER_RANGE));
        for (AbstractVillager guard : guards) {
            if (!isGuard(guard) || !guard.isEntityAlive()) {
                continue;
            }
            int firstSlot = 27;                       // held weapon
            int lastSlot = tier == 0 ? 27 : 31;       // higher tiers include armor (28-31)
            for (int slot = firstSlot; slot <= lastSlot; ++slot) {
                ItemStack stack = guard.inventory.getStackInSlot(slot);
                if (stack.isEmpty() || stack.isItemEnchanted()) {
                    continue;
                }
                float rank;
                if (stack.getItem() instanceof ItemSword) {
                    rank = ((ItemSword) stack.getItem()).getAttackDamage();
                } else if (stack.getItem() instanceof ItemBow) {
                    rank = 5.0f;
                } else if (stack.getItem() instanceof ItemArmor) {
                    rank = ((ItemArmor) stack.getItem()).damageReduceAmount;
                } else {
                    continue;
                }
                candidates.add(new BlessCandidate(guard, stack, rank));
            }
        }
        return candidates;
    }
}
