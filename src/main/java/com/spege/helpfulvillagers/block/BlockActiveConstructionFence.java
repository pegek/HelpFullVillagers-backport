package com.spege.helpfulvillagers.block;

import net.minecraft.block.material.Material;

public class BlockActiveConstructionFence extends BlockConstructionFence {
    public BlockActiveConstructionFence(String name, Material material) {
        super(name, material);
        this.setLightLevel(1.0f);
        this.setCreativeTab(null);
    }
}
