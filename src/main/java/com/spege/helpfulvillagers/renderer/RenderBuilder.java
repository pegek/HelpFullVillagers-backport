package com.spege.helpfulvillagers.renderer;

import com.spege.helpfulvillagers.entity.EntityBuilder;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderBuilder extends RenderLiving<EntityBuilder> {
    private static final ResourceLocation villagerTextures = new ResourceLocation(HelpfulVillagers.MODID, "textures/entity/villager/builder.png");

    public RenderBuilder(RenderManager renderManagerIn) {
        super(renderManagerIn, new ModelVillager(0.0F), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBuilder entity) {
        return villagerTextures;
    }
}
