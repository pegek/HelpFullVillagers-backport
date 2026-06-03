package com.spege.helpfulvillagers.util;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A connected run of identical blocks (a tree, an ore vein, a crop patch) discovered by flood-fill.
 * 1.12.2 migration: ChunkCoordinates -> BlockPos; block identity compared via {@link Block#getIdFromBlock}.
 */
@SuppressWarnings({ "null", "deprecation" })
public class ResourceCluster {
    public World world;
    public BlockPos coords;
    public BlockPos lowestCoords;
    public Block startBlock;
    public ArrayList<BlockPos> blockCluster;
    public boolean builtFlag;

    public ResourceCluster(World world) {
        this.world = world;
    }

    public ResourceCluster(World world, BlockPos coords) {
        this.world = world;
        this.coords = coords;
        this.lowestCoords = coords;
        this.startBlock = world.getBlockState(coords).getBlock();
        this.blockCluster = new ArrayList<BlockPos>();
        this.builtFlag = false;
    }

    public ArrayList<Block> getAdjacent() {
        ArrayList<Block> blocks = new ArrayList<Block>();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    Block block = this.world
                            .getBlockState(new BlockPos(this.coords.getX() + x, this.coords.getY() + y, this.coords.getZ() + z))
                            .getBlock();
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    private ArrayList<BlockPos> getAdjacentCoords(BlockPos coords) {
        ArrayList<BlockPos> adjacent = new ArrayList<BlockPos>();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    adjacent.add(new BlockPos(coords.getX() + x, coords.getY() + y, coords.getZ() + z));
                }
            }
        }
        return adjacent;
    }

    public void buildCluster() {
        this.buildCluster(0);
    }

    public void buildCluster(int limit) {
        if (!this.startBlock.equals(Blocks.AIR)) {
            this.buildCluster(this.coords, limit);
        }
    }

    private void buildCluster(BlockPos coords, int limit) {
        if (limit > 0) {
            if (AIHelper.findDistance(coords.getX(), this.coords.getX()) > limit) {
                return;
            }
            if (AIHelper.findDistance(coords.getY(), this.coords.getY()) > limit) {
                return;
            }
            if (AIHelper.findDistance(coords.getY(), this.coords.getY()) > limit) {
                return;
            }
        }
        Block currentBlock = this.world.getBlockState(coords).getBlock();
        if (!this.blockCluster.contains(coords)
                && Block.getIdFromBlock(currentBlock) == Block.getIdFromBlock(this.startBlock)) {
            if (coords.getY() < this.lowestCoords.getY()) {
                this.lowestCoords = coords;
            }
            this.blockCluster.add(coords);
            ArrayList<BlockPos> adjacent = this.getAdjacentCoords(coords);
            for (int i = 0; i < adjacent.size(); ++i) {
                if (adjacent.get(i).equals(coords)) {
                    continue;
                }
                this.buildCluster(adjacent.get(i), limit);
            }
        }
    }

    public boolean matchesCluster(ResourceCluster cluster) {
        ArrayList<BlockPos> otherCluster = cluster.blockCluster;
        if (this.blockCluster.size() != otherCluster.size()) {
            return false;
        }
        for (int i = 0; i < otherCluster.size(); ++i) {
            BlockPos otherCoords = otherCluster.get(i);
            if (this.blockCluster.contains(otherCoords)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public NBTTagList writeToNBT(NBTTagList tagList) {
        NBTTagCompound compound = new NBTTagCompound();
        int[] coords = new int[] { this.coords.getX(), this.coords.getY(), this.coords.getZ() };
        compound.setIntArray("Coords", coords);
        tagList.appendTag(compound);
        return tagList;
    }

    public void readFromNBT(NBTTagList tagList) {
        NBTTagCompound compound = tagList.getCompoundTagAt(0);
        int[] coords = compound.getIntArray("Coords");
        this.lowestCoords = this.coords = new BlockPos(coords[0], coords[1], coords[2]);
        this.blockCluster = new ArrayList<BlockPos>();
        this.builtFlag = false;
    }
}
