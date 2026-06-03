package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;

import com.spege.helpfulvillagers.entity.EntityFarmer;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ResourceCluster;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockStem;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;

/**
 * Farmer gathering AI: finds a tilled-soil cluster inside the village, harvests mature crops and
 * replants seeds.
 *
 * <p>1.12.2 migration notes:
 * <ul>
 *   <li>Crop maturity now via {@link BlockCrops#isMaxAge} / {@link BlockNetherWart#AGE} rather than
 *       raw metadata thresholds.</li>
 *   <li>{@code BlockStem.getMeta(...)} (which pointed at the grown fruit) has no 1.12.2 analogue, so
 *       melon/pumpkin harvesting scans the stem's horizontal neighbours for the fruit block instead.
 *       VERIFY in-game.</li>
 *   <li>{@code IPlantable.getPlant} now returns an {@link IBlockState}; planting uses setBlockState.</li>
 * </ul>
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIFarmer extends EntityAIWorker {
    private EntityFarmer farmer;
    private ArrayList<BlockPos> farmCoords = new ArrayList<BlockPos>();

    public EntityAIFarmer(EntityFarmer farmer) {
        super(farmer);
        this.farmer = farmer;
        this.target = null;
    }

    @Override
    protected boolean gather() {
        if (this.farmer.homeGuildHall == null) {
            return this.idle();
        }
        if (this.farmer.insideHall()) {
            BlockPos exit = this.farmer.homeGuildHall.entranceCoords;
            if (exit == null) {
                exit = AIHelper.getRandInsideCoords(this.farmer);
            }
            this.farmer.moveTo(exit, this.speed);
        } else if (this.farmer.currentResource == null) {
            this.findFarm();
        } else {
            this.target = this.farmCoords.get(0);
            int distX = AIHelper.findDistance((int) this.farmer.posX, this.target.getX());
            int distY = AIHelper.findDistance((int) this.farmer.posY, this.target.getY());
            int distZ = AIHelper.findDistance((int) this.farmer.posZ, this.target.getZ());
            if (distX > 3 || distY > 3 || distZ > 3) {
                this.farmer.moveTo(this.target, this.speed);
            } else {
                this.harvestCrops();
            }
        }
        return this.idle();
    }

    private void findFarm() {
        if (this.target == null) {
            this.target = AIHelper.getRandInsideCoords(this.farmer);
        }
        if (this.target != null) {
            this.farmer.moveTo(this.target, this.speed);
        }
        this.farmer.updateBoxes();
        if (this.farmer.searchBox != null && this.farmer.world.isMaterialInBB(this.farmer.searchBox, Material.GROUND)) {
            this.farmer.currentResource = this.getNewResource();
            if (this.farmer.currentResource != null) {
                this.farmCoords.addAll(this.farmer.currentResource.blockCluster);
                this.farmer.getNavigator().clearPath();
            }
        }
        // Defensive null guard (1.7.10 dereferenced target unconditionally here and could NPE).
        if (this.target != null && Math.abs(this.farmer.posX - (double) this.target.getX()) <= 5.0
                && Math.abs(this.farmer.posZ - (double) this.target.getZ()) <= 5.0) {
            this.target = null;
        }
    }

    private ResourceCluster getNewResource() {
        ArrayList<BlockPos> boxCoords = this.farmer.getValidCoords();
        double closestDist = Double.MAX_VALUE;
        ResourceCluster closestValidCluster = null;
        for (int i = 0; i < boxCoords.size(); ++i) {
            BlockPos currCoords = boxCoords.get(i);
            ResourceCluster currentCluster = new ResourceCluster(this.farmer.world, currCoords);
            currentCluster.buildCluster();
            boolean visited = false;
            if (this.farmer.visitedFarms != null) {
                for (int j = 0; j < this.farmer.visitedFarms.size(); ++j) {
                    ResourceCluster farm = this.farmer.visitedFarms.get(j);
                    if (!farm.matchesCluster(currentCluster)) {
                        continue;
                    }
                    visited = true;
                    break;
                }
            }
            double dist = Math.sqrt(this.farmer.getDistanceSq(currCoords.getX(), currCoords.getY(), currCoords.getZ()));
            if (visited || !(dist < closestDist)) {
                continue;
            }
            closestDist = dist;
            closestValidCluster = currentCluster;
        }
        return closestValidCluster;
    }

    private void harvestCrops() {
        boolean shouldSwing = false;
        BlockPos aboveCoords = this.target.up();
        Block currentCrop = this.farmer.world.getBlockState(aboveCoords).getBlock();
        if (!this.canHarvest(aboveCoords)) {
            this.farmCoords.remove(0);
            if (this.farmCoords.isEmpty()) {
                this.farmer.visitedFarms.add(this.farmer.currentResource);
                this.farmer.currentResource = null;
                this.target = null;
                this.previousTime = 0;
                this.currentTime = 0;
            }
            return;
        }
        if (this.farmer.getNavigator().noPath()) {
            BlockPos low = this.farmer.currentResource.lowestCoords;
            this.farmer.getLookHelper().setLookPosition((double) low.getX(), (double) low.getY(), (double) low.getZ(), 10.0f, 10.0f);
            shouldSwing = true;
            if (this.previousTime <= 0) {
                this.previousTime = this.farmer.ticksExisted;
                this.harvestTime = this.farmer.getHarvestTime();
            }
        } else {
            shouldSwing = false;
        }
        if (this.previousTime > 0) {
            this.currentTime = this.farmer.ticksExisted;
            if (!this.farmCoords.isEmpty()) {
                if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                    this.previousTime = this.currentTime;
                    this.harvestTime = this.farmer.getHarvestTime();
                    if (this.canHarvest(aboveCoords)) {
                        if (!(currentCrop instanceof BlockStem)) {
                            IBlockState cropState = this.farmer.world.getBlockState(aboveCoords);
                            NonNullList<ItemStack> cropDrops = NonNullList.create();
                            currentCrop.getDrops(cropDrops, this.farmer.world, aboveCoords, cropState, 0);
                            ItemStack foundSeed = ItemStack.EMPTY;
                            for (ItemStack i : cropDrops) {
                                if (!(i.getItem() instanceof IPlantable)) {
                                    continue;
                                }
                                foundSeed = i;
                                break;
                            }
                            if (!foundSeed.isEmpty()) {
                                this.farmer.seedToPlant = (IPlantable) foundSeed.getItem();
                            }
                            AIHelper.breakBlock(aboveCoords, this.farmer);
                            this.plantCrop(aboveCoords);
                        } else {
                            BlockPos fruitPos = this.findAdjacentFruit(aboveCoords);
                            if (fruitPos != null) {
                                AIHelper.breakBlock(fruitPos, this.farmer);
                            }
                        }
                    }
                    this.farmCoords.remove(0);
                    if (this.farmCoords.isEmpty()) {
                        this.farmer.visitedFarms.add(this.farmer.currentResource);
                        this.farmer.currentResource = null;
                        this.target = null;
                        this.previousTime = 0;
                        this.currentTime = 0;
                    }
                }
            } else {
                this.farmer.visitedFarms.add(this.farmer.currentResource);
                this.farmer.currentResource = null;
                this.target = null;
                this.previousTime = 0;
                this.currentTime = 0;
            }
        }
        if (shouldSwing) {
            this.farmer.swingArm(EnumHand.MAIN_HAND);
        } else {
            this.previousTime = 0;
        }
    }

    private boolean canHarvest(BlockPos coords) {
        IBlockState state = this.farmer.world.getBlockState(coords);
        Block block = state.getBlock();
        if (block instanceof BlockCrops) {
            return ((BlockCrops) block).isMaxAge(state);
        }
        if (block instanceof BlockReed) {
            return true;
        }
        if (block instanceof BlockNetherWart) {
            return state.getValue(BlockNetherWart.AGE) >= 3;
        }
        if (block instanceof BlockStem) {
            return this.findAdjacentFruit(coords) != null;
        }
        return false;
    }

    /**
     * Re-implements the 1.7.10 stem-direction lookup: scan the stem's four horizontal neighbours for a
     * grown melon or pumpkin block. Returns its position, or null if the stem has not fruited yet.
     */
    private BlockPos findAdjacentFruit(BlockPos stemPos) {
        BlockPos[] neighbors = new BlockPos[] { stemPos.west(), stemPos.east(), stemPos.north(), stemPos.south() };
        for (BlockPos n : neighbors) {
            Block b = this.farmer.world.getBlockState(n).getBlock();
            if (b == Blocks.MELON_BLOCK || b == Blocks.PUMPKIN) {
                return n;
            }
        }
        return null;
    }

    private void plantCrop(BlockPos coords) {
        if (!this.farmer.world.isAirBlock(coords)) {
            return;
        }
        if (this.farmer.seedToPlant != null) {
            EnumPlantType plantType = this.farmer.seedToPlant.getPlantType(this.farmer.world, coords);
            if (plantType == EnumPlantType.Nether
                    && this.farmer.world.getBlockState(coords.down()).getBlock() != Blocks.SOUL_SAND) {
                return;
            }
            if (plantType == EnumPlantType.Crop
                    && this.farmer.world.getBlockState(coords.down()).getBlock() != Blocks.FARMLAND) {
                return;
            }
            Item plantItem = (Item) this.farmer.seedToPlant;
            IBlockState newPlant = this.farmer.seedToPlant.getPlant(this.farmer.world, coords);
            if (this.farmer.lastSeedIndex < 0 || this.farmer.inventory.getStackInSlot(this.farmer.lastSeedIndex).isEmpty()
                    || !this.farmer.inventory.getStackInSlot(this.farmer.lastSeedIndex).getItem().equals(plantItem)) {
                this.farmer.lastSeedIndex = this.farmer.inventory.containsItem(new ItemStack(plantItem));
            }
            if (this.farmer.lastSeedIndex >= 0) {
                this.farmer.inventory.decrementSlot(this.farmer.lastSeedIndex);
                this.farmer.world.setBlockState(coords, newPlant);
            }
        }
    }
}
