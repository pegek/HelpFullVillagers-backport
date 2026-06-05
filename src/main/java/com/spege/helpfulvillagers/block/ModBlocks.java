package com.spege.helpfulvillagers.block;

import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.tileentity.TileEntityContructionFence;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = HelpfulVillagers.MODID)
public class ModBlocks {
    public static Block construction_fence;
    public static Block active_construction_fence;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        construction_fence = new BlockConstructionFence("construction_fence", Material.WOOD);
        active_construction_fence = new BlockActiveConstructionFence("active_construction_fence", Material.WOOD);

        event.getRegistry().registerAll(
            construction_fence,
            active_construction_fence
        );

        GameRegistry.registerTileEntity(TileEntityContructionFence.class, new ResourceLocation(HelpfulVillagers.MODID, "construction_fence_tile"));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
            new ItemBlock(construction_fence).setRegistryName(construction_fence.getRegistryName()),
            new ItemBlock(active_construction_fence).setRegistryName(active_construction_fence.getRegistryName())
        );
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(construction_fence), 0, new ModelResourceLocation(construction_fence.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(active_construction_fence), 0, new ModelResourceLocation(active_construction_fence.getRegistryName(), "inventory"));
    }
}
