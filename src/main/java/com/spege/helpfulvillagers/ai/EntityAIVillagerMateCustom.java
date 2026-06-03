package com.spege.helpfulvillagers.ai;

import java.util.Random;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.PlayerMessagePacket;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Custom mating AI: two adult mod villagers breed when the village has spare guild-hall capacity,
 * spawning 1-3 children. 1.12.2 migration: bounding-box grow(), findNearestEntityWithinAABB,
 * setMating/createChild, ChunkCoordinates-free bed scan via BlockPos.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIVillagerMateCustom extends EntityAIBase {
    private AbstractVillager villagerObj;
    private AbstractVillager mate;
    private World worldObj;
    private int matingTimeout;
    private Random gen = new Random();
    private HelpfulVillage villageObj;

    public EntityAIVillagerMateCustom(AbstractVillager villager) {
        this.villagerObj = villager;
        this.worldObj = villager.world;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!this.villagerObj.shouldReproduce()) {
            return false;
        }
        if (this.villagerObj.getGrowingAge() != 0) {
            return false;
        }
        this.villageObj = this.villagerObj.homeVillage;
        if (this.villageObj == null || this.worldObj.isRemote) {
            return false;
        }
        if (!this.checkSufficientHallssPresentForNewVillager()) {
            return false;
        }
        Entity entity = this.worldObj.findNearestEntityWithinAABB(AbstractVillager.class,
                this.villagerObj.getEntityBoundingBox().grow(8.0, 3.0, 8.0), this.villagerObj);
        if (entity == null || !entity.isEntityAlive() || ((AbstractVillager) entity).isChild()) {
            return false;
        }
        this.mate = (AbstractVillager) entity;
        return this.mate.getGrowingAge() == 0 && (this.mate.leader == null || this.mate.leader == this.villagerObj);
    }

    @Override
    public void startExecuting() {
        this.matingTimeout = this.gen.nextInt(50) + 300;
        this.villagerObj.setMating(true);
        this.mate.setMating(true);
    }

    @Override
    public void resetTask() {
        this.villagerObj.setMating(false);
        this.mate.setMating(false);
        this.villageObj = null;
        this.mate = null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.villagerObj.shouldReproduce() && this.matingTimeout >= 0
                && this.checkSufficientHallssPresentForNewVillager() && this.villagerObj.getGrowingAge() == 0
                && this.mate != null && this.mate.isEntityAlive();
    }

    @Override
    public void updateTask() {
        --this.matingTimeout;
        this.villagerObj.getLookHelper().setLookPositionWithEntity(this.mate, 10.0f, 30.0f);
        if (this.villagerObj.getDistanceSq(this.mate) > 2.25) {
            this.villagerObj.getNavigator().tryMoveToEntityLiving(this.mate, 0.25);
        } else if (this.matingTimeout == 0 && this.mate.isMating()) {
            this.giveBirth();
        }
        if (this.villagerObj.getRNG().nextInt(35) == 0) {
            this.worldObj.setEntityState(this.villagerObj, (byte) 12);
        }
    }

    private boolean checkSufficientHallssPresentForNewVillager() {
        int i = this.villageObj.guildHallList != null && this.villageObj.guildHallList.size() <= 0
                ? 3
                : this.villageObj.guildHallList.size() * 2 + 1;
        return this.villageObj.getPopulation() < i;
    }

    private void giveBirth() {
        String message;
        this.mate.setGrowingAge(6000);
        this.villagerObj.setGrowingAge(6000);
        if (!this.villagerObj.shouldReproduce()) {
            return;
        }
        int children = 1;
        int prob = this.bedCheck() ? this.gen.nextInt(100) : this.gen.nextInt(1000);
        if (prob <= 20) {
            ++children;
            if (prob <= 10) {
                ++children;
            }
        }
        for (int i = 0; i < children; ++i) {
            EntityVillager entityvillager = this.villagerObj.createChild(this.mate);
            entityvillager.setGrowingAge(-24000);
            entityvillager.setLocationAndAngles(this.villagerObj.posX, this.villagerObj.posY, this.villagerObj.posZ, 0.0f, 0.0f);
            this.worldObj.spawnEntity(entityvillager);
            this.worldObj.setEntityState(entityvillager, (byte) 12);
        }
        switch (children) {
            case 1:
                message = "A villager was born";
                break;
            case 2:
                message = "Twin villagers were born";
                break;
            case 3:
                message = "Triplet villagers were born";
                break;
            default:
                message = "A villager was born";
        }
        HelpfulVillagers.network.sendToAll(new PlayerMessagePacket(message, EnumMessage.BIRTH, this.villagerObj.getEntityId()));
    }

    private boolean bedCheck() {
        AxisAlignedBB box = this.villagerObj.getEntityBoundingBox().grow(3.0, 3.0, 3.0);
        int i = (int) box.minX;
        while ((double) i < box.maxX) {
            int j = (int) box.minY;
            while ((double) j < box.maxY) {
                int k = (int) box.minZ;
                while ((double) k < box.maxZ) {
                    Block block = this.villagerObj.world.getBlockState(new BlockPos(i, j, k)).getBlock();
                    if (block instanceof BlockBed) {
                        return true;
                    }
                    ++k;
                }
                ++j;
            }
            ++i;
        }
        return false;
    }
}
