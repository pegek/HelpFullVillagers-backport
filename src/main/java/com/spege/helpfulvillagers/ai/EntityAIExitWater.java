package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Drives a villager to the nearest dry standable spot whenever it is in water.
 *
 * <p>{@link net.minecraft.entity.ai.EntityAISwimming} only keeps the villager afloat — it does not
 * get it back to land, so a villager that wanders/falls into a pond tends to bob in place
 * indefinitely. This task runs at high priority (mutex 1) so it pre-empts the profession work AIs
 * while the villager is wet, paths it to the closest dry ground, and releases control once it is out.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIExitWater extends EntityAIBase {

    /** Horizontal half-extent of the land search around the villager. */
    private static final int SCAN = 8;

    private final AbstractVillager villager;
    private BlockPos landTarget;

    public EntityAIExitWater(AbstractVillager villager) {
        this.villager = villager;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!this.villager.isInWater()) {
            return false;
        }
        this.landTarget = this.findNearestLand();
        return this.landTarget != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.villager.isInWater()
                && this.landTarget != null
                && !this.villager.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        this.pathToLand();
    }

    @Override
    public void updateTask() {
        // Re-issue the path if the navigator gave up but we're still wet.
        if (this.villager.getNavigator().noPath()) {
            this.pathToLand();
        }
    }

    private void pathToLand() {
        if (this.landTarget != null) {
            this.villager.getNavigator().tryMoveToXYZ(
                    this.landTarget.getX() + 0.5, this.landTarget.getY(),
                    this.landTarget.getZ() + 0.5, 1.0);
        }
    }

    /** Nearest dry, standable position around the villager, or null if none in range. */
    private BlockPos findNearestLand() {
        World world = this.villager.world;
        BlockPos origin = new BlockPos(this.villager);
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int x = origin.getX() + dx;
                    int y = origin.getY() + dy;
                    int z = origin.getZ() + dz;
                    p.setPos(x, y, z);
                    if (!world.isBlockLoaded(p)) {
                        continue;
                    }
                    if (!this.isDryStand(world, p)) {
                        continue;
                    }
                    double distSq = origin.distanceSq(x, y, z);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return best;
    }

    /** Solid, non-liquid block below; passable, non-liquid feet and head space. */
    private boolean isDryStand(World world, BlockPos pos) {
        IBlockState below = world.getBlockState(pos.down());
        if (below.getMaterial().isLiquid() || !below.getMaterial().isSolid()) {
            return false;
        }
        return this.passable(world, pos) && this.passable(world, pos.up());
    }

    private boolean passable(World world, BlockPos pos) {
        Material m = world.getBlockState(pos).getMaterial();
        return !m.isLiquid() && !m.blocksMovement();
    }
}
