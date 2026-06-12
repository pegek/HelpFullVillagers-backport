package com.spege.helpfulvillagers.ai;

import java.util.Random;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.DimensionType;

/**
 * Custom "move indoors at night / return to guild hall" AI. 1.12.2 migration: ChunkCoordinates ->
 * BlockPos; VillageDoorInfo inside position via {@link VillageDoorInfo#getInsideBlockPos()};
 * Vec3 -> Vec3d; nether check via dimension type.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIMoveIndoorsCustom extends EntityAIBase {
    private AbstractVillager entityObj;
    private VillageDoorInfo doorInfo;
    private int insidePosX = -1;
    private int insidePosZ = -1;
    protected BlockDoor targetDoor;
    private BlockPos destination;
    private Random gen;
    private float speed;

    public EntityAIMoveIndoorsCustom(AbstractVillager abstractEntity) {
        this.entityObj = abstractEntity;
        this.setMutexBits(1);
        this.gen = new Random();
        this.speed = 0.5f;
    }

    @Override
    public boolean shouldExecute() {
        if (this.entityObj.homeGuildHall != null && this.entityObj.currentActivity == EnumActivity.RETURN) {
            this.destination = new BlockPos(this.entityObj.homeGuildHall.doorCoords.getX(),
                    this.entityObj.homeGuildHall.doorCoords.getY(), this.entityObj.homeGuildHall.doorCoords.getZ());
            return !this.entityObj.nearHall();
        }
        if (!(this.entityObj.staysOutdoorsAtNight()
                || this.entityObj.world.isDaytime() && !this.entityObj.world.isRaining()
                || this.entityObj.world.provider.getDimensionType() == DimensionType.NETHER)) {
            if (this.entityObj.getRNG().nextInt(50) != 0) {
                return false;
            }
            if (this.insidePosX != -1
                    && this.entityObj.getDistanceSq(this.insidePosX, this.entityObj.posY, this.insidePosZ) < 4.0) {
                return false;
            }
            if (this.entityObj.homeVillage == null) {
                return false;
            }
            this.doorInfo = this.entityObj.homeVillage.findNearestDoorUnrestricted(
                    MathHelper.floor(this.entityObj.posX), MathHelper.floor(this.entityObj.posY),
                    MathHelper.floor(this.entityObj.posZ));
            return this.doorInfo != null;
        }
        return false;
    }

    @Override
    public boolean shouldContinueExecuting() {
        this.speed = !this.entityObj.homeVillage.isInsideVillage(this.entityObj.posX, this.entityObj.posY, this.entityObj.posZ)
                ? 0.75f
                : 0.5f;
        if (this.entityObj.homeGuildHall != null && this.entityObj.currentActivity == EnumActivity.RETURN) {
            if (this.entityObj.shouldReturn()) {
                this.entityObj.currentActivity = EnumActivity.IDLE;
                return false;
            }
            this.entityObj.moveTo(this.destination, this.speed);
            if (this.entityObj.nearHall()) {
                int distX = AIHelper.findDistance((int) this.entityObj.posX, this.destination.getX());
                int distZ = AIHelper.findDistance((int) this.entityObj.posZ, this.destination.getZ());
                if (distX < 1 || distZ < 1 || this.entityObj.getNavigator().noPath()) {
                    this.entityObj.currentActivity = EnumActivity.IDLE;
                    return false;
                }
                return true;
            }
            return true;
        }
        return !this.entityObj.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        this.insidePosX = -1;
        if (this.entityObj.homeGuildHall != null && this.entityObj.currentActivity == EnumActivity.RETURN) {
            int x = (int) this.entityObj.posX;
            int y = (int) this.entityObj.posY;
            int z = (int) this.entityObj.posZ;
            if (new BlockPos(x + 1, y, z).equals(this.entityObj.homeGuildHall.doorCoords)) {
                this.targetDoor = this.findUsableDoor(x + 1, y, z);
            } else if (new BlockPos(x - 1, y, z).equals(this.entityObj.homeGuildHall.doorCoords)) {
                this.targetDoor = this.findUsableDoor(x - 1, y, z);
            } else if (new BlockPos(x, y, z + 1).equals(this.entityObj.homeGuildHall.doorCoords)) {
                this.targetDoor = this.findUsableDoor(x, y, z + 1);
            } else if (new BlockPos(x, y, z - 1).equals(this.entityObj.homeGuildHall.doorCoords)) {
                this.targetDoor = this.findUsableDoor(x, y, z - 1);
            }
        } else {
            BlockPos inside = this.doorInfo.getInsideBlockPos();
            if (this.entityObj.getDistanceSq(inside.getX(), inside.getY(), inside.getZ()) > 256.0) {
                Vec3d vec3 = RandomPositionGenerator.findRandomTargetBlockTowards(this.entityObj, 14, 3,
                        new Vec3d((double) inside.getX() + 0.5, (double) inside.getY(), (double) inside.getZ() + 0.5));
                if (vec3 != null) {
                    this.entityObj.getNavigator().tryMoveToXYZ(vec3.x, vec3.y, vec3.z, this.speed);
                }
            } else {
                this.entityObj.getNavigator().tryMoveToXYZ((double) inside.getX() + 0.5, (double) inside.getY(),
                        (double) inside.getZ() + 0.5, this.speed);
            }
        }
    }

    @Override
    public void resetTask() {
        if (this.entityObj.homeGuildHall == null) {
            if (!this.entityObj.world.isRemote && this.entityObj.homeVillage != null) {
                this.doorInfo = this.entityObj.homeVillage.findNearestDoorUnrestricted(
                        MathHelper.floor(this.entityObj.posX), MathHelper.floor(this.entityObj.posY),
                        MathHelper.floor(this.entityObj.posZ));
                if (this.doorInfo != null) {
                    BlockPos inside = this.doorInfo.getInsideBlockPos();
                    this.insidePosX = inside.getX();
                    this.insidePosZ = inside.getZ();
                    this.doorInfo = null;
                }
            }
        } else {
            BlockPos center = this.entityObj.homeVillage.getActualCenter();
            this.entityObj.getNavigator().tryMoveToXYZ((double) center.getX(), (double) center.getY(),
                    (double) center.getZ(), this.speed);
        }
    }

    private BlockDoor findUsableDoor(int par1, int par2, int par3) {
        Block l = this.entityObj.world.getBlockState(new BlockPos(par1, par2, par3)).getBlock();
        return l != Blocks.OAK_DOOR ? null : (BlockDoor) l;
    }
}
