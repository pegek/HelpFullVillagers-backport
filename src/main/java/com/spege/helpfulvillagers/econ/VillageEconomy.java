package com.spege.helpfulvillagers.econ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.ItemPriceClientPacket;
import com.spege.helpfulvillagers.network.ItemPriceServerPacket;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Per-village economy: derives item prices from local block abundance and crafting recipes,
 * tracks supply/demand, and holds player "bank" accounts.
 *
 * <p>1.12.2 migration notes:
 * <ul>
 *   <li><b>Threading:</b> the 1.7.10 original calculated prices on a background {@link Thread}
 *       that read the world off the main thread — unsafe in 1.12.2 (and always racy). Price
 *       calculation is now SYNCHRONOUS ({@link #initPrices()} runs on the caller's thread). It is
 *       a one-time, per-village pass guarded by {@code pricesCalculated}; for very large villages
 *       this can cause a brief hitch (TODO: chunk/spread the scan if it becomes a problem).</li>
 *   <li>The original {@code initPrices} body was partly corrupted by the decompiler (dead code
 *       after an unconditional {@code continue}); the block-drop tally loop is reconstructed to the
 *       evident intent.</li>
 *   <li>{@code Block.canSilkHarvest} is {@code protected} in 1.12.2, so the silk-harvest drop
 *       augmentation is omitted (minor pricing effect).</li>
 *   <li>Recipe iteration uses {@link CraftingManager#REGISTRY} and
 *       {@link FurnaceRecipes#getSmeltingList()}.</li>
 *   <li>Player account keys use {@code EntityPlayer.getName()} (getDisplayName() now returns a
 *       text component).</li>
 * </ul>
 */
@SuppressWarnings("null")
public class VillageEconomy {
    private HelpfulVillage village;
    private HashMap<String, ArrayList<VillagerRecipe>> recipeMap = new HashMap<String, ArrayList<VillagerRecipe>>();
    private HashMap<String, ItemPrice> itemPrices = new HashMap<String, ItemPrice>();
    private HashMap<String, ItemStack> searchMap = new HashMap<String, ItemStack>();
    private HashMap<String, Integer> accountMap = new HashMap<String, Integer>();
    private int lowestWoodPrice = Integer.MAX_VALUE;

    public VillageEconomy() {
    }

    public VillageEconomy(HelpfulVillage village, boolean init) {
        this.village = village;
        if (init) {
            this.initPrices();
        }
    }

    /**
     * Synchronous one-time price calculation. Scans the village's bounding box for block drops to
     * estimate item abundance, then derives recipe-based prices. See class javadoc for the threading
     * change versus the 1.7.10 original.
     */
    private void initPrices() {
        this.village.priceCalcStarted = true;
        HashMap<ItemStack, Integer> itemMap = new HashMap<ItemStack, Integer>();

        ArrayList<Item> outputs = new ArrayList<Item>();
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ItemStack outputStack = recipe.getRecipeOutput();
            if (outputStack.isEmpty()) {
                continue;
            }
            Item outputItem = outputStack.getItem();
            if (!outputs.contains(outputItem)) {
                outputs.add(outputItem);
            }
        }

        World world = this.village.world;
        AxisAlignedBB bounds = this.village.villageBounds;
        for (int y = 0; y < bounds.maxY; ++y) {
            for (int x = (int) bounds.minX; x < bounds.maxX; ++x) {
                for (int z = (int) bounds.minZ; z < bounds.maxZ; ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    NonNullList<ItemStack> drops = NonNullList.create();
                    block.getDrops(drops, world, pos, state, 0);
                    for (ItemStack item : drops) {
                        if (item.isEmpty()) {
                            continue;
                        }
                        try {
                            if (item.getDisplayName().contains("Bedrock")) {
                                continue;
                            }
                        } catch (RuntimeException e) {
                            // ignore items whose display name lookup misbehaves
                        }
                        if (outputs.contains(item.getItem())) {
                            continue;
                        }
                        boolean found = false;
                        for (ItemStack mapItem : itemMap.keySet()) {
                            if (ItemStack.areItemStacksEqual(mapItem, item)) {
                                itemMap.put(mapItem, itemMap.get(mapItem) + 1);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            itemMap.put(item, 1);
                        }
                    }
                }
            }
        }

        int highestAmount = 0;
        int lowestAmount = Integer.MAX_VALUE;
        for (Integer amount : itemMap.values()) {
            if (amount > highestAmount) {
                highestAmount = amount;
            }
            if (amount < lowestAmount) {
                lowestAmount = amount;
            }
        }
        for (Map.Entry<ItemStack, Integer> entry : itemMap.entrySet()) {
            int price = this.calcItemValue(highestAmount, lowestAmount, 100, 1, entry.getValue());
            this.itemPrices.put(entry.getKey().getDisplayName(), new ItemPrice(entry.getKey(), price));
            Block block = Block.getBlockFromItem(entry.getKey().getItem());
            if (price > 0 && block instanceof BlockLog && price < this.lowestWoodPrice) {
                this.lowestWoodPrice = price;
            }
        }

        ArrayList<ItemStack> outputStacks = new ArrayList<ItemStack>();
        outer:
        for (Item i : outputs) {
            if (i.getHasSubtypes()) {
                ArrayList<String> names = new ArrayList<String>();
                for (int j = 0; j < 64; ++j) {
                    ItemStack item2 = new ItemStack(i, 1, j);
                    try {
                        if (names.contains(item2.getDisplayName())) {
                            continue outer;
                        }
                        names.add(item2.getDisplayName());
                        if (this.itemPrices.containsKey(item2.getDisplayName())) {
                            continue;
                        }
                        outputStacks.add(item2);
                    } catch (Exception e) {
                        if (!this.itemPrices.containsKey(i.getUnlocalizedName())) {
                            continue outer;
                        }
                        outputStacks.add(new ItemStack(i, 1, 0));
                        continue outer;
                    }
                }
            } else {
                ItemStack item = new ItemStack(i, 1, 0);
                if (this.itemPrices.containsKey(item.getDisplayName())) {
                    continue;
                }
                outputStacks.add(item);
            }
        }

        this.setupRecipes();
        for (ItemStack item : outputStacks) {
            this.calcValueFromRecipe(item);
        }

        ItemStack emerald = new ItemStack(Items.EMERALD);
        this.itemPrices.remove(emerald.getDisplayName());

        ItemStack leather = new ItemStack(Items.LEATHER);
        if (this.itemPrices.containsKey(leather.getDisplayName())) {
            ItemStack saddle = new ItemStack(Items.SADDLE);
            this.itemPrices.put(saddle.getDisplayName(),
                    new ItemPrice(saddle, this.itemPrices.get(leather.getDisplayName()).getOriginalPrice() * 5));
        }

        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        if (this.itemPrices.containsKey(iron.getDisplayName())) {
            ItemStack ironArmor = new ItemStack(Items.IRON_HORSE_ARMOR);
            this.itemPrices.put(ironArmor.getDisplayName(),
                    new ItemPrice(ironArmor, this.itemPrices.get(iron.getDisplayName()).getOriginalPrice() * 6));
        }
        if (this.itemPrices.containsKey(gold.getDisplayName())) {
            ItemStack goldArmor = new ItemStack(Items.GOLDEN_HORSE_ARMOR);
            this.itemPrices.put(goldArmor.getDisplayName(),
                    new ItemPrice(goldArmor, this.itemPrices.get(gold.getDisplayName()).getOriginalPrice() * 6));
        }
        if (this.itemPrices.containsKey(diamond.getDisplayName())) {
            ItemStack diamondArmor = new ItemStack(Items.DIAMOND_HORSE_ARMOR);
            this.itemPrices.put(diamondArmor.getDisplayName(),
                    new ItemPrice(diamondArmor, this.itemPrices.get(diamond.getDisplayName()).getOriginalPrice() * 6));
        }

        this.village.pricesCalculated = true;
    }

    private int calcItemValue(int highestAmount, int lowestAmount, int maxPrice, int minPrice, int amount) {
        double x1 = Math.log(lowestAmount);
        double x2 = Math.log(highestAmount);
        double y1 = Math.log(maxPrice);
        double y2 = Math.log(minPrice);
        double slopeNum = (x1 - x2) * (y1 - y2);
        double slopeDen = (x1 - x2) * (x1 - x2);
        double slope = slopeNum / slopeDen;
        int result = (int) (100.0 * Math.pow(amount, slope));
        return Math.max(result, 1);
    }

    private void setupRecipes() {
        VillagerRecipe recipe;
        java.util.List<IRecipe> recipes = HelpfulVillagers.vanillaRecipes;
        for (int i = 0; i < recipes.size(); ++i) {
            ItemStack outputItem = recipes.get(i).getRecipeOutput();
            if (outputItem.isEmpty()) {
                continue;
            }
            try {
                recipe = new VillagerRecipe(recipes.get(i), false);
            } catch (Exception e) {
                continue;
            }
            if (recipe.getOutput().isEmpty()) {
                continue;
            }
            this.addToRecipeMap(outputItem.getDisplayName(), recipe);
        }
        for (Map.Entry<ItemStack, ItemStack> entry : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            ItemStack outputItem = entry.getValue();
            recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
            if (recipe.getOutput().isEmpty()) {
                continue;
            }
            this.addToRecipeMap(outputItem.getDisplayName(), recipe);
        }
    }

    private void addToRecipeMap(String key, VillagerRecipe recipe) {
        ArrayList<VillagerRecipe> list = this.recipeMap.get(key);
        if (list == null) {
            list = new ArrayList<VillagerRecipe>();
            this.recipeMap.put(key, list);
        }
        list.add(recipe);
    }

    private int calcValueFromRecipe(ItemStack i) {
        if (this.itemPrices.containsKey(i.getDisplayName())) {
            Block block = Block.getBlockFromItem(i.getItem());
            if (block instanceof BlockLog) {
                return this.lowestWoodPrice;
            }
            return this.itemPrices.get(i.getDisplayName()).getPrice();
        }
        if (!this.recipeMap.containsKey(i.getDisplayName())) {
            return -1;
        }
        this.searchMap.put(i.getDisplayName(), i);
        int price = 0;
        int lowestPrice = Integer.MAX_VALUE;
        ArrayList<VillagerRecipe> recipes = this.recipeMap.get(i.getDisplayName());
        for (VillagerRecipe recipe : recipes) {
            for (ItemStack stack : recipe.getTotalInputs()) {
                if (this.searchMap.containsKey(stack.getDisplayName())) {
                    price = 0;
                    break;
                }
                int val = this.calcValueFromRecipe(stack) * stack.getCount();
                if (val < 0) {
                    price = -1;
                    break;
                }
                price += val;
            }
            if (price > 0) {
                if (recipe.getOutput().getCount() > 0) {
                    price /= recipe.getOutput().getCount();
                }
                if (price <= 0) {
                    price = 1;
                }
                if (price < lowestPrice) {
                    lowestPrice = price;
                }
            }
            price = 0;
        }
        this.searchMap.remove(i.getDisplayName());
        if (lowestPrice < Integer.MAX_VALUE) {
            this.itemPrices.put(i.getDisplayName(), new ItemPrice(i, lowestPrice));
            return lowestPrice;
        }
        return -1;
    }

    public HashMap<String, ItemPrice> getItemPrices() {
        return this.itemPrices;
    }

    public int getPrice(String name) {
        ItemPrice itemPrice = this.itemPrices.get(name);
        if (itemPrice != null) {
            return itemPrice.getPrice();
        }
        return -1;
    }

    public ItemPrice getItemPrice(String name) {
        return this.itemPrices.get(name);
    }

    public void putItemPrice(ItemPrice itemPrice) {
        this.itemPrices.put(itemPrice.getItem().getDisplayName(), itemPrice);
    }

    public boolean hasItem(ItemStack item) {
        return this.itemPrices.containsKey(item.getDisplayName());
    }

    public void accountDeposit(EntityPlayer player, int amount) {
        String username = player.getName();
        if (this.accountMap.containsKey(username)) {
            this.accountMap.put(username, this.accountMap.get(username) + amount);
        } else {
            this.accountMap.put(username, amount);
        }
    }

    public int accountWithdraw(EntityPlayer player, int amount) {
        String username = player.getName();
        if (this.accountMap.containsKey(username)) {
            int currAmount = this.accountMap.get(username);
            if (currAmount >= amount) {
                this.accountMap.put(username, currAmount - amount);
                return amount;
            }
        }
        return -1;
    }

    public int getAccount(EntityPlayer player) {
        String username = player.getName();
        if (this.accountMap.containsKey(username)) {
            return this.accountMap.get(username);
        }
        return -1;
    }

    public void setAccount(EntityPlayer player, int amount) {
        this.accountMap.put(player.getName(), amount);
    }

    public void decreaseAllDemand() {
        for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
            entry.getValue().decreaseDemand(0.005);
        }
    }

    public void increaseItemSupply(AbstractVillager villager, ItemStack item) {
        ItemPrice itemPrice = this.itemPrices.get(item.getDisplayName());
        if (itemPrice == null) {
            this.generateNewPrice(item);
            itemPrice = this.itemPrices.get(item.getDisplayName());
        }
        if (itemPrice != null) {
            itemPrice.increaseSupply(item.getCount());
        }
        try {
            if (!this.village.world.isRemote) {
                this.itemSyncClient(villager, null, item);
            }
        } catch (NullPointerException e) {
            // empty catch block
        }
    }

    public void decreaseItemSupply(AbstractVillager villager, ItemStack item) {
        ItemPrice itemPrice = this.itemPrices.get(item.getDisplayName());
        if (itemPrice == null) {
            this.generateNewPrice(item);
            itemPrice = this.itemPrices.get(item.getDisplayName());
        }
        if (itemPrice != null) {
            itemPrice.decreaseSupply(item.getCount());
        }
        try {
            if (!this.village.world.isRemote) {
                this.itemSyncClient(villager, null, item);
            }
        } catch (NullPointerException e) {
            // empty catch block
        }
    }

    private void generateNewPrice(ItemStack item) {
        if (item.isEmpty()) {
            return;
        }
        Random rand = new Random();
        int sum = 0;
        int highest = Integer.MIN_VALUE;
        for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            int price = entry.getValue().getPrice();
            sum += price;
            if (price > highest) {
                highest = price;
            }
        }
        int denom = this.itemPrices.size() > 0 ? this.itemPrices.size() : 1;
        int average = sum / denom;
        int range = (highest - average) / 2;
        if (range <= 0) {
            range = 1; // guard against nextInt(<=0) (the 1.7.10 original could throw here)
        }
        int newPrice = rand.nextInt(range) + average;
        this.itemPrices.put(item.getDisplayName(), new ItemPrice(item, newPrice));
    }

    public void fullSyncClient(AbstractVillager villager, EntityPlayer player) {
        if (player == null) {
            for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
                HelpfulVillagers.network.sendToAll(new ItemPriceClientPacket(villager, entry.getValue()));
            }
        } else {
            for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
                HelpfulVillagers.network.sendTo(new ItemPriceClientPacket(villager, entry.getValue()), (EntityPlayerMP) player);
            }
        }
    }

    public void fullSyncServer(AbstractVillager villager) {
        for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
            HelpfulVillagers.network.sendToServer(new ItemPriceServerPacket(villager, entry.getValue()));
        }
    }

    public void itemSyncClient(AbstractVillager villager, EntityPlayer player, ItemStack item) {
        if (player == null) {
            HelpfulVillagers.network.sendToAll(new ItemPriceClientPacket(villager, this.itemPrices.get(item.getDisplayName())));
        } else {
            HelpfulVillagers.network.sendTo(new ItemPriceClientPacket(villager, this.itemPrices.get(item.getDisplayName())), (EntityPlayerMP) player);
        }
    }

    public void itemSyncServer(AbstractVillager villager, ItemStack item) {
        HelpfulVillagers.network.sendToServer(new ItemPriceServerPacket(villager, this.itemPrices.get(item.getDisplayName())));
    }

    public NBTTagList writeToNBT(NBTTagList nbtTagList) {
        NBTTagCompound nbttagcompound;
        for (Map.Entry<String, ItemPrice> entry : this.itemPrices.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            nbttagcompound = new NBTTagCompound();
            nbttagcompound.setString("Name", entry.getKey());
            nbttagcompound.setTag("Item", entry.getValue().writeToNBT(new NBTTagCompound()));
            nbtTagList.appendTag(nbttagcompound);
        }
        for (Map.Entry<String, Integer> entry : this.accountMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            nbttagcompound = new NBTTagCompound();
            nbttagcompound.setString("Player", entry.getKey());
            nbttagcompound.setInteger("Amount", entry.getValue().intValue());
            nbtTagList.appendTag(nbttagcompound);
        }
        return nbtTagList;
    }

    public void readFromNBT(NBTTagList nbttaglist) {
        for (int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
            if (nbttagcompound.hasKey("Name")) {
                String name = nbttagcompound.getString("Name");
                NBTTagCompound priceCompound = nbttagcompound.getCompoundTag("Item");
                ItemPrice itemPrice = ItemPrice.loadCraftItemFromNBT(priceCompound);
                if (itemPrice != null) {
                    this.itemPrices.put(name, itemPrice);
                }
                continue;
            }
            String player = nbttagcompound.getString("Player");
            int amount = nbttagcompound.getInteger("Amount");
            this.accountMap.put(player, amount);
        }
    }
}
