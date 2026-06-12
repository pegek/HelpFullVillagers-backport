package com.spege.helpfulvillagers.ai;

import java.util.List;
import java.util.function.Predicate;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Target selection for village guards (soldier/archer), registered in {@code targetTasks} so that
 * combat movement/attacking lives in separate attack tasks (vanilla-style split).
 *
 * <p>Selection order: revenge attacker (if hostile), then the nearest {@link IMob} within the
 * village bounds plus {@link HelpfulVillage#AGGRESSOR_SEARCH_MARGIN}, preferring (1) visible
 * targets matching the optional priority predicate (e.g. creepers for archers), then (2) any
 * visible target, then (3) the nearest unseen one.
 *
 * <p>Leash: the target is dropped once it strays more than {@value #LEASH_MARGIN} blocks outside
 * the village bounds, or when the guard cannot see nor path to it for a few seconds — guards no
 * longer chase a single mob across the world.
 */
@SuppressWarnings("null")
public class EntityAIVillageGuardTarget extends EntityAITarget {
    /** Blocks beyond actualBounds before a pursued target is abandoned. */
    private static final int LEASH_MARGIN = 16;
    /** Ticks without path or progress toward an unseen target before giving up. */
    private static final int STUCK_GIVE_UP_TICKS = 100;
    /** 1-in-N tick chance to run the (relatively expensive) area scan, like vanilla. */
    private static final int SCAN_CHANCE = 10;

    private final AbstractVillager guard;
    private final Predicate<EntityLivingBase> priorityTarget;
    /** Hostiles failing this predicate are never targeted (e.g. creepers for melee guards). */
    private final Predicate<EntityLivingBase> targetFilter;
    private EntityLivingBase candidate;
    private int stuckTicks;
    private double lastDistanceSq;

    public EntityAIVillageGuardTarget(AbstractVillager guard) {
        this(guard, null, null);
    }

    public EntityAIVillageGuardTarget(AbstractVillager guard, Predicate<EntityLivingBase> priorityTarget,
            Predicate<EntityLivingBase> targetFilter) {
        super(guard, false, false);
        this.guard = guard;
        this.priorityTarget = priorityTarget;
        this.targetFilter = targetFilter;
        this.setMutexBits(1);
    }

    private boolean passesFilter(EntityLivingBase target) {
        return this.targetFilter == null || this.targetFilter.test(target);
    }

    @Override
    public boolean shouldExecute() {
        if (this.guard.world.isRemote || this.guard.isChild() || !this.isAvailableForCombat()) {
            return false;
        }
        EntityLivingBase revenge = this.guard.getRevengeTarget();
        if (revenge != null && revenge.isEntityAlive() && revenge instanceof IMob && this.passesFilter(revenge)) {
            this.candidate = revenge;
            return true;
        }
        if (this.guard.getRNG().nextInt(SCAN_CHANCE) != 0) {
            return false;
        }
        this.candidate = this.findNearestAggressor();
        return this.candidate != null;
    }

    @Override
    public void startExecuting() {
        this.guard.setAttackTarget(this.candidate);
        this.stuckTicks = 0;
        this.lastDistanceSq = Double.MAX_VALUE;
        HelpfulVillagers.logger.info("[HV] GuardTarget: {} id={} engages {} dist={}",
                this.guard.getClass().getSimpleName(), this.guard.getEntityId(),
                this.candidate.getName(), String.format("%.1f", this.guard.getDistance(this.candidate)));
        super.startExecuting();
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = this.guard.getAttackTarget();
        if (target == null || !target.isEntityAlive() || !this.isAvailableForCombat()
                || !this.passesFilter(target)) {
            // Filter re-check covers state changes mid-fight, e.g. an archer running out of
            // arrows drops a creeper target instead of falling back to melee against it.
            return false;
        }
        HelpfulVillage village = this.guard.homeVillage;
        if (village == null || village.actualBounds == null) {
            return false;
        }
        // Leash: stop pursuing once the target is well outside the village.
        if (!village.actualBounds.grow(LEASH_MARGIN).intersects(target.getEntityBoundingBox())) {
            HelpfulVillagers.logger.info("[HV] GuardTarget: {} id={} leashes off {} (left village area)",
                    this.guard.getClass().getSimpleName(), this.guard.getEntityId(), target.getName());
            return false;
        }
        // Give up on unseen targets we make no progress toward (unreachable, e.g. behind walls).
        // Visible targets are exempt: an archer legitimately stands still while shooting.
        double distanceSq = this.guard.getDistanceSq(target);
        if (!this.guard.getEntitySenses().canSee(target) && this.guard.getNavigator().noPath()
                && distanceSq >= this.lastDistanceSq - 1.0) {
            if (++this.stuckTicks > STUCK_GIVE_UP_TICKS) {
                HelpfulVillagers.logger.info("[HV] GuardTarget: {} id={} gives up on {} (unseen, no path progress)",
                        this.guard.getClass().getSimpleName(), this.guard.getEntityId(), target.getName());
                return false;
            }
        } else {
            this.stuckTicks = 0;
        }
        this.lastDistanceSq = distanceSq;
        return true;
    }

    @Override
    public void resetTask() {
        this.candidate = null;
        this.stuckTicks = 0;
        super.resetTask();
    }

    /** Combat is suspended while following a leader or while the resupply/return flow runs. */
    private boolean isAvailableForCombat() {
        return this.guard.currentActivity != EnumActivity.FOLLOW
                && this.guard.currentActivity != EnumActivity.RETURN
                && this.guard.currentActivity != EnumActivity.STORE;
    }

    private EntityLivingBase findNearestAggressor() {
        HelpfulVillage village = this.guard.homeVillage;
        if (village == null || village.actualBounds == null) {
            return null;
        }
        AxisAlignedBB bounds = village.actualBounds.grow(HelpfulVillage.AGGRESSOR_SEARCH_MARGIN);
        // IMob is an interface (not an Entity subtype), so query living entities and filter.
        List<EntityLivingBase> entities = this.guard.world.getEntitiesWithinAABB(EntityLivingBase.class, bounds);
        EntityLivingBase bestPriority = null;
        EntityLivingBase bestVisible = null;
        EntityLivingBase bestAny = null;
        double distPriority = Double.MAX_VALUE;
        double distVisible = Double.MAX_VALUE;
        double distAny = Double.MAX_VALUE;
        for (EntityLivingBase curr : entities) {
            if (!(curr instanceof IMob) || !curr.isEntityAlive() || !this.passesFilter(curr)) {
                continue;
            }
            double dist = this.guard.getDistanceSq(curr);
            if (this.guard.getEntitySenses().canSee(curr)) {
                if (this.priorityTarget != null && this.priorityTarget.test(curr) && dist < distPriority) {
                    distPriority = dist;
                    bestPriority = curr;
                }
                if (dist < distVisible) {
                    distVisible = dist;
                    bestVisible = curr;
                }
            }
            if (dist < distAny) {
                distAny = dist;
                bestAny = curr;
            }
        }
        if (bestPriority != null) {
            return bestPriority;
        }
        if (bestVisible != null) {
            return bestVisible;
        }
        return bestAny;
    }
}
