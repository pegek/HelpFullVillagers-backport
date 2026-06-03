package com.spege.helpfulvillagers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Custom fishing hook cast by a {@link EntityFisherman}.
 *
 * <p><b>STUB (step 10 / fish hook is the riskiest part of the port).</b> 1.12.2 rewrote vanilla
 * {@code EntityFishHook} entirely, so this is a from-scratch custom entity. For now this provides only
 * the constructors and Entity contract needed for CORE to compile and for the fisherman/FishHookPacket
 * to spawn/remove it; the bobber motion, water detection, catch logic and renderer are TODO and will be
 * filled in during the dedicated fish-hook step. Until then it just sits where it is spawned.
 */
public class EntityFishHookCustom extends Entity {
    public EntityFisherman owner;
    public int x;
    public int y;
    public int z;

    public EntityFishHookCustom(World world) {
        super(world);
        this.setSize(0.25f, 0.25f);
        this.ignoreFrustumCheck = true;
    }

    public EntityFishHookCustom(World world, double x, double y, double z, EntityFisherman fisherman) {
        this(world);
        this.owner = fisherman;
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
        this.setLocationAndAngles(x + 0.5, y + 0.5, z + 0.5, 0.0f, 0.0f);
    }

    public EntityFishHookCustom(World world, EntityFisherman fisherman) {
        this(world);
        this.owner = fisherman;
    }

    @Override
    protected void entityInit() {
        // no synced data yet (TODO step 10)
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        // TODO step 10
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        // TODO step 10
    }
}
