package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.spege.helpfulvillagers.entity.EntityFishHookCustom;
import com.spege.helpfulvillagers.entity.EntityFisherman;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.FishHookPacket;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ResourceCluster;

import net.minecraft.block.material.Material;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/** Fisherman gathering AI: finds water outside the village, casts a custom hook, harvests squid ink. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIFisherman extends EntityAIWorker {
    private EntityFisherman fisherman;
    private int searchLimit;
    private Random rand;

    public EntityAIFisherman(EntityFisherman fisherman) {
        super(fisherman);
        this.fisherman = fisherman;
        this.target = null;
        this.currentTime = 0;
        this.previousTime = 0;
        this.harvestTime = 20.0f;
        this.searchLimit = 20;
        this.rand = new Random();
    }

    @Override
    protected boolean gather() {
        if (this.fisherman.homeGuildHall == null) {
            return this.idle();
        }
        if (this.fisherman.insideHall()) {
            BlockPos exit = this.fisherman.homeGuildHall.entranceCoords;
            if (exit == null) {
                exit = AIHelper.getRandInsideCoords(this.fisherman);
            }
            this.fisherman.moveTo(exit, this.speed);
        } else if (this.fisherman.currentResource == null) {
            this.findWater();
        } else {
            int distX = AIHelper.findDistance((int) this.fisherman.posX, this.fisherman.currentResource.coords.getX());
            int distY = AIHelper.findDistance((int) this.fisherman.posY, this.fisherman.currentResource.coords.getY());
            int distZ = AIHelper.findDistance((int) this.fisherman.posZ, this.fisherman.currentResource.coords.getZ());
            this.target = this.fisherman.currentResource.coords;
            if (distX > 3 || distY > 3 || distZ > 3) {
                this.fisherman.moveTo(this.target, this.speed);
            } else {
                this.fish();
            }
        }
        return this.idle();
    }

    private void findWater() {
        if (this.target == null) {
            this.target = AIHelper.getRandOutsideCoords(this.fisherman, this.searchLimit);
        }
        if (this.target != null) {
            this.fisherman.moveTo(this.target, this.speed);
        }
        if (!AIHelper.isInRangeOfAnyVillage(this.fisherman.posX, this.fisherman.posY, this.fisherman.posZ)) {
            this.fisherman.updateBoxes();
            if (this.fisherman.searchBox != null
                    && this.fisherman.world.isMaterialInBB(this.fisherman.searchBox, Material.WATER)) {
                this.fisherman.currentResource = this.getNewResource();
                if (this.fisherman.currentResource != null) {
                    this.fisherman.currentResource.buildCluster(5);
                    this.fisherman.getNavigator().clearPath();
                }
            }
        }
        // Defensive null guard (1.7.10 dereferenced target unconditionally here and could NPE).
        if (this.target != null && Math.abs(this.fisherman.posX - (double) this.target.getX()) <= 5.0
                && Math.abs(this.fisherman.posZ - (double) this.target.getZ()) <= 5.0) {
            this.target = null;
        }
    }

    private ResourceCluster getNewResource() {
        ArrayList<BlockPos> boxCoords = this.fisherman.getValidCoords();
        double closestDist = Double.MAX_VALUE;
        ResourceCluster closestValidCluster = null;
        for (int i = 0; i < boxCoords.size(); ++i) {
            BlockPos currCoords = boxCoords.get(i);
            double dist = Math.sqrt(this.fisherman.getDistanceSq(currCoords.getX(), currCoords.getY(), currCoords.getZ()));
            if (!(dist < closestDist)) {
                continue;
            }
            ResourceCluster currentCluster = new ResourceCluster(this.fisherman.world, boxCoords.get(i));
            closestValidCluster = currentCluster;
            closestDist = dist;
        }
        return closestValidCluster;
    }

    private void fish() {
        if (this.fisherman.fishEntity == null && this.fisherman.currentResource.blockCluster.size() > 0) {
            if (this.previousTime <= 0) {
                this.previousTime = this.fisherman.ticksExisted;
            }
            if (this.previousTime > 0) {
                this.currentTime = this.fisherman.ticksExisted;
                if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                    int index = this.rand.nextInt(this.fisherman.currentResource.blockCluster.size());
                    BlockPos coords = this.fisherman.currentResource.blockCluster.get(index);
                    this.fisherman.fishEntity = new EntityFishHookCustom(this.fisherman.world, coords.getX(), coords.getY(), coords.getZ(), this.fisherman);
                    this.fisherman.swingArm(EnumHand.MAIN_HAND);
                    this.fisherman.world.spawnEntity(this.fisherman.fishEntity);
                    HelpfulVillagers.network.sendToAll(new FishHookPacket(this.fisherman.getEntityId(), false, coords.getX(), coords.getY(), coords.getZ()));
                    this.previousTime = 0;
                    this.currentTime = 0;
                }
            }
        }
        List<EntitySquid> squids = this.fisherman.world.getEntitiesWithinAABB(EntitySquid.class, this.fisherman.searchBox);
        for (EntitySquid squid : squids) {
            if (this.fisherman.harvestedSquids.contains(squid)) {
                continue;
            }
            this.fisherman.inventory.addItem(new ItemStack(Items.DYE, 1, 0));
            this.fisherman.harvestedSquids.add(squid);
        }
    }
}
