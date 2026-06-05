package com.spege.helpfulvillagers.util;

import java.util.ArrayList;

import com.spege.helpfulvillagers.block.BlockActiveConstructionFence;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumConstructionType;
import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.PlayerMessagePacket;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

public class ConstructionSite {
    private World world;
    private AxisAlignedBB bounds;
    private Block[][][] blocks;
    private EnumConstructionType jobType;
    private int[] currentIndex;
    private ArrayList<BlockPos> fenceCoords = new ArrayList<BlockPos>();
    private boolean finished = false;

    public ConstructionSite(World world) {
        this.world = world;
    }

    public ConstructionSite(World world, AxisAlignedBB bounds, EnumConstructionType jobType) {
        this.world = world;
        this.bounds = bounds;
        this.jobType = jobType;
        this.currentIndex = new int[]{0, 0, 0};
        this.blocks = new Block[(int)(bounds.maxX - bounds.minX + 1.0)][(int)(bounds.maxY - bounds.minY + 1.0)][(int)(bounds.maxZ - bounds.minZ + 1.0)];
        this.loadBlocks();
    }

    public ConstructionSite(World world, AxisAlignedBB bounds, EnumConstructionType jobType, Object blueprint) {
        this.world = world;
        this.bounds = bounds;
        this.jobType = jobType;
        this.currentIndex = new int[]{0, 0, 0};
        this.blocks = new Block[(int)(bounds.maxX - bounds.minX + 1.0)][(int)(bounds.maxY - bounds.minY + 1.0)][(int)(bounds.maxZ - bounds.minZ + 1.0)];
        this.loadBlocks();
    }

    private void loadBlocks() {
        for (int i = 0; i < this.blocks.length; ++i) {
            for (int j = 0; j < this.blocks[i].length; ++j) {
                block6: for (int k = 0; k < this.blocks[i][j].length; ++k) {
                    BlockPos currCoords = new BlockPos((int)this.bounds.minX + i, (int)this.bounds.minY + j, (int)this.bounds.minZ + k);
                    Block block = this.world.getBlockState(currCoords).getBlock();
                    switch (this.jobType) {
                        case DEMOLISH: {
                            this.blocks[i][j][k] = Blocks.AIR;
                            continue block6;
                        }
                        case RECORD: {
                            this.blocks[i][j][k] = null;
                            continue block6;
                        }
                        default: {
                            System.out.println("Construction Job Type Not Found");
                        }
                    }
                }
            }
        }
    }

    public void doJob(AbstractVillager villager) {
        if (this.finished) {
            return;
        }
        for (int i = this.currentIndex[0]; i < this.blocks.length; ++i) {
            for (int j = this.currentIndex[1]; j < this.blocks[i].length; ++j) {
                block6: for (int k = this.currentIndex[2]; k < this.blocks[i][j].length; ++k) {
                    BlockPos currCoords = new BlockPos((int)this.bounds.minX + i, (int)this.bounds.minY + j, (int)this.bounds.minZ + k);
                    Block block = this.world.getBlockState(currCoords).getBlock();
                    if (block == this.blocks[i][j][k] || block instanceof BlockActiveConstructionFence) continue;
                    switch (this.jobType) {
                        case DEMOLISH: {
                            if (block instanceof BlockLiquid) {
                                this.jobFinished();
                                continue block6;
                            }
                            AIHelper.breakBlock(currCoords, villager);
                            break;
                        }
                        case RECORD: {
                            if (block instanceof BlockLiquid) {
                                this.blocks[i][j][k] = Blocks.AIR;
                                this.jobFinished();
                                continue block6;
                            }
                            this.blocks[i][j][k] = block;
                            break;
                        }
                        default: {
                            System.out.println("Construction Job Type Not Found");
                        }
                    }
                    this.currentIndex[0] = i;
                    this.currentIndex[1] = j;
                    this.currentIndex[2] = k;
                    return;
                }
            }
        }
        this.jobFinished();
    }

    private void jobFinished() {
        for (int i = 0; i < this.blocks.length; ++i) {
            for (int j = 0; j < this.blocks[i].length; ++j) {
                for (int k = 0; k < this.blocks[i][j].length; ++k) {
                    BlockPos currCoords = new BlockPos((int)this.bounds.minX + i, (int)this.bounds.minY + j, (int)this.bounds.minZ + k);
                    Block block = this.world.getBlockState(currCoords).getBlock();
                    if (block instanceof BlockActiveConstructionFence) {
                        if (this.fenceCoords.contains(currCoords)) continue;
                        this.fenceCoords.add(currCoords);
                        continue;
                    }
                    if (block == this.blocks[i][j][k] || block instanceof BlockLiquid) continue;
                    this.currentIndex[0] = i;
                    this.currentIndex[1] = j;
                    this.currentIndex[2] = k;
                    return;
                }
            }
        }
        BlockPos coords = new BlockPos((int)this.bounds.minX, (int)this.bounds.minY, (int)this.bounds.minZ);
        HelpfulVillagers.network.sendToAll(new PlayerMessagePacket("Construction Job Finished", EnumMessage.CONSTRUCTION, coords));
        this.finished = true;
        this.removeFences();
    }

    private void removeFences() {
        for (BlockPos coords : this.fenceCoords) {
            this.world.setBlockToAir(coords);
        }
        this.fenceCoords.clear();
    }

    public BlockPos getCenter() {
        int x = (int)(this.bounds.minX + (this.bounds.maxX - this.bounds.minX) / 2.0);
        int y = (int)(this.bounds.minY + (this.bounds.maxY - this.bounds.minY) / 2.0);
        int z = (int)(this.bounds.minZ + (this.bounds.maxZ - this.bounds.minZ) / 2.0);
        return new BlockPos(x, y, z);
    }

    public AxisAlignedBB getBounds() {
        return this.bounds;
    }

    public EnumConstructionType getJobType() {
        return this.jobType;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void cancelConstruction() {
        this.finished = true;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        int[] boundsArr = new int[]{(int)this.bounds.minX, (int)this.bounds.minY, (int)this.bounds.minZ, (int)this.bounds.maxX, (int)this.bounds.maxY, (int)this.bounds.maxZ};
        compound.setTag("Bounds", new NBTTagIntArray(boundsArr));
        compound.setInteger("Job Type", this.jobType.ordinal());
        compound.setTag("Current Index", new NBTTagIntArray(this.currentIndex));
        NBTTagList nbtTagList = new NBTTagList();
        for (int i = 0; i < this.blocks.length; ++i) {
            for (int j = 0; j < this.blocks[i].length; ++j) {
                for (int k = 0; k < this.blocks[i][j].length; ++k) {
                    String index = i + " " + j + " " + k;
                    NBTTagCompound nbttagcompound = new NBTTagCompound();
                    nbttagcompound.setString("Index", index);
                    nbttagcompound.setInteger("Block", Block.getIdFromBlock(this.blocks[i][j][k]));
                    nbtTagList.appendTag(nbttagcompound);
                }
            }
        }
        compound.setTag("Blocks", nbtTagList);
        return compound;
    }

    public void readFromNBT(NBTTagCompound compound) {
        int[] boundsArr = compound.getIntArray("Bounds");
        if (boundsArr.length == 6) {
            this.bounds = new AxisAlignedBB((double)boundsArr[0], (double)boundsArr[1], (double)boundsArr[2], (double)boundsArr[3], (double)boundsArr[4], (double)boundsArr[5]);
        } else {
            this.bounds = new AxisAlignedBB(0, 0, 0, 0, 0, 0);
        }
        this.jobType = EnumConstructionType.values()[compound.getInteger("Job Type")];
        this.currentIndex = compound.getIntArray("Current Index");
        if (this.currentIndex.length != 3) {
            this.currentIndex = new int[]{0, 0, 0};
        }
        this.blocks = new Block[(int)(this.bounds.maxX - this.bounds.minX + 1.0)][(int)(this.bounds.maxY - this.bounds.minY + 1.0)][(int)(this.bounds.maxZ - this.bounds.minZ + 1.0)];
        NBTTagList nbtTagList = compound.getTagList("Blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nbtTagList.tagCount(); ++i) {
            NBTTagCompound nbttagcompound = nbtTagList.getCompoundTagAt(i);
            String sIndex = nbttagcompound.getString("Index");
            String[] sIndices = sIndex.split(" ");
            if (sIndices.length == 3) {
                int[] iIndices = new int[]{Integer.parseInt(sIndices[0]), Integer.parseInt(sIndices[1]), Integer.parseInt(sIndices[2])};
                this.blocks[iIndices[0]][iIndices[1]][iIndices[2]] = Block.getBlockById(nbttagcompound.getInteger("Block"));
            }
        }
    }

    public static ConstructionSite loadSiteFromNBT(NBTTagCompound compound, World world) {
        ConstructionSite site = new ConstructionSite(world);
        site.readFromNBT(compound);
        return site;
    }
}
