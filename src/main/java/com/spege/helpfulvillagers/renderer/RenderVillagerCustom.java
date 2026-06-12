package com.spege.helpfulvillagers.renderer;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders all mod villagers (every profession, including the builder) using a vanilla biped model with
 * a per-profession texture.
 *
 * <p>1.12.2 migration: {@link RenderBiped} is now generic and the constructor takes a
 * {@link RenderManager}; {@code getEntityTexture} replaces the SRG method override;
 * {@code IRenderFactory} is used for registration in {@link ClientProxy}.
 *
 * <p>Armor fix: in 1.7.10 the old {@code RenderBiped} rendered worn armor itself, but in 1.12.2 armor
 * rendering moved to layers and {@code RenderBiped} only adds head/elytra/held-item layers (NOT armor).
 * We therefore add {@link LayerBipedArmor} explicitly so villager-worn armor (soldiers/archers, or any
 * villager equipped via its inventory GUI) is drawn — matching vanilla biped mobs like zombies.
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
    private static final ResourceLocation BUILDER    = new ResourceLocation("helpfulvillagers", "textures/entity/villager/builder.png");
    private static final ResourceLocation CLERIC     = new ResourceLocation("helpfulvillagers", "textures/entity/villager/cleric.png");

    public RenderVillagerCustom(RenderManager renderManager) {
        super(renderManager, new ModelVillagerBiped(0.0f), 0.5f);
        this.addLayer(new LayerBipedArmor(this));
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
            case 9:  return BUILDER;
            case 10: return CLERIC;
            default: return VILLAGER;
        }
    }
}
