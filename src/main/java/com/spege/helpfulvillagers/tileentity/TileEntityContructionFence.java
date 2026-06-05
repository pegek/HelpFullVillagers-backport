package com.spege.helpfulvillagers.tileentity;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.util.ResourceLocation;

public class TileEntityContructionFence extends TileEntity {
    public String player = "";
    private final int MAX_LENGTH = 64;
    private ArrayList<BlockPos> foundCoords = new ArrayList<BlockPos>();

    public AxisAlignedBB setupConstructionSite(World world, BlockPos coords) {
        this.checkCoords(world, coords);
        AxisAlignedBB box = this.constructBox(world);
        this.foundCoords.clear();
        return box;
    }

    private void checkCoords(World world, BlockPos coords) {
        this.foundCoords.add(coords);
        for (int i = 1; i < MAX_LENGTH; ++i) {
            this.checkX(world, coords, i);
            this.checkY(world, coords, i);
            this.checkZ(world, coords, i);
        }
    }

    private void checkX(World world, BlockPos coords, int i) {
        BlockPos newCoords = new BlockPos(coords.getX() - i, coords.getY(), coords.getZ());
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
        newCoords = new BlockPos(coords.getX() + i, coords.getY(), coords.getZ());
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
    }

    private void checkY(World world, BlockPos coords, int i) {
        BlockPos newCoords = new BlockPos(coords.getX(), coords.getY() - i, coords.getZ());
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
        newCoords = new BlockPos(coords.getX(), coords.getY() + i, coords.getZ());
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
    }

    private void checkZ(World world, BlockPos coords, int i) {
        BlockPos newCoords = new BlockPos(coords.getX(), coords.getY(), coords.getZ() - i);
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
        newCoords = new BlockPos(coords.getX(), coords.getY(), coords.getZ() + i);
        if (this.foundCoords.contains(newCoords)) {
            return;
        }
        if (this.isValidBlock(world, newCoords)) {
            this.checkCoords(world, newCoords);
        }
    }

    private boolean isValidBlock(World world, BlockPos coords) {
        TileEntity tileEntity = world.getTileEntity(coords);
        if (tileEntity != null && tileEntity instanceof TileEntityContructionFence) {
            TileEntityContructionFence constructionFence = (TileEntityContructionFence) tileEntity;
            return constructionFence.player.equals(this.player);
        }
        return false;
    }

    private AxisAlignedBB constructBox(World world) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos coords : this.foundCoords) {
            if (coords.getX() < minX) {
                minX = coords.getX();
            }
            if (coords.getX() > maxX) {
                maxX = coords.getX();
            }
            if (coords.getY() < minY) {
                minY = coords.getY();
            }
            if (coords.getY() > maxY) {
                maxY = coords.getY();
            }
            if (coords.getZ() < minZ) {
                minZ = coords.getZ();
            }
            if (coords.getZ() > maxZ) {
                maxZ = coords.getZ();
            }
        }
        if (minX != maxX && minY != maxY && minZ != maxZ) {
            int i;
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("helpfulvillagers", "active_construction_fence"));
            for (i = minX; i <= maxX; ++i) {
                world.setBlockState(new BlockPos(i, minY, minZ), block.getDefaultState());
                world.setBlockState(new BlockPos(i, minY, maxZ), block.getDefaultState());
                world.setBlockState(new BlockPos(i, maxY, minZ), block.getDefaultState());
                world.setBlockState(new BlockPos(i, maxY, maxZ), block.getDefaultState());
            }
            for (i = minZ; i <= maxZ; ++i) {
                world.setBlockState(new BlockPos(minX, minY, i), block.getDefaultState());
                world.setBlockState(new BlockPos(maxX, minY, i), block.getDefaultState());
                world.setBlockState(new BlockPos(minX, maxY, i), block.getDefaultState());
                world.setBlockState(new BlockPos(maxX, maxY, i), block.getDefaultState());
            }
            for (i = minY; i <= maxY; ++i) {
                world.setBlockState(new BlockPos(minX, i, minZ), block.getDefaultState());
                world.setBlockState(new BlockPos(maxX, i, minZ), block.getDefaultState());
                world.setBlockState(new BlockPos(minX, i, maxZ), block.getDefaultState());
                world.setBlockState(new BlockPos(maxX, i, maxZ), block.getDefaultState());
            }
        } else {
            System.out.println("Invalid Box");
            System.out.println("min x: " + minX);
            System.out.println("min y: " + minY);
            System.out.println("min z: " + minZ);
            System.out.println("max x: " + maxX);
            System.out.println("max y: " + maxY);
            System.out.println("max z: " + maxZ);
            return null;
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("player", this.player);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.player = compound.getString("player");
    }
}
