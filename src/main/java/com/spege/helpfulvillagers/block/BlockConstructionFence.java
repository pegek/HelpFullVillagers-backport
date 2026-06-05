package com.spege.helpfulvillagers.block;

import com.spege.helpfulvillagers.tileentity.TileEntityContructionFence;

import net.minecraft.block.BlockFence;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockConstructionFence extends BlockFence implements ITileEntityProvider {
    public BlockConstructionFence(String name, Material material) {
        super(material, material.getMaterialMapColor());
        this.setUnlocalizedName(name);
        this.setRegistryName(name);
        this.setCreativeTab(CreativeTabs.DECORATIONS);
        this.setHardness(2.0f);
        this.setResistance(5.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityContructionFence();
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (placer instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) placer;
            TileEntity tile = worldIn.getTileEntity(pos);
            if (tile instanceof TileEntityContructionFence) {
                ((TileEntityContructionFence) tile).player = player.getName();
            }
        }
    }
}
