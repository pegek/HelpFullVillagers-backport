package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIFarmer;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.ResourceCluster;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;

/** Farmer villager: tills/harvests crops, replants seeds, crafts food/paper. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityFarmer extends AbstractVillager {
    private final ItemStack[] farmerTools = new ItemStack[] {
            new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.GOLDEN_HOE), new ItemStack(Items.IRON_HOE),
            new ItemStack(Items.STONE_HOE), new ItemStack(Items.WOODEN_HOE) };

    public static final ItemStack[] farmerCraftables = new ItemStack[] {
            new ItemStack(Blocks.LIT_PUMPKIN), new ItemStack(Blocks.HAY_BLOCK), new ItemStack(Blocks.MELON_BLOCK),
            new ItemStack(Items.CAKE), new ItemStack(Items.BREAD), new ItemStack(Items.COOKIE),
            new ItemStack(Items.BREAD), new ItemStack(Items.MUSHROOM_STEW), new ItemStack(Items.BREAD),
            new ItemStack(Items.PUMPKIN_SEEDS), new ItemStack(Items.MELON_SEEDS), new ItemStack(Items.SUGAR),
            new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.PAPER), new ItemStack(Items.BOOK) };

    public static final ItemStack[] farmerSmeltables = new ItemStack[] { new ItemStack(Items.BAKED_POTATO) };

    public ArrayList<ResourceCluster> visitedFarms = new ArrayList<ResourceCluster>();
    public IPlantable seedToPlant;
    public int lastSeedIndex;

    public EntityFarmer(World world) {
        super(world);
        this.init();
    }

    public EntityFarmer(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 3;
        this.profName = "Farmer";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.seedToPlant = null;
        this.lastSeedIndex = 0;
        this.knownRecipes.addAll(HelpfulVillagers.farmerRecipes);
        this.setTools(this.farmerTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(2, new EntityAIFarmer(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    public int getHarvestTime() {
        ItemStack current = this.getCurrentItem();
        if (current.isEmpty()) {
            return 60;
        }
        if (!(current.getItem() instanceof ItemHoe)) {
            return 60;
        }
        ItemStack hoe = current;
        if (hoe.getDisplayName().equals(new ItemStack(Items.WOODEN_HOE).getDisplayName())) {
            return 50;
        }
        if (hoe.getDisplayName().equals(new ItemStack(Items.STONE_HOE).getDisplayName())) {
            return 40;
        }
        if (hoe.getDisplayName().equals(new ItemStack(Items.IRON_HOE).getDisplayName())) {
            return 30;
        }
        if (hoe.getDisplayName().equals(new ItemStack(Items.GOLDEN_HOE).getDisplayName())) {
            return 20;
        }
        if (hoe.getDisplayName().equals(new ItemStack(Items.DIAMOND_HOE).getDisplayName())) {
            return 10;
        }
        return 50;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        this.updateBoxes();
        ArrayList<BlockPos> coords = new ArrayList<BlockPos>();
        AxisAlignedBB box = this.searchBox;
        for (int x = (int) box.minX; x <= box.maxX; ++x) {
            for (int y = (int) box.minY; y <= box.maxY; ++y) {
                for (int z = (int) box.minZ; z <= box.maxZ; ++z) {
                    Block block = this.world.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (block == Blocks.FARMLAND || block == Blocks.SOUL_SAND || block == Blocks.REEDS) {
                        coords.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return coords;
    }

    @Override
    protected void dayCheck() {
        super.dayCheck();
        this.visitedFarms.clear();
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemHoe;
    }

    @Override
    protected boolean canCraft() {
        return true;
    }
}
