package com.spege.helpfulvillagers.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import com.spege.helpfulvillagers.crafting.CraftTree;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.village.GuildHall;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Static AI utility helpers (random village coords, chest/furnace item transfer, block breaking,
 * stack merging). 1.12.2 migration: ChunkCoordinates -> BlockPos; ItemStack null -> ItemStack.EMPTY;
 * {@code Block.getDrops} now fills a {@link NonNullList} from an {@link IBlockState}.
 */
@SuppressWarnings({ "null", "deprecation" })
public class AIHelper {
    /** Shared RNG — avoids allocating a new Random() on every per-tick call. */
    private static final Random SHARED_RNG = new Random();

    public static BlockPos getRandOutsideCoords(AbstractVillager villager, int limit) {
        Random gen = SHARED_RNG;
        HelpfulVillage village = villager.homeVillage;
        if (villager.lastResource != null) {
            BlockPos center = villager.lastResource.coords;
            int x = center.getX();
            int y = center.getY();
            int z = center.getZ();
            int newX = gen.nextBoolean() ? x + gen.nextInt(limit / 2) : x - gen.nextInt(limit / 2);
            int newZ = gen.nextBoolean() ? z + gen.nextInt(limit / 2) : z - gen.nextInt(limit / 2);
            return new BlockPos(newX, y, newZ);
        }
        if (village != null) {
            int newZ;
            int newX;
            BlockPos center = village.getActualCenter();
            int x = center.getX();
            int y = center.getY();
            int z = center.getZ();
            if (gen.nextBoolean()) {
                newX = x - (village.getActualRadius() + 10);
                newX -= gen.nextInt(limit / 2);
            } else {
                newX = x + (village.getActualRadius() + 10);
                newX += gen.nextInt(limit / 2);
            }
            if (gen.nextBoolean()) {
                newZ = z - (village.getActualRadius() + 10);
                newZ -= gen.nextInt(limit / 2);
            } else {
                newZ = z + (village.getActualRadius() + 10);
                newZ += gen.nextInt(limit / 2);
            }
            return new BlockPos(newX, y, newZ);
        }
        return null;
    }

    public static BlockPos getRandInsideCoords(AbstractVillager villager) {
        Random gen = SHARED_RNG;
        HelpfulVillage village = villager.homeVillage;
        if (village != null) {
            BlockPos center = village.getActualCenter();
            int xRange = (int) (Math.abs(village.actualBounds.maxX - village.actualBounds.minX) + 5.0);
            int zRange = (int) (Math.abs(village.actualBounds.maxZ - village.actualBounds.minZ) + 5.0);
            int x = (int) (village.actualBounds.minX + (double) gen.nextInt(xRange));
            int y = center.getY();
            int z = (int) (village.actualBounds.minZ + (double) gen.nextInt(zRange));
            return new BlockPos(x, y, z);
        }
        return null;
    }

    public static int findDistance(int par1, int par2) {
        int temp1 = Math.abs(par1);
        int temp2 = Math.abs(par2);
        int temp3 = temp1 - temp2;
        return Math.abs(temp3);
    }

    public static int chestContains(TileEntityChest chest, ItemStack item) {
        for (int i = 0; i < chest.getSizeInventory(); ++i) {
            ItemStack chestItem = chest.getStackInSlot(i);
            if (chestItem.isEmpty()
                    || !chestItem.getItem().equals(item.getItem())
                    || chestItem.getMetadata() != item.getMetadata()) {
                continue;
            }
            return i;
        }
        return -1;
    }

    public static int chestContains(TileEntityChest chest, AbstractVillager villager) {
        for (int i = 0; i < chest.getSizeInventory(); ++i) {
            ItemStack chestItem = chest.getStackInSlot(i);
            if (chestItem.isEmpty() || !villager.isValidTool(chestItem)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    public static boolean takeItemFromChest(ItemStack item, TileEntityChest chest, AbstractVillager villager) {
        for (int i = 0; i < chest.getSizeInventory(); ++i) {
            ItemStack chestItem = chest.getStackInSlot(i);
            Block block = Block.getBlockFromItem(item.getItem());
            Block chestBlock = chestItem.isEmpty() ? Blocks.AIR : Block.getBlockFromItem(chestItem.getItem());
            if (chestItem.isEmpty() || chestItem.getCount() <= 0
                    || (!villager.currentCraftItem.isSensitive() || !chestItem.getDisplayName().equals(item.getDisplayName()))
                            && (villager.currentCraftItem.isSensitive() || !chestItem.getItem().equals(item.getItem())
                                    && (!(block instanceof BlockLog) || !(chestBlock instanceof BlockLog)))) {
                continue;
            }
            if (chestItem.getCount() >= item.getCount()) {
                chestItem.setCount(chestItem.getCount() - item.getCount());
                villager.inventory.addItem(item);
                villager.homeVillage.economy.decreaseItemSupply(villager, item);
                if (chestItem.getCount() <= 0) {
                    chestItem = ItemStack.EMPTY;
                }
                chest.setInventorySlotContents(i, chestItem);
                return true;
            }
            villager.inventory.addItem(chestItem);
            villager.homeVillage.economy.decreaseItemSupply(villager, chestItem);
            chest.setInventorySlotContents(i, ItemStack.EMPTY);
            item.setCount(item.getCount() - chestItem.getCount());
        }
        return false;
    }

    public static boolean takeItemFromChest(ItemStack item, TileEntityChest chest, AbstractVillager villager, boolean sensitive) {
        for (int i = 0; i < chest.getSizeInventory(); ++i) {
            ItemStack chestItem = chest.getStackInSlot(i);
            Block block = Block.getBlockFromItem(item.getItem());
            Block chestBlock = chestItem.isEmpty() ? Blocks.AIR : Block.getBlockFromItem(chestItem.getItem());
            if (chestItem.isEmpty() || chestItem.getCount() <= 0
                    || (!sensitive || !chestItem.getDisplayName().equals(item.getDisplayName()))
                            && (sensitive || !chestItem.getItem().equals(item.getItem())
                                    && (!(block instanceof BlockLog) || !(chestBlock instanceof BlockLog)))) {
                continue;
            }
            if (chestItem.getCount() >= item.getCount()) {
                chestItem.setCount(chestItem.getCount() - item.getCount());
                villager.inventory.addItem(item);
                if (chestItem.getCount() <= 0) {
                    chestItem = ItemStack.EMPTY;
                }
                chest.setInventorySlotContents(i, chestItem);
                return true;
            }
            villager.inventory.addItem(chestItem);
            chest.setInventorySlotContents(i, ItemStack.EMPTY);
            item.setCount(item.getCount() - chestItem.getCount());
        }
        return false;
    }

    public static boolean takeItemFromFurnace(ItemStack item, TileEntityFurnace furnace, AbstractVillager villager) {
        ItemStack furnaceItem = furnace.getStackInSlot(2);
        Block block = Block.getBlockFromItem(item.getItem());
        Block furnaceBlock = furnaceItem.isEmpty() ? Blocks.AIR : Block.getBlockFromItem(furnaceItem.getItem());
        if (!furnaceItem.isEmpty() && furnaceItem.getCount() > 0
                && (villager.currentCraftItem.isSensitive() && furnaceItem.getDisplayName().equals(item.getDisplayName())
                        || !villager.currentCraftItem.isSensitive() && (furnaceItem.getItem().equals(item.getItem())
                                || block instanceof BlockLog && furnaceBlock instanceof BlockLog))) {
            if (furnaceItem.getCount() >= item.getCount()) {
                furnaceItem.setCount(furnaceItem.getCount() - item.getCount());
                villager.inventory.addItem(item);
                if (furnaceItem.getCount() <= 0) {
                    furnaceItem = ItemStack.EMPTY;
                }
                furnace.setInventorySlotContents(2, furnaceItem);
                return true;
            }
            villager.inventory.addItem(furnaceItem);
            furnace.setInventorySlotContents(2, ItemStack.EMPTY);
            item.setCount(item.getCount() - furnaceItem.getCount());
        }
        return false;
    }

    public static boolean takeItemFromFurnace(ItemStack item, TileEntityFurnace furnace, AbstractVillager villager, boolean sensitive) {
        ItemStack furnaceItem = furnace.getStackInSlot(2);
        Block block = Block.getBlockFromItem(item.getItem());
        Block furnaceBlock = furnaceItem.isEmpty() ? Blocks.AIR : Block.getBlockFromItem(furnaceItem.getItem());
        if (!furnaceItem.isEmpty() && furnaceItem.getCount() > 0
                && (sensitive && furnaceItem.getDisplayName().equals(item.getDisplayName())
                        || !sensitive && (furnaceItem.getItem().equals(item.getItem())
                                || block instanceof BlockLog && furnaceBlock instanceof BlockLog))) {
            if (furnaceItem.getCount() >= item.getCount()) {
                furnaceItem.setCount(furnaceItem.getCount() - item.getCount());
                villager.inventory.addItem(item);
                if (furnaceItem.getCount() <= 0) {
                    furnaceItem = ItemStack.EMPTY;
                }
                furnace.setInventorySlotContents(2, furnaceItem);
                return true;
            }
            villager.inventory.addItem(furnaceItem);
            furnace.setInventorySlotContents(2, ItemStack.EMPTY);
            item.setCount(item.getCount() - furnaceItem.getCount());
        }
        return false;
    }

    public static void addFuelToFurnace(HelpfulVillage village, TileEntityFurnace furnace, int burnTime) {
        int totalTime = 0;
        for (int i = 0; i < village.guildHallList.size(); ++i) {
            GuildHall hall = village.guildHallList.get(i);
            hall.checkChests();
            ArrayList<TileEntityChest> chests = hall.guildChests;
            for (int j = 0; j < chests.size(); ++j) {
                TileEntityChest chest = chests.get(j);
                for (int k = 0; k < chest.getSizeInventory(); ++k) {
                    ItemStack item = chest.getStackInSlot(k);
                    if (item.isEmpty() || !item.getItem().equals(Items.COAL)) {
                        continue;
                    }
                    int currTime = TileEntityFurnace.getItemBurnTime(item) * item.getCount();
                    totalTime += currTime;
                    if (furnace.getStackInSlot(1).isEmpty()) {
                        furnace.setInventorySlotContents(1, item);
                        chest.setInventorySlotContents(k, ItemStack.EMPTY);
                    } else {
                        int size = item.getCount() + furnace.getStackInSlot(1).getCount();
                        if (size > 64) {
                            int removeAmount = item.getCount() - (64 - furnace.getStackInSlot(1).getCount());
                            furnace.setInventorySlotContents(1, new ItemStack(item.getItem(), 64));
                            if (removeAmount <= 0) {
                                chest.setInventorySlotContents(k, ItemStack.EMPTY);
                            } else {
                                chest.setInventorySlotContents(k, new ItemStack(item.getItem(), removeAmount));
                            }
                            return;
                        }
                        furnace.setInventorySlotContents(1, new ItemStack(item.getItem(), size));
                        chest.setInventorySlotContents(k, ItemStack.EMPTY);
                    }
                    if (totalTime < burnTime) {
                        continue;
                    }
                    return;
                }
            }
        }
    }

    public static ArrayList<BlockPos> getAdjacentCoords(BlockPos coords) {
        ArrayList<BlockPos> adjacent = new ArrayList<BlockPos>();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    adjacent.add(new BlockPos(coords.getX() + x, coords.getY() + y, coords.getZ() + z));
                }
            }
        }
        return adjacent;
    }

    public static void breakBlock(BlockPos currentCoords, AbstractVillager villager) {
        World world = villager.world;
        IBlockState state = world.getBlockState(currentCoords);
        Block currentBlock = state.getBlock();
        if (!(currentBlock == null || currentBlock.equals(Blocks.AIR) || currentBlock.equals(Blocks.WATER)
                || currentBlock.equals(Blocks.LAVA) || currentBlock.equals(Blocks.BEDROCK)
                || currentBlock.equals(Blocks.TALLGRASS))) {
            NonNullList<ItemStack> items = NonNullList.create();
            currentBlock.getDrops(items, world, currentCoords, state, 0);
            for (ItemStack i : items) {
                try {
                    villager.inventory.addItem(i);
                    villager.damageItem();
                } catch (NullPointerException e) {
                    // empty catch block
                }
            }
        }
        world.setBlockToAir(currentCoords);
    }

    public static boolean isinsideAnyVillage(double x, double y, double z) {
        for (HelpfulVillage i : HelpfulVillagers.villages) {
            if (!i.isInsideVillage(x, y, z)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static boolean isInRangeOfAnyVillage(double x, double y, double z) {
        for (HelpfulVillage i : HelpfulVillagers.villages) {
            if (!i.isInRange(x, y, z)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static void mergeItemStackArrays(ArrayList<ItemStack> from, ArrayList<ItemStack> to) {
        Iterator<ItemStack> iterator = from.iterator();
        while (iterator.hasNext()) {
            ItemStack s = iterator.next();
            if (s != null && !s.isEmpty()) {
                continue;
            }
            iterator.remove();
        }
        iterator = to.iterator();
        while (iterator.hasNext()) {
            ItemStack s = iterator.next();
            if (s != null && !s.isEmpty()) {
                continue;
            }
            iterator.remove();
        }
        for (ItemStack i : from) {
            for (ItemStack j : to) {
                if (i.isEmpty() || j.isEmpty() || !i.getDisplayName().equals(j.getDisplayName())
                        || i.getCount() <= 0 || j.getCount() >= j.getMaxStackSize()) {
                    continue;
                }
                i.shrink(1);
                j.grow(1);
            }
            if (i.getCount() <= 0) {
                continue;
            }
            to.add(i);
        }
    }

    public static void mergeItemStackArrays(ItemStack from, ArrayList<ItemStack> to) {
        ArrayList<ItemStack> temp = new ArrayList<ItemStack>();
        temp.add(from);
        AIHelper.mergeItemStackArrays(temp, to);
    }

    public static boolean removeItemStack(ItemStack item, ArrayList<ItemStack> array) {
        Iterator<ItemStack> iterator = array.iterator();
        while (iterator.hasNext()) {
            ItemStack currItem = iterator.next();
            if (!item.getItem().equals(currItem.getItem()) || currItem.getMetadata() != item.getMetadata()) {
                continue;
            }
            if (currItem.getCount() >= item.getCount()) {
                int itemSize = item.getCount();
                currItem.setCount(currItem.getCount() - itemSize);
                if (currItem.getCount() <= 0) {
                    iterator.remove();
                }
                item.setCount(0);
                return true;
            }
            item.setCount(item.getCount() - currItem.getCount());
            iterator.remove();
        }
        return false;
    }

    public static void removeNodeBranch(ArrayList<CraftTree.Node> nodes, CraftTree.Node parent) {
        Iterator<CraftTree.Node> i = nodes.iterator();
        while (i.hasNext()) {
            CraftTree.Node node = i.next();
            if (!node.equals(parent) && !node.getParent().equals(parent)) {
                continue;
            }
            i.remove();
        }
    }
}
