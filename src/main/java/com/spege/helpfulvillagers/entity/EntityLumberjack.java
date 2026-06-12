package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.List;

import com.spege.helpfulvillagers.ai.EntityAILumberjack;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.SaplingPacket;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Lumberjack villager: chops trees, replants saplings, crafts wood products. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityLumberjack extends AbstractVillager {
    public boolean foundTree;
    /** Synced to client via SaplingPacket so the client model can show a sapling in hand. */
    public boolean shouldPlant;

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
        this.knownRecipes.addAll(HelpfulVillagers.lumberjackRecipes);
        this.setTools(this.lumberjackTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        // No setBreakDoors(false): see EntitySoldier.addThisAI — it would wall off closed doors.
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
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
        // Note: sapling planting is now handled inside EntityAILumberjack (Thrall-style stump
        // replant). shouldPlant is kept and synced so the client can show the sapling-in-hand
        // visual if desired; the crude onUpdate plantSapling() has been removed.
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
        // Kept to set shouldPlant for client-side visual sync (SaplingPacket).
        // Actual planting is now done by EntityAILumberjack (stump replant after each tree).
        if (this.homeVillage != null && !this.world.isRemote) {
            this.shouldPlant = this.inventory.containsItem(new ItemStack(Blocks.SAPLING)) >= 0;
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
