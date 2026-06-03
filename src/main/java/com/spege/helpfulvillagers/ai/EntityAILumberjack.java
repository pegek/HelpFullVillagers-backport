package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;

import com.spege.helpfulvillagers.entity.EntityLumberjack;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ResourceCluster;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/** Lumberjack gathering AI: finds a tree cluster outside the village, walks to it and chops it down. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAILumberjack extends EntityAIWorker {
    private EntityLumberjack lumberjack;
    private int searchLimit;

    public EntityAILumberjack(EntityLumberjack lumberjack) {
        super(lumberjack);
        this.lumberjack = lumberjack;
        this.currentTime = 0;
        this.previousTime = 0;
        this.harvestTime = 0.0f;
        this.searchLimit = 20;
    }

    @Override
    protected boolean gather() {
        if (this.lumberjack.homeGuildHall == null) {
            return this.idle();
        }
        if (this.lumberjack.insideHall()) {
            BlockPos exit = this.lumberjack.homeGuildHall.entranceCoords;
            if (exit == null) {
                exit = AIHelper.getRandInsideCoords(this.lumberjack);
            }
            this.lumberjack.moveTo(exit, this.speed);
        } else if (this.lumberjack.currentResource == null) {
            this.findTree();
        } else {
            int distX = AIHelper.findDistance((int) this.lumberjack.posX, this.lumberjack.currentResource.coords.getX());
            int distZ = AIHelper.findDistance((int) this.lumberjack.posZ, this.lumberjack.currentResource.coords.getZ());
            if (distX > 5 || distZ > 5) {
                this.moveToTree();
            } else {
                this.chopTree();
            }
        }
        return this.idle();
    }

    private void findTree() {
        if (this.target == null) {
            this.target = AIHelper.getRandOutsideCoords(this.lumberjack, this.searchLimit);
        }
        if (this.target != null) {
            this.lumberjack.moveTo(this.target, this.speed);
        }
        if (this.lumberjack.searchBox != null
                && this.lumberjack.world.isMaterialInBB(this.lumberjack.searchBox, Material.WOOD)
                && !AIHelper.isInRangeOfAnyVillage(this.lumberjack.posX, this.lumberjack.posY, this.lumberjack.posZ)) {
            this.lumberjack.currentResource = this.getNewResource();
            if (this.lumberjack.currentResource != null) {
                this.searchLimit = 20;
                this.lumberjack.foundTree = true;
                this.lumberjack.getNavigator().clearPath();
            }
        }
        // Defensive null guard (1.7.10 dereferenced target unconditionally here and could NPE).
        if (this.target != null && Math.abs(this.lumberjack.posX - (double) this.target.getX()) <= 5.0
                && Math.abs(this.lumberjack.posZ - (double) this.target.getZ()) <= 5.0) {
            this.target = null;
            this.searchLimit += 10;
        }
    }

    private void moveToTree() {
        this.target = this.lumberjack.currentResource.lowestCoords;
        this.lumberjack.moveTo(this.target, this.speed);
    }

    private ResourceCluster getNewResource() {
        ArrayList<BlockPos> boxCoords = this.lumberjack.getValidCoords();
        double closestDist = Double.MAX_VALUE;
        ResourceCluster closestValidCluster = null;
        for (int i = 0; i < boxCoords.size(); ++i) {
            BlockPos currCoords = boxCoords.get(i);
            double dist = Math.sqrt(this.lumberjack.getDistanceSq(currCoords.getX(), currCoords.getY(), currCoords.getZ()));
            if (!(dist < closestDist)) {
                continue;
            }
            ResourceCluster currentCluster = new ResourceCluster(this.lumberjack.world, boxCoords.get(i));
            ArrayList<Block> sideBlocks = currentCluster.getAdjacent();
            for (int j = 0; j < sideBlocks.size(); ++j) {
                Block currentBlock = sideBlocks.get(j);
                if (!currentBlock.equals(Blocks.COBBLESTONE) && !currentBlock.equals(Blocks.PLANKS)
                        && !currentBlock.equals(Blocks.FARMLAND) && !currentBlock.equals(Blocks.OAK_FENCE)
                        && !currentBlock.equals(Blocks.OAK_FENCE_GATE) && !currentBlock.equals(Blocks.OAK_DOOR)
                        && !currentBlock.equals(Blocks.IRON_DOOR) && !currentBlock.equals(Blocks.BOOKSHELF)
                        && !currentBlock.equals(Blocks.CHEST) && !currentBlock.equals(Blocks.CRAFTING_TABLE)
                        && !(currentBlock instanceof BlockStairs)) {
                    continue;
                }
                currentCluster = null;
                break;
            }
            if (currentCluster == null) {
                continue;
            }
            closestValidCluster = currentCluster;
            closestDist = dist;
        }
        return closestValidCluster;
    }

    private void chopTree() {
        this.lumberjack.moveTo(this.lumberjack.currentResource.lowestCoords, this.speed);
        boolean shouldSwing = false;
        if (!this.lumberjack.currentResource.builtFlag) {
            this.lumberjack.currentResource.buildCluster();
            this.lumberjack.currentResource.builtFlag = true;
        }
        if (this.lumberjack.getNavigator().noPath()) {
            BlockPos low = this.lumberjack.currentResource.lowestCoords;
            this.lumberjack.getLookHelper().setLookPosition((double) low.getX(), (double) low.getY(), (double) low.getZ(), 10.0f, 10.0f);
            shouldSwing = true;
            if (this.previousTime <= 0) {
                this.previousTime = this.lumberjack.ticksExisted;
                this.harvestTime = 60.0f / this.lumberjack.getCurrentItem().getItem()
                        .getDestroySpeed(this.lumberjack.getCurrentItem(), this.lumberjack.currentResource.startBlock.getDefaultState());
            }
        } else {
            shouldSwing = false;
        }
        if (this.previousTime > 0) {
            this.currentTime = this.lumberjack.ticksExisted;
            if (!this.lumberjack.currentResource.blockCluster.isEmpty()) {
                if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                    this.previousTime = this.currentTime;
                    this.harvestTime = 60.0f / this.lumberjack.getCurrentItem().getItem()
                            .getDestroySpeed(this.lumberjack.getCurrentItem(), this.lumberjack.currentResource.startBlock.getDefaultState());
                    BlockPos currentCoords = this.lumberjack.currentResource.blockCluster.get(0);
                    Block currentBlock = this.lumberjack.world.getBlockState(currentCoords).getBlock();
                    if (Block.getIdFromBlock(currentBlock) == Block.getIdFromBlock(this.lumberjack.currentResource.startBlock)) {
                        this.lumberjack.currentResource.blockCluster.remove(0);
                        AIHelper.breakBlock(currentCoords, this.lumberjack);
                    }
                }
            } else {
                this.lumberjack.lastResource = this.lumberjack.currentResource;
                this.lumberjack.currentResource = null;
                this.target = null;
                this.previousTime = 0;
                this.currentTime = 0;
            }
        }
        if (shouldSwing) {
            this.lumberjack.swingArm(EnumHand.MAIN_HAND);
        } else {
            this.previousTime = 0;
        }
    }
}
