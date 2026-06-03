package com.spege.helpfulvillagers.renderer;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders all mod villagers using a vanilla biped model with a per-profession texture.
 *
 * <p>1.12.2 migration: {@link RenderBiped} is now generic and the constructor takes a
 * {@link RenderManager}; {@code getEntityTexture} replaces the SRG method override;
 * {@code IRenderFactory} is used for registration in {@link ClientProxy}.
 */
@SideOnly(Side.CLIENT)
public class RenderVillagerCustom extends RenderBiped<AbstractVillager> {
    private static final ResourceLocation VILLAGER   = new ResourceLocation("helpfulvillagers", "textures/entity/villager/villager.png");
    private static final ResourceLocation LUMBERJACK = new ResourceLocation("helpfulvillagers", "textures/entity/villager/lumberjack.png");
    private static final ResourceLocation MINER      = new ResourceLocation("helpfulvillagers", "textures/entity/villager/miner.png");
    private static final ResourceLocation FARMER     = new ResourceLocation("helpfulvillagers", "textures/entity/villager/farmer.png");
    private static final ResourceLocation SOLDIER    = new ResourceLocation("helpfulvillagers", "textures/entity/villager/soldier.png");
    private static final ResourceLocation ARCHER     = new ResourceLocation("helpfulvillagers", "textures/entity/villager/archer.png");
    private static final ResourceLocation MERCHANT   = new ResourceLocation("helpfulvillagers", "textures/entity/villager/merchant.png");
    private static final ResourceLocation FISHERMAN  = new ResourceLocation("helpfulvillagers", "textures/entity/villager/fisherman.png");
    private static final ResourceLocation RANCHER    = new ResourceLocation("helpfulvillagers", "textures/entity/villager/rancher.png");

    public RenderVillagerCustom(RenderManager renderManager) {
        super(renderManager, new ModelBiped(0.0f), 0.5f);
    }

    @Override
    protected ResourceLocation getEntityTexture(AbstractVillager entity) {
        return textureFor(entity.getProfession());
    }

    private static ResourceLocation textureFor(int profession) {
        switch (profession) {
            case 1:  return LUMBERJACK;
            case 2:  return MINER;
            case 3:  return FARMER;
            case 4:  return SOLDIER;
            case 5:  return ARCHER;
            case 6:  return MERCHANT;
            case 7:  return FISHERMAN;
            case 8:  return RANCHER;
            default: return VILLAGER;
        }
    }
}
