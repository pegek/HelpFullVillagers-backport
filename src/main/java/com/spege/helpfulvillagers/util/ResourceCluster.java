package com.spege.helpfulvillagers.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A connected run of identical blocks (a tree, an ore vein, a crop patch) discovered by flood-fill.
 * 1.12.2 migration: ChunkCoordinates -> BlockPos; block identity compared via ==.
 *
 * <p>Optimisation vs 1.7.10 port:
 * <ul>
 *   <li>buildCluster now iterative BFS with HashSet visited — O(n) instead of O(n²) ArrayList.contains.</li>
 *   <li>Eliminates StackOverflow risk for large clusters (no more deep recursion).</li>
 *   <li>matchesCluster uses a temporary HashSet — O(n) instead of O(n²).</li>
 * </ul>
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

    /** Returns blocks in the 3×3×3 cube centred on this cluster's starting coords. */
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

    public void buildCluster() {
        this.buildCluster(0);
    }

    /**
     * Iterative BFS flood-fill collecting all connected blocks of the same type as {@code startBlock}.
     * {@code limit} caps the Manhattan distance per-axis from the origin; 0 means unlimited.
     */
    public void buildCluster(int limit) {
        if (this.startBlock == null || this.startBlock.equals(Blocks.AIR)) {
            return;
        }
        HashSet<BlockPos> visited = new HashSet<BlockPos>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        visited.add(this.coords);
        queue.add(this.coords);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            if (limit > 0) {
                if (AIHelper.findDistance(current.getX(), this.coords.getX()) > limit) continue;
                if (AIHelper.findDistance(current.getY(), this.coords.getY()) > limit) continue;
                if (AIHelper.findDistance(current.getZ(), this.coords.getZ()) > limit) continue;
            }

            Block currentBlock = this.world.getBlockState(current).getBlock();
            if (currentBlock != this.startBlock) continue;

            if (current.getY() < this.lowestCoords.getY()) {
                this.lowestCoords = current;
            }
            this.blockCluster.add(current);

            // 26-connectivity: all face/edge/corner neighbours (preserves original behaviour for ore veins)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbour = new BlockPos(
                                current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        if (visited.add(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
            }
        }
    }

    /** O(n) match using a temporary HashSet instead of O(n²) ArrayList.contains chain. */
    public boolean matchesCluster(ResourceCluster cluster) {
        if (this.blockCluster.size() != cluster.blockCluster.size()) {
            return false;
        }
        HashSet<BlockPos> mySet = new HashSet<BlockPos>(this.blockCluster);
        for (BlockPos pos : cluster.blockCluster) {
            if (!mySet.contains(pos)) return false;
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
