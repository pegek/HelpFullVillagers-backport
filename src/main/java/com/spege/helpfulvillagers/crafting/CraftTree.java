package com.spege.helpfulvillagers.crafting;

import java.util.ArrayList;
import java.util.List;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.item.ItemStack;

/**
 * Recursively expands a target craft into a tree of sub-crafts/raw materials, recording
 * what the villager still needs to gather/smelt and the order in which to craft.
 */
@SuppressWarnings("null")
public class CraftTree {
    private Node root = new Node();
    private AbstractVillager villager;

    public CraftTree(ItemStack itemStack, AbstractVillager villager) {
        this.root.itemStack = itemStack;
        this.root.children = new ArrayList<Node>();
        this.root.inputs = new ArrayList<ItemStack>();
        this.villager = villager;
        this.populateTree(this.root);
    }

    private void populateTree(Node node) {
        VillagerRecipe recipe = this.villager.getRecipe(node.itemStack);
        if (node.itemStack.getDisplayName().equals(this.villager.currentCraftItem.getItem().getDisplayName())
                && recipe != null) {
            this.villager.currentCraftItem.setSensitivity(recipe.getMetadataSensitivity());
        }
        if (recipe != null) {
            if (recipe.isSmelted()) {
                AIHelper.mergeItemStackArrays(node.itemStack, this.villager.materialsNeeded);
            }
            int multiplier = (int) Math.ceil((double) node.itemStack.getCount() / (double) recipe.getOutput().getCount());
            int leftover = recipe.getOutput().getCount() * multiplier - node.itemStack.getCount();
            node.leftover = leftover;
            this.villager.craftChain.add(0, node);
            ArrayList<ItemStack> inputs = new ArrayList<ItemStack>();
            for (ItemStack i : recipe.getTotalInputs()) {
                int multipliedSize = i.getCount() * multiplier;
                int maxSize = i.getMaxStackSize();
                int numMax = multipliedSize / maxSize;
                for (int j = 0; j < numMax; ++j) {
                    inputs.add(new ItemStack(i.getItem(), maxSize));
                    multipliedSize -= maxSize;
                }
                if (multipliedSize <= 0) {
                    continue;
                }
                inputs.add(new ItemStack(i.getItem(), multipliedSize));
            }
            node.inputs.addAll(inputs);
            for (ItemStack i : inputs) {
                ItemStack currItem = i.copy();
                this.villager.inventory.storeAsCollected(currItem, recipe.isSmelted());
                if (currItem.isEmpty()) {
                    continue;
                }
                this.villager.lookForItem(currItem);
                this.villager.inventory.storeAsCollected(currItem, recipe.isSmelted());
                if (currItem.isEmpty()) {
                    continue;
                }
                this.addChild(node, currItem.copy(), recipe.isSmelted());
            }
            for (Node n : node.children) {
                this.populateTree(n);
            }
        } else if (node.smelt) {
            AIHelper.mergeItemStackArrays(node.itemStack, this.villager.smeltablesNeeded);
        } else {
            AIHelper.mergeItemStackArrays(node.itemStack, this.villager.materialsNeeded);
        }
    }

    private void addChild(Node parent, ItemStack itemStack, boolean smelt) {
        Node child = new Node();
        child.itemStack = itemStack;
        child.smelt = smelt;
        child.parent = parent;
        child.children = new ArrayList<Node>();
        child.inputs = new ArrayList<ItemStack>();
        child.parent.children.add(child);
    }

    public void traverseTree() {
        this.root.traverseTree();
    }

    public static class Node {
        private ItemStack itemStack = ItemStack.EMPTY;
        private boolean smelt;
        private int leftover;
        private Node parent;
        private List<Node> children;
        private List<ItemStack> inputs;

        private void traverseTree() {
            System.out.println(this.itemStack);
            for (Node i : this.children) {
                i.traverseTree();
            }
        }

        public ItemStack getItemStack() {
            return this.itemStack;
        }

        public Node getParent() {
            return this.parent;
        }

        public ArrayList<ItemStack> getInputs() {
            return new ArrayList<ItemStack>(this.inputs);
        }

        public int getLeftover() {
            return this.leftover;
        }

        public boolean isSmelted() {
            return this.smelt;
        }

        @Override
        public String toString() {
            return this.itemStack.toString();
        }
    }
}
