package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.List;

import com.spege.helpfulvillagers.ai.EntityAILumberjack;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.SaplingPacket;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Lumberjack villager: chops trees, replants saplings, crafts wood products. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityLumberjack extends AbstractVillager {
    public boolean foundTree;
    public boolean shouldPlant;
    private int previousTime;
    private int currentTime;

    private final ItemStack[] lumberjackTools = new ItemStack[] {
            new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.GOLDEN_AXE), new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.STONE_AXE), new ItemStack(Items.WOODEN_AXE) };

    public static final ItemStack[] lumberjackCraftables = new ItemStack[] {
            new ItemStack(Blocks.PLANKS), new ItemStack(Blocks.CRAFTING_TABLE), new ItemStack(Blocks.CHEST),
            new ItemStack(Blocks.LADDER), new ItemStack(Blocks.OAK_FENCE), new ItemStack(Blocks.WOODEN_SLAB),
            new ItemStack(Blocks.BOOKSHELF), new ItemStack(Blocks.NOTEBLOCK), new ItemStack(Blocks.ACACIA_STAIRS),
            new ItemStack(Blocks.OAK_STAIRS), new ItemStack(Blocks.DARK_OAK_STAIRS), new ItemStack(Blocks.SPRUCE_STAIRS),
            new ItemStack(Blocks.BIRCH_STAIRS), new ItemStack(Blocks.JUNGLE_STAIRS),
            new ItemStack(Blocks.WOODEN_PRESSURE_PLATE), new ItemStack(Blocks.WOODEN_BUTTON),
            new ItemStack(Blocks.TRAPDOOR), new ItemStack(Blocks.OAK_FENCE_GATE), new ItemStack(Blocks.JUKEBOX),
            new ItemStack(Blocks.DAYLIGHT_DETECTOR), new ItemStack(Blocks.TRIPWIRE_HOOK),
            new ItemStack(Blocks.TRAPPED_CHEST), new ItemStack(Items.STICK), new ItemStack(Items.BOAT),
            new ItemStack(Items.SIGN), new ItemStack(Items.PAINTING), new ItemStack(Items.OAK_DOOR),
            new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WOODEN_PICKAXE),
            new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.BOW),
            new ItemStack(Items.ARROW), new ItemStack(Items.BOWL), new ItemStack(Items.FISHING_ROD),
            new ItemStack(Items.CARROT_ON_A_STICK), new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.BED) };

    public EntityLumberjack(World world) {
        super(world);
        this.init();
    }

    public EntityLumberjack(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 1;
        this.profName = "Lumberjack";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.foundTree = false;
        this.shouldPlant = false;
        this.previousTime = 0;
        this.currentTime = 0;
        this.knownRecipes.addAll(HelpfulVillagers.lumberjackRecipes);
        this.setTools(this.lumberjackTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityZombie>(this, EntityZombie.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(2, new EntityAILumberjack(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    public void sync() {
        if (!this.world.isRemote) {
            HelpfulVillagers.network.sendToAll(new SaplingPacket(this.getEntityId(), this.shouldPlant));
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.pickupSaplings();
        this.shouldPlantSapling();
        this.sync();
        if (this.shouldPlant) {
            this.plantSapling();
        }
    }

    @Override
    protected void dayCheck() {
        super.dayCheck();
        if (this.foundTree) {
            this.foundTree = false;
        } else {
            this.lastResource = null;
        }
    }

    private void shouldPlantSapling() {
        if (this.homeVillage != null && !this.world.isRemote) {
            this.shouldPlant = !AIHelper.isInRangeOfAnyVillage(this.posX, this.posY, this.posZ) && !this.nearHall();
        }
    }

    private void plantSapling() {
        int index = this.inventory.containsItem(new ItemStack(Blocks.SAPLING));
        if (this.previousTime <= 0) {
            this.previousTime = this.ticksExisted;
        }
        this.currentTime = this.ticksExisted;
        if (this.currentTime - this.previousTime >= 200) {
            this.previousTime = 0;
            if (index >= 0) {
                int y = (int) this.posY;
                while (true) {
                    Block air = this.world.getBlockState(new BlockPos((int) this.posX, y, (int) this.posZ)).getBlock();
                    Block dirt = this.world.getBlockState(new BlockPos((int) this.posX, y - 1, (int) this.posZ)).getBlock();
                    if (air == Blocks.AIR && (dirt == Blocks.GRASS || dirt == Blocks.DIRT)) {
                        ItemStack saplingItem = this.inventory.getStackInSlot(index);
                        int metadata = saplingItem.getMetadata();
                        Block saplingBlock = Block.getBlockFromItem(saplingItem.getItem());
                        this.world.setBlockState(new BlockPos((int) this.posX, y, (int) this.posZ),
                                saplingBlock.getStateFromMeta(metadata), 2);
                        this.inventory.decrementSlot(index);
                        return;
                    }
                    if (air != Blocks.AIR) {
                        return;
                    }
                    --y;
                }
            }
        }
    }

    private void pickupSaplings() {
        if (this.inventory.isFull()) {
            return;
        }
        List<EntityItem> items = this.world.getEntitiesWithinAABB(EntityItem.class, this.searchBox);
        for (EntityItem currentItem : items) {
            ItemStack currentStack = currentItem.getItem();
            if (currentStack.isEmpty() || !currentStack.getDisplayName().contains("Sapling")) {
                continue;
            }
            this.inventory.addItem(currentItem.getItem());
            currentItem.setDead();
        }
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
                    if (block instanceof BlockLog) {
                        coords.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return coords;
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemAxe;
    }

    @Override
    protected boolean canCraft() {
        return true;
    }
}
