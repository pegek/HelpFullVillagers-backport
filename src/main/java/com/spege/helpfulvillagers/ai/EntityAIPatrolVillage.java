package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.block.BlockDoor;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;

/**
 * Village patrol for guards: when there is no threat, the guard walks a round of the village's
 * door positions (nearest-neighbour order, optionally including the guild hall door), pausing at
 * each to look around, instead of standing in one spot. Interrupts instantly when the target task
 * assigns an attack target (the higher-priority combat tasks then take over).
 *
 * <p>Waypoints that cannot be reached within {@link #WAYPOINT_TIMEOUT} ticks are skipped, so
 * difficult terrain cannot wedge the patrol. Generic over {@link AbstractVillager} for future
 * reuse by other professions.
 */
@SuppressWarnings("null")
public class EntityAIPatrolVillage extends EntityAIBase {
    private static final int MAX_WAYPOINTS = 16;
    /** Ticks allowed per waypoint before it is skipped (difficult terrain). */
    private static final int WAYPOINT_TIMEOUT = 200;
    private static final double WAYPOINT_REACHED_SQ = 4.0;
    /** 1-in-N tick chance to start a patrol while idle (~6 s on average). */
    private static final int START_CHANCE = 120;

    private final AbstractVillager guard;
    private final float speed;
    private final ArrayList<BlockPos> route = new ArrayList<BlockPos>();
    private int routeIndex;
    private int waypointTicks;
    private int pauseTicks;
    private int repathDelay;

    public EntityAIPatrolVillage(AbstractVillager guard) {
        this.guard = guard;
        this.speed = 0.5f;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (this.guard.world.isRemote || this.guard.isChild()) {
            return false;
        }
        if (this.guard.currentActivity != EnumActivity.IDLE || this.guard.getAttackTarget() != null
                || !this.guard.hasTool) {
            return false;
        }
        if (this.guard.homeVillage == null || this.guard.homeVillage.villageDoors.isEmpty()) {
            return false;
        }
        if (this.guard.getRNG().nextInt(START_CHANCE) != 0) {
            return false;
        }
        this.buildRoute();
        return !this.route.isEmpty();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.guard.currentActivity == EnumActivity.IDLE
                && this.guard.getAttackTarget() == null
                && this.routeIndex < this.route.size();
    }

    @Override
    public void startExecuting() {
        this.routeIndex = 0;
        this.waypointTicks = 0;
        this.pauseTicks = 0;
        this.repathDelay = 0;
        HelpfulVillagers.logger.info("[HV] Patrol: {} id={} starts round with {} waypoints",
                this.guard.getClass().getSimpleName(), this.guard.getEntityId(), this.route.size());
    }

    @Override
    public void resetTask() {
        this.route.clear();
        this.routeIndex = 0;
        this.guard.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        BlockPos waypoint = this.route.get(this.routeIndex);
        if (this.pauseTicks > 0) {
            // Look-around pause at the waypoint: glance at a random nearby spot every second or so.
            if (this.pauseTicks % 20 == 0) {
                int dx = this.guard.getRNG().nextInt(17) - 8;
                int dz = this.guard.getRNG().nextInt(17) - 8;
                this.guard.getLookHelper().setLookPosition(
                        this.guard.posX + dx, this.guard.posY + this.guard.getEyeHeight(),
                        this.guard.posZ + dz, 30.0f, 30.0f);
            }
            if (--this.pauseTicks <= 0) {
                this.advance();
            }
            return;
        }
        if (++this.waypointTicks > WAYPOINT_TIMEOUT) {
            HelpfulVillagers.logger.info("[HV] Patrol: {} id={} skips unreachable waypoint {}",
                    this.guard.getClass().getSimpleName(), this.guard.getEntityId(), waypoint);
            this.advance();
            return;
        }
        if (this.isAtWaypoint(waypoint)) {
            this.guard.getNavigator().clearPath();
            this.pauseTicks = 60 + this.guard.getRNG().nextInt(41);
        } else if (--this.repathDelay <= 0) {
            this.repathDelay = 20;
            this.guard.moveTo(waypoint, this.speed);
        }
    }

    /**
     * Horizontal-distance arrival check: door waypoints can sit 1-2 blocks above the guard's feet
     * (stairs, porches), and a strict 3D radius made such stops register as "unreachable" forever.
     */
    private boolean isAtWaypoint(BlockPos waypoint) {
        double dx = this.guard.posX - (waypoint.getX() + 0.5);
        double dz = this.guard.posZ - (waypoint.getZ() + 0.5);
        double dy = Math.abs(this.guard.posY - waypoint.getY());
        return dx * dx + dz * dz <= WAYPOINT_REACHED_SQ && dy <= 2.5;
    }

    private void advance() {
        ++this.routeIndex;
        this.waypointTicks = 0;
        this.pauseTicks = 0;
        this.repathDelay = 0;
    }

    /**
     * Builds a patrol route over the village doors (sampled down to {@link #MAX_WAYPOINTS}),
     * ordered greedily by nearest neighbour from the guard's position; the guild hall door is
     * included when known.
     */
    private void buildRoute() {
        this.route.clear();
        // villageDoors contains both door halves (and stacked detections); patrol only the bottom
        // halves — upper halves float 1-2 blocks up and were endlessly skipped as unreachable.
        ArrayList<BlockPos> remaining = new ArrayList<BlockPos>();
        for (BlockPos door : this.guard.homeVillage.villageDoors) {
            if (!(this.guard.world.getBlockState(door.down()).getBlock() instanceof BlockDoor)) {
                remaining.add(door);
            }
        }
        if (remaining.size() > MAX_WAYPOINTS) {
            ArrayList<BlockPos> sampled = new ArrayList<BlockPos>(MAX_WAYPOINTS);
            double step = (double) remaining.size() / MAX_WAYPOINTS;
            for (int i = 0; i < MAX_WAYPOINTS; ++i) {
                sampled.add(remaining.get((int) (i * step)));
            }
            remaining = sampled;
        }
        if (this.guard.homeGuildHall != null && this.guard.homeGuildHall.doorCoords != null) {
            remaining.add(this.guard.homeGuildHall.doorCoords);
        }
        double curX = this.guard.posX;
        double curY = this.guard.posY;
        double curZ = this.guard.posZ;
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); ++i) {
                BlockPos pos = remaining.get(i);
                double dist = pos.distanceSqToCenter(curX, curY, curZ);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = i;
                }
            }
            BlockPos next = remaining.remove(best);
            this.route.add(next);
            curX = next.getX();
            curY = next.getY();
            curZ = next.getZ();
        }
    }
}
