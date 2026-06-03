package com.spege.helpfulvillagers.village;

import java.util.ArrayList;
import java.util.List;

import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A rancher's guild hall: a fenced pasture whose enclosed animals form the managed herd. */
@SuppressWarnings("null")
public class RanchGuildHall extends GuildHall {
    private int minX;
    private int minZ;
    private int minY;
    private int maxY;
    private int maxX;
    private int maxZ;
    public ArrayList<EntityAnimal> herd = new ArrayList<EntityAnimal>();
    public ArrayList<EntityAnimal> checkedAnimals = new ArrayList<EntityAnimal>();
    public ArrayList<BlockPos> pastureCoords = new ArrayList<BlockPos>();

    public RanchGuildHall(World world, HelpfulVillage village) {
        super(world, village);
    }

    public void findPastureCoords() {
        for (BlockPos coords : this.insideCoords) {
            for (BlockPos adjCoords : AIHelper.getAdjacentCoords(coords)) {
                if (this.pastureCoords.contains(adjCoords) || !this.isFence(adjCoords)) {
                    continue;
                }
                this.pastureCoords.add(adjCoords);
                this.minX = adjCoords.getX();
                this.maxX = adjCoords.getX();
                this.minY = adjCoords.getY();
                this.maxY = adjCoords.getY();
                this.minZ = adjCoords.getZ();
                this.maxZ = adjCoords.getZ();
                this.findNextFence(adjCoords);
                for (int i = this.minX; i <= this.maxX; ++i) {
                    for (int j = this.minY; j <= this.maxY; ++j) {
                        for (int k = this.minZ; k <= this.maxZ; ++k) {
                            BlockPos fillCoords = new BlockPos(i, j, k);
                            if (!this.pastureCoords.contains(fillCoords)) {
                                this.pastureCoords.add(fillCoords);
                            }
                        }
                    }
                }
                AxisAlignedBB box = new AxisAlignedBB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
                List<EntityAnimal> animals = this.worldObj.getEntitiesWithinAABB(EntityAnimal.class, box);
                for (EntityAnimal animal : animals) {
                    if (!this.herd.contains(animal)) {
                        this.herd.add(animal);
                    }
                }
            }
        }
    }

    private boolean isFence(BlockPos coords) {
        Block block = this.worldObj.getBlockState(coords).getBlock();
        return block instanceof BlockFence || block instanceof BlockFenceGate || block instanceof BlockDoor;
    }

    private void findNextFence(BlockPos coords) {
        for (BlockPos fenceAdjCoords : AIHelper.getAdjacentCoords(coords)) {
            if (!this.isFence(fenceAdjCoords) || this.pastureCoords.contains(fenceAdjCoords)) {
                continue;
            }
            this.pastureCoords.add(fenceAdjCoords);
            if (fenceAdjCoords.getX() < this.minX) {
                this.minX = fenceAdjCoords.getX();
            } else if (fenceAdjCoords.getX() > this.maxX) {
                this.maxX = fenceAdjCoords.getX();
            }
            if (fenceAdjCoords.getY() < this.minY) {
                this.minY = fenceAdjCoords.getY();
            } else if (fenceAdjCoords.getY() > this.maxY) {
                this.maxY = fenceAdjCoords.getY();
            }
            if (fenceAdjCoords.getZ() < this.minZ) {
                this.minZ = fenceAdjCoords.getZ();
            } else if (fenceAdjCoords.getZ() > this.maxZ) {
                this.maxZ = fenceAdjCoords.getZ();
            }
            this.findNextFence(fenceAdjCoords);
        }
    }

    public void checkPasture() {
        this.pastureCoords.clear();
        this.findPastureCoords();
    }
}
