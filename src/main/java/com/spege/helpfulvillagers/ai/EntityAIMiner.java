package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.EntityMiner;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ResourceCluster;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Miner gathering AI: digs a spiral staircase down to ore depth, tunnels to ore veins and mines them,
 * back-filling as it goes.
 *
 * <p>1.12.2 migration: ChunkCoordinates -> immutable BlockPos (offsets create new positions);
 * ForgeDirection.UP -> EnumFacing.UP via {@link net.minecraft.world.World#isSideSolid}; block placement
 * via setBlockState(getDefaultState()); dig speed via getDestroySpeed(stack, IBlockState);
 * Block.getIdFromBlock for identity.
 *
 * <p>Fixed: an incomplete field rename during the port left {@link #findMine()} pathing to the
 * inherited {@code Worker.target} (never assigned here) while operating on {@code miner.target}
 * everywhere else, so the miner never moved toward its mine. The reference 1.4.0b5 used a single
 * consistent target; findMine now uses {@code miner.target} throughout.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIMiner extends EntityAIWorker {
    private EntityMiner miner;
    private final int WAIT_TIME = 40;
    private int previousWait;
    private Block lastBlock;
    private Block lastAbove;
    private Block lastAbove2;
    private ArrayList<BlockPos> surroundingCoords;
    private boolean reset;
    private boolean skipResource;

    public EntityAIMiner(EntityMiner miner) {
        super(miner);
        this.miner = miner;
        miner.shaftIndex = miner.nearestShaftCoord();
        this.previousWait = 0;
        this.lastBlock = null;
        this.lastAbove = null;
        this.lastAbove2 = null;
        this.surroundingCoords = new ArrayList<BlockPos>();
        this.reset = true;
        this.skipResource = false;
        this.setMutexBits(1);
    }

    @Override
    protected boolean idle() {
        this.villager.currentActivity = EnumActivity.IDLE;
        if (!this.villager.world.isRemote && this.villager.homeVillage == null) {
            System.out.println("No Home Village");
            return false;
        }
        this.villager.checkGuildHall();
        if (this.villager.homeGuildHall == null) {
            return false;
        }
        if (this.villager.currentCraftItem != null && this.villager.currentCraftItem.getPriority() >= 1) {
            if ((this.readyToCraft || this.readyToSmelt) && !this.villager.nearHall()) {
                this.mReturn();
                return false;
            }
            if ((this.readyToCraft || this.readyToSmelt) && this.villager.nearHall()) {
                this.villager.currentActivity = EnumActivity.CRAFT;
                return true;
            }
            if (!this.craftCheck) {
                this.villager.currentActivity = EnumActivity.CRAFT;
                this.craftCheck = true;
                return true;
            }
        }
        if (this.villager.inventory.isFull() || !this.villager.hasTool) {
            if (this.villager.nearHall()) {
                if (this.villager.currentCraftItem != null && !this.craftCheck) {
                    this.villager.currentActivity = EnumActivity.CRAFT;
                    this.craftCheck = true;
                    return true;
                }
                if (!this.villager.inventory.isEmpty() || !this.villager.hasTool) {
                    this.villager.currentActivity = EnumActivity.STORE;
                    this.craftCheck = false;
                    return true;
                }
                this.craftCheck = false;
                return false;
            }
            this.mReturn();
            this.craftCheck = false;
            return false;
        }
        if (this.villager.world.isDaytime()) {
            this.villager.currentActivity = EnumActivity.GATHER;
            this.craftCheck = false;
            return true;
        }
        if (!this.villager.nearHall()) {
            this.mReturn();
            this.craftCheck = false;
            return false;
        }
        if (this.villager.currentCraftItem != null && !this.craftCheck) {
            this.villager.currentActivity = EnumActivity.CRAFT;
            this.craftCheck = true;
            return true;
        }
        if (!this.villager.inventory.isEmpty() || !this.villager.hasTool) {
            this.villager.currentActivity = EnumActivity.STORE;
            this.craftCheck = false;
            return true;
        }
        this.craftCheck = false;
        return true;
    }

    @Override
    protected boolean gather() {
        if (this.miner.topCoords == null) {
            this.findMine();
        } else if (this.miner.shaftCoords.isEmpty()) {
            this.buildStairs(this.miner.topCoords, this.miner.topDir);
        } else if (!this.skipResource) {
            if (this.miner.currentResource == null) {
                this.getNewResource();
            }
            if (this.miner.currentResource == null) {
                if (this.miner.returnPath.isEmpty()) {
                    this.digSection(this.miner.shaftIndex, true);
                } else {
                    this.digTunnel(true);
                }
            } else if (!this.miner.tunnelCoords.isEmpty()) {
                this.digTunnel(false);
            } else {
                this.mineResource();
            }
        } else {
            if (this.miner.returnPath.isEmpty()) {
                this.digSection(this.miner.shaftIndex, true);
            } else {
                this.digTunnel(true);
            }
            if ((int) this.miner.posY < (int) this.miner.lastTickPosY) {
                this.skipResource = false;
            }
        }
        return this.idle();
    }

    @Override
    protected boolean store() {
        if (this.miner.homeGuildHall == null) {
            return this.idle();
        }
        TileEntityChest chest = this.miner.homeGuildHall.getAvailableChest();
        if (!this.miner.inventory.isEmpty() || !this.miner.hasTool) {
            if (chest != null) {
                this.miner.moveTo(chest.getPos(), this.speed);
            } else {
                this.miner.changeGuildHall = true;
            }
            if (chest != null
                    && AIHelper.findDistance((int) this.miner.posX, chest.getPos().getX()) <= 2
                    && AIHelper.findDistance((int) this.miner.posY, chest.getPos().getY()) <= 2
                    && AIHelper.findDistance((int) this.miner.posZ, chest.getPos().getZ()) <= 2) {
                int solidIndex = this.miner.inventory.findSolidBlock(this.miner.excludeBlocks);
                ItemStack temp = ItemStack.EMPTY;
                if (solidIndex >= 0) {
                    temp = this.miner.inventory.getStackInSlot(solidIndex);
                    this.miner.inventory.setMainContents(solidIndex, ItemStack.EMPTY);
                }
                try {
                    this.miner.inventory.dumpInventory(chest);
                } catch (NullPointerException e) {
                    // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
                }
                if (solidIndex >= 0) {
                    this.miner.inventory.addItem(temp);
                }
                if (!this.miner.hasTool) {
                    for (TileEntityChest toolChest : this.miner.homeGuildHall.guildChests) {
                        int index = AIHelper.chestContains(toolChest, this.miner);
                        if (index < 0) {
                            continue;
                        }
                        this.miner.inventory.swapEquipment(toolChest, index, 0);
                    }
                }
            }
        }
        if (!this.miner.hasTool && this.miner.queuedTool.isEmpty()) {
            int lowestPrice = Integer.MAX_VALUE;
            ItemStack lowestItem = ItemStack.EMPTY;
            for (int i = 0; i < this.miner.getValidTools().length; ++i) {
                ItemStack item = this.miner.getValidTools()[i];
                int price = this.miner.homeVillage.economy.getPrice(item.getDisplayName());
                if (price >= lowestPrice && !lowestItem.isEmpty()) {
                    continue;
                }
                lowestPrice = price;
                lowestItem = item;
            }
            this.miner.addCraftItem(new CraftItem(lowestItem, this.miner));
            this.miner.queuedTool = lowestItem;
        } else if (this.miner.hasTool) {
            this.miner.queuedTool = ItemStack.EMPTY;
        }
        return this.idle();
    }

    private void mReturn() {
        if (this.miner.currentResource != null) {
            this.miner.currentResource = null;
        }
        this.miner.tunnelCoords.clear();
        if (this.miner.topCoords != null && this.miner.shaftIndex > 0) {
            this.miner.setLocationAndAngles(this.miner.topCoords.getX(), this.miner.topCoords.getY(),
                    this.miner.topCoords.getZ(), 0.0f, 0.0f);
            this.miner.shaftIndex = 0;
            this.miner.dugSection = false;
            this.miner.digCoords.clear();
        }
        this.miner.currentActivity = EnumActivity.RETURN;
    }

    private void findMine() {
        if (this.miner.target == null) {
            this.miner.target = this.miner.lastResource == null
                    ? AIHelper.getRandOutsideCoords(this.miner, 30)
                    : AIHelper.getRandOutsideCoords(this.miner, 60);
        }
        if (this.miner.target != null) {
            // Fixed: was moveTo(this.target) — the AI-base target field, which findMine never assigns
            // (it operates on this.miner.target throughout). moveTo(null) hit the caught NPE and the
            // miner never pathed to its mine target. Aligned to this.miner.target.
            this.miner.moveTo(this.miner.target, this.speed);
        }
        if (!AIHelper.isInRangeOfAnyVillage(this.miner.posX, this.miner.posY, this.miner.posZ)) {
            BlockPos currCoords = new BlockPos((int) this.miner.posX, (int) this.miner.posY - 1, (int) this.miner.posZ);
            Block currBlock = this.miner.world.getBlockState(currCoords).getBlock();
            if (currBlock.equals(Blocks.STONE)) {
                this.miner.topCoords = currCoords;
                this.miner.topDir = this.miner.getDirection();
                this.miner.lastResource = new ResourceCluster(this.miner.world, this.miner.topCoords);
            } else {
                // Guard: new ItemStack(AIR) / blocks without an item form yield an empty stack, and
                // 1.12.2 OreDictionary.getOreIDs throws "Stack can not be invalid!" on empty stacks.
                ItemStack blockStack = new ItemStack(currBlock);
                if (!blockStack.isEmpty()) {
                    int[] oreDictIDs = OreDictionary.getOreIDs(blockStack);
                    for (int j = 0; j < oreDictIDs.length; ++j) {
                        String name = OreDictionary.getOreName(oreDictIDs[j]);
                        if (!name.contains("ore")) {
                            continue;
                        }
                        this.miner.topCoords = currCoords;
                        this.miner.topDir = this.miner.getDirection();
                        this.miner.lastResource = new ResourceCluster(this.miner.world, this.miner.topCoords);
                        break;
                    }
                }
            }
        }
        if (Math.abs(this.miner.posX - (double) this.miner.target.getX()) <= 2.0
                && Math.abs(this.miner.posZ - (double) this.miner.target.getZ()) <= 2.0) {
            this.miner.lastResource = null;
            this.surroundingCoords = this.miner.getSurroundingCoords();
            for (int i = 0; i < this.surroundingCoords.size(); ++i) {
                BlockPos coord = this.surroundingCoords.get(i);
                Block block = this.miner.world.getBlockState(coord).getBlock();
                if (!block.equals(Blocks.WATER) && !block.equals(Blocks.LAVA)) {
                    continue;
                }
                this.miner.target = null;
                return;
            }
            BlockPos topCoords = this.miner.getCoords();
            while (this.miner.world.getBlockState(topCoords).getBlock().equals(Blocks.AIR)) {
                topCoords = topCoords.down();
            }
            this.miner.topCoords = topCoords;
            this.miner.topDir = this.miner.getDirection();
        }
    }

    private void buildStairs(BlockPos coords, int direction) {
        Block currentBlock = this.miner.world.getBlockState(coords).getBlock();
        if (coords.getY() <= 0 || currentBlock.equals(Blocks.BEDROCK) || currentBlock.equals(Blocks.LAVA)
                || currentBlock.equals(Blocks.WATER)) {
            return;
        }
        this.miner.shaftCoords.add(coords);
        int dx = 0;
        int dz = 0;
        switch (direction) {
            case 0:
                dz = 1;
                break;
            case 1:
                dx = -1;
                break;
            case 2:
                dz = -1;
                break;
            case 3:
                dx = 1;
                break;
        }
        BlockPos nextCoords = new BlockPos(coords.getX() + dx, coords.getY() - 1, coords.getZ() + dz);
        if (++direction > 3) {
            direction = 0;
        }
        this.buildStairs(nextCoords, direction);
    }

    private void digSection(int index, boolean down) {
        if (index >= this.miner.shaftCoords.size()) {
            return;
        }
        if (index < 0) {
            this.miner.shaftIndex = 0;
            return;
        }
        boolean shouldSwing = false;
        BlockPos stairCoords = this.miner.shaftCoords.get(index);
        this.miner.moveTo(stairCoords, this.speed);
        if (AIHelper.findDistance((int) this.miner.posX, stairCoords.getX()) <= 3
                && AIHelper.findDistance((int) this.miner.posY, stairCoords.getY()) <= 3
                && AIHelper.findDistance((int) this.miner.posZ, stairCoords.getZ()) <= 3) {
            if (!this.miner.dugSection) {
                for (int i = 3; i >= 1; --i) {
                    BlockPos aboveCoords = new BlockPos(stairCoords.getX(), stairCoords.getY() + i, stairCoords.getZ());
                    Block currentBlock = this.miner.world.getBlockState(aboveCoords).getBlock();
                    if (currentBlock.equals(Blocks.BEDROCK) || currentBlock.equals(Blocks.WATER)
                            || currentBlock.equals(Blocks.LAVA)) {
                        return;
                    }
                    if (!this.miner.world.isSideSolid(aboveCoords, EnumFacing.UP)) {
                        continue;
                    }
                    this.miner.digCoords.add(aboveCoords);
                }
                this.miner.dugSection = true;
            } else if (!this.miner.digCoords.isEmpty()) {
                BlockPos currentCoords = this.miner.digCoords.get(0);
                if (this.previousTime <= 0) {
                    this.previousTime = this.miner.ticksExisted;
                    if (!this.miner.getCurrentItem().isEmpty()) {
                        this.harvestTime = 60.0f / this.miner.getCurrentItem().getItem()
                                .getDestroySpeed(this.miner.getCurrentItem(), this.miner.world.getBlockState(currentCoords));
                    }
                    shouldSwing = true;
                } else {
                    this.currentTime = this.miner.ticksExisted;
                    if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                        this.previousTime = 0;
                        shouldSwing = false;
                        this.miner.digCoords.remove(0);
                        AIHelper.breakBlock(currentCoords, this.miner);
                    } else {
                        shouldSwing = true;
                    }
                }
            } else {
                IBlockState stairState = this.miner.world.getBlockState(stairCoords);
                Block stairBlock = stairState.getBlock();
                if (!this.miner.world.isSideSolid(stairCoords, EnumFacing.UP) || stairBlock.equals(Blocks.GRAVEL)
                        || stairBlock.equals(Blocks.SAND)) {
                    int solidIndex = this.miner.inventory.findSolidBlock(this.miner.excludeBlocks);
                    if (solidIndex >= 0) {
                        Block newBlock = Block.getBlockFromItem(this.miner.inventory.getStackInSlot(solidIndex).getItem());
                        if (!(stairBlock.equals(Blocks.AIR) || stairBlock.equals(Blocks.WATER) || stairBlock.equals(Blocks.LAVA))) {
                            int metadata = stairBlock.getMetaFromState(stairState);
                            ItemStack item = new ItemStack(stairBlock, 1, metadata);
                            try {
                                this.miner.inventory.addItem(item);
                            } catch (NullPointerException e) {
                                // empty catch block
                            }
                        }
                        this.miner.world.setBlockState(stairCoords, newBlock.getDefaultState());
                        this.miner.inventory.decrementSlot(solidIndex);
                    } else {
                        return;
                    }
                }
                if (this.miner.getNavigator().noPath()) {
                    BlockPos upCoords = new BlockPos(stairCoords.getX(), stairCoords.getY() + 2, stairCoords.getZ());
                    if (!this.miner.beingFollowed && upCoords.getY() < this.miner.topCoords.getY()) {
                        ArrayList<BlockPos> adjacentCoords = AIHelper.getAdjacentCoords(upCoords);
                        for (int i = 0; i < adjacentCoords.size(); ++i) {
                            BlockPos aCoords = adjacentCoords.get(i);
                            if (!this.miner.isInMine() || this.miner.world.isSideSolid(aCoords, EnumFacing.UP)) {
                                continue;
                            }
                            this.replaceBlock(adjacentCoords.get(i));
                        }
                    }
                    this.miner.shaftIndex = down ? ++this.miner.shaftIndex : --this.miner.shaftIndex;
                    this.miner.dugSection = false;
                }
            }
        } else if (AIHelper.findDistance((int) this.miner.posX, stairCoords.getX()) > 10
                || AIHelper.findDistance((int) this.miner.posZ, stairCoords.getZ()) > 10) {
            this.miner.moveTo(stairCoords, this.speed);
        } else {
            this.miner.shaftIndex = this.miner.nearestShaftCoord();
        }
        if (shouldSwing) {
            this.miner.swingArm(EnumHand.MAIN_HAND);
        }
        this.miner.swingingPickaxe = shouldSwing;
    }

    private boolean replaceBlock(BlockPos coords) {
        for (int i = 0; i < 4; ++i) {
            BlockPos checkCoords = this.miner.shaftCoords.get(i);
            if (checkCoords.getX() != coords.getX() || checkCoords.getZ() != coords.getZ()) {
                continue;
            }
            return false;
        }
        Block block = this.miner.world.getBlockState(coords).getBlock();
        int solidIndex = this.miner.inventory.findSolidBlock(this.miner.excludeBlocks);
        if (solidIndex >= 0) {
            Block newBlock = Block.getBlockFromItem(this.miner.inventory.getStackInSlot(solidIndex).getItem());
            if (!(block.equals(Blocks.AIR) || block.equals(Blocks.WATER) || block.equals(Blocks.LAVA))) {
                AIHelper.breakBlock(coords, this.miner);
            }
            this.miner.world.setBlockState(coords, newBlock.getDefaultState());
            this.miner.inventory.decrementSlot(solidIndex);
        }
        return true;
    }

    private void getNewResource() {
        ArrayList<BlockPos> boxCoords = this.miner.getValidCoords();
        double closestDist = Double.MAX_VALUE;
        ResourceCluster closestValidCluster = null;
        for (int i = 0; i < boxCoords.size(); ++i) {
            BlockPos currentCoords = boxCoords.get(i);
            int dist = (int) currentCoords.distanceSq(this.miner.getCoords());
            if (!((double) dist < closestDist)) {
                continue;
            }
            closestValidCluster = new ResourceCluster(this.miner.world, currentCoords);
            closestDist = dist;
        }
        if (closestValidCluster != null) {
            this.miner.currentResource = closestValidCluster;
            this.miner.currentResource.buildCluster();
            this.buildTunnel();
        }
    }

    private void buildTunnel() {
        BlockPos destCoords = this.miner.currentResource.coords;
        int cx = (int) this.miner.posX;
        int cy = destCoords.getY();
        int cz = (int) this.miner.posZ;
        while (true) {
            this.miner.tunnelCoords.add(new BlockPos(cx, cy, cz));
            if (cx < destCoords.getX()) {
                ++cx;
                continue;
            }
            if (cx <= destCoords.getX()) {
                break;
            }
            --cx;
        }
        while (true) {
            this.miner.tunnelCoords.add(new BlockPos(cx, cy, cz));
            if (cz < destCoords.getZ()) {
                ++cz;
                continue;
            }
            if (cz <= destCoords.getZ()) {
                break;
            }
            --cz;
        }
        this.miner.tunnelCoords.remove(this.miner.tunnelCoords.size() - 1);
        --this.miner.shaftIndex;
    }

    private void digTunnel(boolean returning) {
        boolean shouldSwing;
        block46: {
            shouldSwing = false;
            BlockPos currentCoords = returning
                    ? this.miner.returnPath.get(this.miner.returnPath.size() - 1)
                    : this.miner.tunnelCoords.get(0);
            ArrayList<BlockPos> adjacent = AIHelper.getAdjacentCoords(currentCoords);
            for (int i = 0; i < adjacent.size(); ++i) {
                BlockPos check = adjacent.get(i);
                Block checkBlock = this.miner.world.getBlockState(check).getBlock();
                if (!checkBlock.equals(Blocks.LAVA)) {
                    continue;
                }
                this.miner.tunnelCoords.clear();
                this.skipResource = true;
                this.miner.currentResource = null;
                return;
            }
            if (this.lastBlock != null && (this.lastBlock.equals(Blocks.SAND) || this.lastBlock.equals(Blocks.GRAVEL))) {
                if (this.previousWait <= 0) {
                    this.previousWait = this.miner.ticksExisted;
                    return;
                }
                this.currentTime = this.miner.ticksExisted;
                if (this.currentTime - this.previousWait >= 40) {
                    this.previousWait = 0;
                    this.lastBlock = null;
                } else {
                    this.miner.swingArm(EnumHand.MAIN_HAND);
                    return;
                }
            }
            if (this.lastAbove != null && (this.lastAbove.equals(Blocks.SAND) || this.lastAbove.equals(Blocks.GRAVEL))) {
                if (this.previousWait <= 0) {
                    this.previousWait = this.miner.ticksExisted;
                    return;
                }
                this.currentTime = this.miner.ticksExisted;
                if (this.currentTime - this.previousWait >= 40) {
                    this.previousWait = 0;
                    this.lastAbove = null;
                } else {
                    this.miner.swingArm(EnumHand.MAIN_HAND);
                    return;
                }
            }
            if (this.lastAbove2 != null && (this.lastAbove2.equals(Blocks.SAND) || this.lastAbove2.equals(Blocks.GRAVEL))) {
                if (this.previousWait <= 0) {
                    this.previousWait = this.miner.ticksExisted;
                    return;
                }
                this.currentTime = this.miner.ticksExisted;
                if (this.currentTime - this.previousWait >= 40) {
                    this.previousWait = 0;
                    this.lastAbove2 = null;
                } else {
                    this.miner.swingArm(EnumHand.MAIN_HAND);
                    return;
                }
            }
            BlockPos aboveCoords = new BlockPos(currentCoords.getX(), currentCoords.getY() + 1, currentCoords.getZ());
            BlockPos aboveCoords2 = new BlockPos(currentCoords.getX(), currentCoords.getY() + 2, currentCoords.getZ());
            BlockPos belowCoords = new BlockPos(currentCoords.getX(), currentCoords.getY() - 1, currentCoords.getZ());
            Block currentBlock = this.miner.world.getBlockState(currentCoords).getBlock();
            Block aboveBlock = this.miner.world.getBlockState(aboveCoords).getBlock();
            Block aboveBlock2 = this.miner.world.getBlockState(aboveCoords2).getBlock();
            Block belowBlock = this.miner.world.getBlockState(belowCoords).getBlock();
            this.miner.moveTo(currentCoords, this.speed);
            if (AIHelper.findDistance((int) this.miner.posX, currentCoords.getX()) <= 3
                    && AIHelper.findDistance((int) this.miner.posY, currentCoords.getY()) <= 3
                    && AIHelper.findDistance((int) this.miner.posZ, currentCoords.getZ()) <= 3) {
                this.miner.getNavigator().clearPath();
                if (!(currentBlock.equals(Blocks.AIR) || currentBlock.equals(Blocks.WATER) || currentBlock.equals(Blocks.LAVA))) {
                    if (this.previousTime <= 0) {
                        this.previousTime = this.miner.ticksExisted;
                        if (!this.miner.getCurrentItem().isEmpty()) {
                            this.harvestTime = 60.0f / this.miner.getCurrentItem().getItem()
                                    .getDestroySpeed(this.miner.getCurrentItem(), this.miner.world.getBlockState(currentCoords));
                        }
                        shouldSwing = true;
                    } else {
                        this.currentTime = this.miner.ticksExisted;
                        if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                            this.previousTime = 0;
                            shouldSwing = false;
                            AIHelper.breakBlock(currentCoords, this.miner);
                            this.lastBlock = currentBlock;
                        } else {
                            shouldSwing = true;
                        }
                    }
                } else if (!(aboveBlock.equals(Blocks.AIR) || aboveBlock.equals(Blocks.WATER) || aboveBlock.equals(Blocks.LAVA))) {
                    if (this.previousTime <= 0) {
                        this.previousTime = this.miner.ticksExisted;
                        if (!this.miner.getCurrentItem().isEmpty()) {
                            this.harvestTime = 60.0f / this.miner.getCurrentItem().getItem()
                                    .getDestroySpeed(this.miner.getCurrentItem(), this.miner.world.getBlockState(aboveCoords));
                        }
                        shouldSwing = true;
                    } else {
                        this.currentTime = this.miner.ticksExisted;
                        if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                            this.previousTime = 0;
                            shouldSwing = false;
                            AIHelper.breakBlock(aboveCoords, this.miner);
                            this.lastAbove = aboveBlock;
                        } else {
                            shouldSwing = true;
                        }
                    }
                } else if (!(aboveBlock2.equals(Blocks.AIR) || aboveBlock2.equals(Blocks.WATER) || aboveBlock2.equals(Blocks.LAVA))) {
                    if (this.previousTime <= 0) {
                        this.previousTime = this.miner.ticksExisted;
                        if (!this.miner.getCurrentItem().isEmpty()) {
                            this.harvestTime = 60.0f / this.miner.getCurrentItem().getItem()
                                    .getDestroySpeed(this.miner.getCurrentItem(), this.miner.world.getBlockState(aboveCoords2));
                        }
                        shouldSwing = true;
                    } else {
                        this.currentTime = this.miner.ticksExisted;
                        if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                            this.previousTime = 0;
                            shouldSwing = false;
                            AIHelper.breakBlock(aboveCoords2, this.miner);
                            this.lastAbove2 = aboveBlock2;
                        } else {
                            shouldSwing = true;
                        }
                    }
                } else if (!this.miner.world.isSideSolid(belowCoords, EnumFacing.UP) || belowBlock.equals(Blocks.GRAVEL)
                        || belowBlock.equals(Blocks.SAND)) {
                    boolean replaced = this.replaceBlock(belowCoords);
                    if (!replaced) {
                        try {
                            if (returning) {
                                this.miner.returnPath.remove(this.miner.returnPath.size() - 1);
                                break block46;
                            }
                            this.miner.returnPath.add(currentCoords);
                            this.miner.tunnelCoords.remove(0);
                        } catch (Exception e) {
                            // empty catch block
                        }
                    }
                } else if (returning) {
                    this.miner.returnPath.remove(this.miner.returnPath.size() - 1);
                } else {
                    this.miner.returnPath.add(currentCoords);
                    this.miner.tunnelCoords.remove(0);
                }
            } else if (AIHelper.findDistance((int) this.miner.posX, currentCoords.getX()) > 10
                    || AIHelper.findDistance((int) this.miner.posZ, currentCoords.getZ()) > 10) {
                this.miner.moveTo(currentCoords, this.speed);
            }
        }
        if (shouldSwing) {
            this.miner.swingArm(EnumHand.MAIN_HAND);
        }
        this.miner.swingingPickaxe = shouldSwing;
    }

    private void mineResource() {
        boolean shouldSwing = false;
        BlockPos currentCoords = this.miner.currentResource.coords;
        this.miner.moveTo(currentCoords, this.speed);
        if (AIHelper.findDistance((int) this.miner.posX, currentCoords.getX()) <= 3
                && AIHelper.findDistance((int) this.miner.posY, currentCoords.getY()) <= 3
                && AIHelper.findDistance((int) this.miner.posZ, currentCoords.getZ()) <= 3) {
            shouldSwing = true;
            if (this.previousTime <= 0) {
                this.previousTime = this.miner.ticksExisted;
                this.harvestTime = 60.0f / this.miner.getCurrentItem().getItem()
                        .getDestroySpeed(this.miner.getCurrentItem(), this.miner.currentResource.startBlock.getDefaultState());
            }
            if (this.previousTime > 0) {
                this.currentTime = this.miner.ticksExisted;
                if (!this.miner.currentResource.blockCluster.isEmpty()) {
                    if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                        this.previousTime = this.currentTime;
                        currentCoords = this.miner.currentResource.blockCluster.get(0);
                        Block currentBlock = this.miner.world.getBlockState(currentCoords).getBlock();
                        if (Block.getIdFromBlock(currentBlock) == Block.getIdFromBlock(this.miner.currentResource.startBlock)) {
                            AIHelper.breakBlock(currentCoords, this.miner);
                        }
                        this.miner.currentResource.blockCluster.remove(0);
                        if (currentCoords.getY() == (int) this.miner.posY - 1) {
                            this.replaceBlock(currentCoords);
                        }
                    }
                } else {
                    this.miner.currentResource = null;
                    this.previousTime = 0;
                    this.currentTime = 0;
                }
            }
        } else if (AIHelper.findDistance((int) this.miner.posX, currentCoords.getX()) > 10
                || AIHelper.findDistance((int) this.miner.posZ, currentCoords.getZ()) > 10) {
            this.miner.moveTo(currentCoords, this.speed);
            shouldSwing = false;
        }
        if (shouldSwing) {
            this.miner.swingArm(EnumHand.MAIN_HAND);
        }
        this.miner.swingingPickaxe = shouldSwing;
    }
}
