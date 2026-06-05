package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIBuilder;
import com.spege.helpfulvillagers.block.ModBlocks;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.enums.EnumConstructionType;
import com.spege.helpfulvillagers.tileentity.TileEntityContructionFence;
import com.spege.helpfulvillagers.util.ConstructionSite;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class EntityBuilder extends AbstractVillager {
    public ConstructionSite currentSite;
    private final ItemStack[] builderTools = new ItemStack[]{
        new ItemStack(Items.WOODEN_SHOVEL), 
        new ItemStack(Items.STONE_SHOVEL), 
        new ItemStack(Items.IRON_SHOVEL), 
        new ItemStack(Items.GOLDEN_SHOVEL), 
        new ItemStack(Items.DIAMOND_SHOVEL)
    };

    public EntityBuilder(World world) {
        super(world);
        this.init();
    }

    public EntityBuilder(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 9;
        this.profName = "Builder";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 3;
        this.setTools(this.builderTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        ((net.minecraft.pathfinding.PathNavigateGround)this.getNavigator()).setBreakDoors(false);
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityZombie>(this, EntityZombie.class, 8.0f, 0.3D, 0.35D));
        this.tasks.addTask(2, new EntityAIBuilder(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    public void processJobRequest(EnumConstructionType type, EntityPlayer player) {
        ArrayList<BlockPos> coords = this.getValidCoords();
        boolean success = false;
        if (coords.size() == 0) {
            player.sendMessage(new TextComponentString("There is no construction fence nearby."));
        } else {
            for (BlockPos coord : coords) {
                try {
                    TileEntityContructionFence fence = (TileEntityContructionFence) this.world.getTileEntity(coord);
                    if (fence == null) continue;
                    AxisAlignedBB box = fence.setupConstructionSite(this.world, coord);
                    if (box == null) continue;
                    success = true;
                    this.currentSite = new ConstructionSite(this.world, box, type);
                    this.homeVillage.constructionSites.add(this.currentSite);
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            if (!success) {
                player.sendMessage(new TextComponentString("Something's not right. Double check your construction fences."));
            }
        }
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemSpade;
    }

    @Override
    public boolean canCraft() {
        return false;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        this.updateBoxes();
        ArrayList<BlockPos> coords = new ArrayList<BlockPos>();
        AxisAlignedBB searchBox = this.searchBox;
        int x = (int) searchBox.minX;
        while ((double) x <= searchBox.maxX) {
            int y = (int) searchBox.minY;
            while ((double) y <= searchBox.maxY) {
                int z = (int) searchBox.minZ;
                while ((double) z <= searchBox.maxZ) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = this.world.getBlockState(pos).getBlock();
                    if (block == ModBlocks.construction_fence) {
                        coords.add(pos);
                    }
                    ++z;
                }
                ++y;
            }
            ++x;
        }
        return coords;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (this.currentSite != null) {
            compound.setTag("Site", this.currentSite.writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        try {
            if (compound.hasKey("Site")) {
                NBTTagCompound siteCompound = compound.getCompoundTag("Site");
                this.currentSite = ConstructionSite.loadSiteFromNBT(siteCompound, this.world);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
