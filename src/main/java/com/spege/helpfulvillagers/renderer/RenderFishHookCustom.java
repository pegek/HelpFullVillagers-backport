package com.spege.helpfulvillagers.renderer;

import com.spege.helpfulvillagers.entity.EntityFisherman;
import com.spege.helpfulvillagers.entity.EntityFishHookCustom;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Renders the custom fishing bobber and its line back to the fisherman. */
@SideOnly(Side.CLIENT)
public class RenderFishHookCustom extends Render<EntityFishHookCustom> {
    private static final ResourceLocation PARTICLES = new ResourceLocation("textures/particle/particles.png");

    public RenderFishHookCustom(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityFishHookCustom entity, double x, double y, double z,
            float entityYaw, float partialTicks) {
        this.renderBobber(entity, x, y, z);
        this.renderLine(entity, x, y, z, partialTicks);
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityFishHookCustom entity) {
        return PARTICLES;
    }

    private void renderBobber(EntityFishHookCustom entity, double x, double y, double z) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        this.bindEntityTexture(entity);

        int textureX = 1;
        int textureY = 2;
        float minU = (float) (textureX * 8) / 128.0f;
        float maxU = (float) (textureX * 8 + 8) / 128.0f;
        float minV = (float) (textureY * 8) / 128.0f;
        float maxV = (float) (textureY * 8 + 8) / 128.0f;

        GlStateManager.rotate(180.0f - this.renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-this.renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-0.5, -0.5, 0.0).tex(minU, maxV).endVertex();
        buffer.pos( 0.5, -0.5, 0.0).tex(maxU, maxV).endVertex();
        buffer.pos( 0.5,  0.5, 0.0).tex(maxU, minV).endVertex();
        buffer.pos(-0.5,  0.5, 0.0).tex(minU, minV).endVertex();
        tessellator.draw();

        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    private void renderLine(EntityFishHookCustom entity, double x, double y, double z, float partialTicks) {
        EntityFisherman fisherman = entity.getOwner();
        if (fisherman == null) {
            return;
        }
        double hookX = entity.prevPosX + (entity.posX - entity.prevPosX) * (double) partialTicks;
        double hookY = entity.prevPosY + (entity.posY - entity.prevPosY) * (double) partialTicks;
        double hookZ = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * (double) partialTicks;

        float yaw = (fisherman.prevRotationYaw
                + (fisherman.rotationYaw - fisherman.prevRotationYaw) * partialTicks) * ((float) Math.PI / 180.0f);
        double sinYaw = MathHelper.sin(yaw);
        double cosYaw = MathHelper.cos(yaw);
        double handX = fisherman.prevPosX + (fisherman.posX - fisherman.prevPosX) * (double) partialTicks
                - cosYaw * 0.35 - sinYaw * 0.85;
        double handY = fisherman.prevPosY + (fisherman.posY - fisherman.prevPosY) * (double) partialTicks
                + 1.05;
        double handZ = fisherman.prevPosZ + (fisherman.posZ - fisherman.prevPosZ) * (double) partialTicks
                - sinYaw * 0.35 + cosYaw * 0.85;

        double dx = handX - hookX;
        double dy = handY - hookY;
        double dz = handZ - hookZ;

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GL11.glLineWidth(2.0f);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int segments = 16;
        for (int i = 0; i <= segments; ++i) {
            float progress = (float) i / (float) segments;
            double curve = y + dy * (double) (progress * progress + progress) * 0.5 + 0.25;
            buffer.pos(x + dx * (double) progress, curve, z + dz * (double) progress)
                    .color(24, 20, 16, 255)
                    .endVertex();
        }
        tessellator.draw();
        GL11.glLineWidth(1.0f);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }
}
