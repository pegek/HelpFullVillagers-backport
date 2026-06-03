package com.spege.helpfulvillagers.renderer;

import com.spege.helpfulvillagers.entity.EntityFishHookCustom;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the custom fishing hook.
 *
 * <p><b>STUB (step 10)</b> — returns null texture, draws nothing.
 * Full bobber rendering will be implemented alongside the full fish hook entity port.
 */
@SideOnly(Side.CLIENT)
public class RenderFishHookCustom extends Render<EntityFishHookCustom> {
    public RenderFishHookCustom(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityFishHookCustom entity, double x, double y, double z,
            float entityYaw, float partialTicks) {
        // TODO step 10: render bobber geometry
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityFishHookCustom entity) {
        return null;
    }
}
