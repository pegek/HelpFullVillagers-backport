package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.Collections;

import com.spege.helpfulvillagers.ai.EntityAIMiner;
import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

/** Miner villager: digs mineshafts, mines ores, smelts ingots, crafts tools/redstone/minecarts. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityMiner extends AbstractVillager {
    private final ItemStack[] minerTools = new ItemStack[] {
            new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
            new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.WOODEN_PICKAXE) };

    public static final ItemStack[] minerCraftables = new ItemStack[] {
            new ItemStack(Blocks.FURNACE), new ItemStack(Blocks.STONE_SLAB), new ItemStack(Blocks.STONE),
            new ItemStack(Blocks.STONEBRICK), new ItemStack(Blocks.STONE_BRICK_STAIRS), new ItemStack(Blocks.STONE_STAIRS),
            new ItemStack(Blocks.STONE_BUTTON), new ItemStack(Blocks.STONE_PRESSURE_PLATE),
            new ItemStack(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE), new ItemStack(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE),
            new ItemStack(Blocks.LEVER), new ItemStack(Blocks.REDSTONE_LAMP), new ItemStack(Blocks.REDSTONE_TORCH),
            new ItemStack(Blocks.RAIL), new ItemStack(Blocks.GOLDEN_RAIL), new ItemStack(Blocks.DETECTOR_RAIL),
            new ItemStack(Blocks.ACTIVATOR_RAIL), new ItemStack(Blocks.COAL_BLOCK), new ItemStack(Blocks.IRON_BLOCK),
            new ItemStack(Blocks.GOLD_BLOCK), new ItemStack(Blocks.REDSTONE_BLOCK), new ItemStack(Blocks.LAPIS_BLOCK),
            new ItemStack(Blocks.EMERALD_BLOCK), new ItemStack(Blocks.DIAMOND_BLOCK), new ItemStack(Blocks.COBBLESTONE_WALL),
            new ItemStack(Blocks.PISTON), new ItemStack(Blocks.STICKY_PISTON), new ItemStack(Blocks.DISPENSER),
            new ItemStack(Blocks.DROPPER), new ItemStack(Blocks.HOPPER), new ItemStack(Blocks.IRON_BARS),
            new ItemStack(Blocks.ENCHANTING_TABLE), new ItemStack(Blocks.ANVIL), new ItemStack(Blocks.TORCH),
            new ItemStack(Items.IRON_DOOR), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.IRON_HOE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL),
            new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
            new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.GOLDEN_AXE),
            new ItemStack(Items.GOLDEN_HOE), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.GOLDEN_SHOVEL),
            new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_HOE),
            new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_SWORD),
            new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.SHEARS), new ItemStack(Items.BUCKET),
            new ItemStack(Items.CLOCK), new ItemStack(Items.COMPASS), new ItemStack(Items.REPEATER),
            new ItemStack(Items.COMPARATOR), new ItemStack(Items.MINECART), new ItemStack(Items.FURNACE_MINECART),
            new ItemStack(Items.CHEST_MINECART), new ItemStack(Items.HOPPER_MINECART), new ItemStack(Items.TNT_MINECART),
            new ItemStack(Items.CAULDRON), new ItemStack(Items.BREWING_STAND) };

    public static final ItemStack[] minerSmeltables = new ItemStack[] {
            new ItemStack(Blocks.STONE), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT) };

    private final Block[] excludeBlocksArray = new Block[] {
            Blocks.AIR, Blocks.SAND, Blocks.GRAVEL, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE,
            Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.REDSTONE_ORE, Blocks.EMERALD_ORE };

    public ArrayList<Block> excludeBlocks = new ArrayList<Block>();
    public BlockPos target;
    public ArrayList<BlockPos> shaftCoords = new ArrayList<BlockPos>();
    public BlockPos topCoords;
    public int topDir;
    public int shaftIndex;
    public ArrayList<BlockPos> digCoords = new ArrayList<BlockPos>();
    public boolean dugSection;
    public ArrayList<BlockPos> tunnelCoords = new ArrayList<BlockPos>();
    public ArrayList<BlockPos> returnPath = new ArrayList<BlockPos>();
    public boolean beingFollowed;
    public boolean swingingPickaxe;
    private int suffocationCount;

    public EntityMiner(World world) {
        super(world);
        this.init();
    }

    public EntityMiner(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.target = null;
        this.topCoords = null;
        this.topDir = 0;
        this.shaftIndex = 0;
        this.dugSection = false;
        this.beingFollowed = false;
        this.swingingPickaxe = false;
        this.suffocationCount = 0;
        this.profession = 2;
        this.profName = "Miner";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 5;
        this.knownRecipes.addAll(HelpfulVillagers.minerRecipes);
        this.addHorseArmorRecipes();
        this.setTools(this.minerTools);
        this.getNewGuildHall();
        this.addExcludeBlocks();
        this.addThisAI();
    }

    private void addHorseArmorRecipes() {
        if (!this.world.isRemote) {
            ArrayList<ItemStack> inputs = new ArrayList<ItemStack>();
            inputs.add(new ItemStack(Blocks.WOOL, 1));
            inputs.add(new ItemStack(Items.IRON_INGOT, 6));
            this.knownRecipes.add(new VillagerRecipe(inputs, new ItemStack(Items.IRON_HORSE_ARMOR), false));
            inputs.set(1, new ItemStack(Items.GOLD_INGOT, 6));
            this.knownRecipes.add(new VillagerRecipe(inputs, new ItemStack(Items.GOLDEN_HORSE_ARMOR), false));
            inputs.set(1, new ItemStack(Items.DIAMOND, 6));
            this.knownRecipes.add(new VillagerRecipe(inputs, new ItemStack(Items.DIAMOND_HORSE_ARMOR), false));
            Collections.sort(this.knownRecipes);
        }
    }

    private void addExcludeBlocks() {
        for (int i = 0; i < this.excludeBlocksArray.length; ++i) {
            this.excludeBlocks.add(this.excludeBlocksArray[i]);
        }
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(true);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(2, new EntityAIMiner(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (this.getHealth() >= this.getMaxHealth()) {
            this.suffocationCount = 0;
        }
        this.searchRadius = this.posY <= 30.0 ? 8 : 5;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        this.updateBoxes();
        ArrayList<BlockPos> coords = new ArrayList<BlockPos>();
        AxisAlignedBB box = this.searchBox;
        for (int x = (int) box.minX; x <= box.maxX; ++x) {
            for (int z = (int) box.minZ; z <= box.maxZ; ++z) {
                Block block = this.world.getBlockState(new BlockPos(x, (int) this.posY, z)).getBlock();
                // Guard: empty stacks (air / itemless blocks) make 1.12.2 getOreIDs throw.
                ItemStack blockStack = new ItemStack(block);
                if (blockStack.isEmpty()) {
                    continue;
                }
                int[] oreDictIDs = OreDictionary.getOreIDs(blockStack);
                for (int j = 0; j < oreDictIDs.length; ++j) {
                    String name = OreDictionary.getOreName(oreDictIDs[j]);
                    if (!name.contains("ore")) {
                        continue;
                    }
                    coords.add(new BlockPos(x, (int) this.posY, z));
                    break;
                }
            }
        }
        return coords;
    }

    public int nearestShaftCoord() {
        for (int i = 0; i < this.shaftCoords.size(); ++i) {
            BlockPos currentCoords = this.shaftCoords.get(i);
            if (currentCoords.getY() != (int) this.posY) {
                continue;
            }
            return i;
        }
        if (this.topCoords != null && this.getCoords().getY() >= this.topCoords.getY()) {
            return 0;
        }
        return this.shaftCoords.size() - 1;
    }

    public boolean areCoordsInMine(BlockPos coords) {
        for (int i = 0; i < 4; ++i) {
            BlockPos check = this.shaftCoords.get(i);
            if (coords.getX() != check.getX() || coords.getZ() != check.getZ()) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean isInMine() {
        return this.areCoordsInMine(this.getCoords());
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float f) {
        if (src.equals(DamageSource.IN_WALL) && this.shaftCoords.size() > 0) {
            ++this.suffocationCount;
            if (this.suffocationCount > 2) {
                BlockPos dest = this.nearestShaftCoord() - 1 >= 0
                        ? this.shaftCoords.get(this.nearestShaftCoord() - 1)
                        : this.shaftCoords.get(this.nearestShaftCoord());
                if (Math.sqrt(this.getDistanceSq(dest.getX(), dest.getY(), dest.getZ())) <= 5.0) {
                    this.setLocationAndAngles(dest.getX(), dest.getY(), dest.getZ(), this.rotationYaw, this.rotationPitch);
                    this.suffocationCount = 0;
                }
            }
        }
        return super.attackEntityFrom(src, f);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        NBTTagList tagList = new NBTTagList();
        if (this.topCoords != null) {
            NBTTagCompound coordsCompound = new NBTTagCompound();
            int[] coords = new int[] { this.topCoords.getX(), this.topCoords.getY(), this.topCoords.getZ() };
            coordsCompound.setIntArray("Coords", coords);
            tagList.appendTag(coordsCompound);
            NBTTagCompound dirCompound = new NBTTagCompound();
            dirCompound.setInteger("Direction", this.topDir);
            tagList.appendTag(dirCompound);
            compound.setTag("Mineshaft", tagList);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("Mineshaft")) {
            NBTTagList tagList = compound.getTagList("Mineshaft", 10);
            NBTTagCompound coordsCompound = tagList.getCompoundTagAt(0);
            int[] coords = coordsCompound.getIntArray("Coords");
            this.topCoords = new BlockPos(coords[0], coords[1], coords[2]);
            NBTTagCompound dirCompound = tagList.getCompoundTagAt(1);
            this.topDir = dirCompound.getInteger("Direction");
        }
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemPickaxe;
    }

    @Override
    protected boolean canCraft() {
        return true;
    }
}
