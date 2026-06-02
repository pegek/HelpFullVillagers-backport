package com.spege.helpfulvillagers.crafting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;

/**
 * A villager-craftable/smeltable recipe: a flattened representation of a vanilla recipe
 * (or a furnace smelt) reduced to its required input stacks and a single output.
 *
 * <p>1.12.2 note: the original parsed each concrete recipe class (ShapedRecipes,
 * ShapelessRecipes, ShapedOreRecipe, ShapelessOreRecipe) by hand. 1.12.2 unifies all
 * recipes behind {@link IRecipe#getIngredients()} returning {@code NonNullList<Ingredient>},
 * so we take a single representative stack per ingredient slot instead.
 */
@SuppressWarnings("null")
public class VillagerRecipe implements Comparable<VillagerRecipe> {
    private ArrayList<ItemStack> inputItems = new ArrayList<ItemStack>();
    private ArrayList<ItemStack> totalInputs = new ArrayList<ItemStack>();
    private ItemStack outputItem = ItemStack.EMPTY;
    private boolean smeltable;
    private boolean metadataSensitive = false;

    public VillagerRecipe() {
    }

    public VillagerRecipe(IRecipe recipe, boolean smeltable) {
        this.smeltable = smeltable;
        this.metadataSensitive = false;
        this.outputItem = recipe.getRecipeOutput();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == Ingredient.EMPTY) {
                continue;
            }
            ItemStack[] matching = ingredient.getMatchingStacks();
            if (matching.length > 0 && !matching[0].isEmpty()) {
                this.inputItems.add(matching[0].copy());
            }
        }
        this.totalInputs = this.initTotalInputs();
        this.initMetadata();
    }

    public VillagerRecipe(ArrayList<ItemStack> inputs, ItemStack output, boolean smeltable) {
        this.smeltable = smeltable;
        this.outputItem = output.copy();
        this.inputItems.addAll(inputs);
        this.totalInputs.addAll(inputs);
        this.initMetadata();
    }

    public VillagerRecipe(ItemStack input, ItemStack output, boolean smeltable) {
        this.smeltable = smeltable;
        this.outputItem = output.copy();
        this.inputItems.add(input.copy());
        this.totalInputs.add(input.copy());
        this.initMetadata();
    }

    private void initMetadata() {
        this.metadataSensitive = this.outputItem.getHasSubtypes();
    }

    private ArrayList<ItemStack> initTotalInputs() {
        ArrayList<ItemStack> totals = new ArrayList<ItemStack>();
        ArrayList<ItemStack> temp = new ArrayList<ItemStack>();
        for (ItemStack i : this.inputItems) {
            ItemStack newItem = i.copy();
            newItem.setCount(1);
            temp.add(newItem);
        }
        AIHelper.mergeItemStackArrays(temp, totals);
        return totals;
    }

    public ArrayList<ItemStack> getInputs() {
        return this.inputItems;
    }

    public ArrayList<ItemStack> getTotalInputs() {
        return new ArrayList<ItemStack>(this.totalInputs);
    }

    public ItemStack getOutput() {
        return this.outputItem.copy();
    }

    public boolean getMetadataSensitivity() {
        return this.metadataSensitive;
    }

    public boolean isSmelted() {
        return this.smeltable;
    }

    public List<String> getTooltip() {
        ArrayList<String> list = new ArrayList<String>();
        String s = this.outputItem.getDisplayName();
        if (this.smeltable) {
            s = s + " (Smelt)";
        }
        list.add(s);
        list.add("");
        for (ItemStack i : this.getTotalInputs()) {
            list.add(i.getDisplayName() + " x" + i.getCount());
        }
        return list;
    }

    @Override
    public String toString() {
        String s = this.outputItem.isEmpty() ? "null" : this.outputItem.toString();
        s = s + " <-";
        s = s + this.getTotalInputs().toString();
        return s;
    }

    @Override
    public int compareTo(VillagerRecipe v) {
        if (v != null) {
            return this.getOutput().getDisplayName().compareTo(v.getOutput().getDisplayName());
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof VillagerRecipe) {
            VillagerRecipe v = (VillagerRecipe) o;
            if (this.smeltable == v.smeltable
                    && this.outputItem.getDisplayName().equals(v.outputItem.getDisplayName())) {
                ArrayList<ItemStack> temp1 = new ArrayList<ItemStack>(this.totalInputs);
                ArrayList<ItemStack> temp2 = new ArrayList<ItemStack>(v.totalInputs);
                if (temp1.size() == temp2.size()) {
                    Iterator<ItemStack> i = temp1.iterator();
                    block0:
                    while (i.hasNext()) {
                        ItemStack itemI = i.next();
                        for (int j = 0; j < temp2.size(); ++j) {
                            ItemStack itemJ = temp2.get(j);
                            if (itemI.getDisplayName().equals(itemJ.getDisplayName())
                                    && itemI.getCount() == itemJ.getCount()) {
                                i.remove();
                                temp2.remove(j);
                                continue block0;
                            }
                        }
                    }
                    return temp1.size() <= 0 && temp2.size() <= 0;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        // Output display name keeps equal recipes in the same bucket; input comparison
        // is finished in equals(). Intentionally coarse, matching the loose equality above.
        return this.outputItem.getDisplayName().hashCode();
    }

    public NBTTagList writeToNBT(NBTTagList list) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("Smelt", this.smeltable);
        this.outputItem.writeToNBT(nbt);
        list.appendTag(nbt);
        for (ItemStack i : this.totalInputs) {
            nbt = new NBTTagCompound();
            i.writeToNBT(nbt);
            list.appendTag(nbt);
        }
        return list;
    }

    public void readFromNBT(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            if (i == 0) {
                this.smeltable = nbt.getBoolean("Smelt");
                this.outputItem = new ItemStack(nbt);
                continue;
            }
            this.totalInputs.add(new ItemStack(nbt));
        }
        this.initMetadata();
    }
}
