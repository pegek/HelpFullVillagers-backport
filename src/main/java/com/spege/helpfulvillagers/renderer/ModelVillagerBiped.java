package com.spege.helpfulvillagers.renderer;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Biped model for mod villagers that animates the arm poses from the entity's synced hand-active
 * state: a drawn bow shows the aiming pose, an active shield the blocking pose, and held items get
 * the regular item pose (vanilla mobs wire this in their renderers; plain {@link ModelBiped}
 * leaves the arms hanging).
 */
@SideOnly(Side.CLIENT)
public class ModelVillagerBiped extends ModelBiped {
    public ModelVillagerBiped(float modelSize) {
        super(modelSize);
    }

    @Override
    public void setLivingAnimations(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
            float partialTickTime) {
        // Mobs are right-handed: MAIN_HAND renders on the right arm, OFF_HAND on the left.
        this.rightArmPose = entity.getHeldItemMainhand().isEmpty() ? ModelBiped.ArmPose.EMPTY : ModelBiped.ArmPose.ITEM;
        this.leftArmPose = entity.getHeldItemOffhand().isEmpty() ? ModelBiped.ArmPose.EMPTY : ModelBiped.ArmPose.ITEM;
        if (entity.isHandActive()) {
            ItemStack active = entity.getActiveItemStack();
            if (!active.isEmpty()) {
                ModelBiped.ArmPose pose = null;
                if (active.getItem() instanceof ItemBow) {
                    pose = ModelBiped.ArmPose.BOW_AND_ARROW;
                } else if (active.getItem() instanceof ItemShield) {
                    pose = ModelBiped.ArmPose.BLOCK;
                }
                if (pose != null) {
                    if (entity.getActiveHand() == EnumHand.MAIN_HAND) {
                        this.rightArmPose = pose;
                    } else {
                        this.leftArmPose = pose;
                    }
                }
            }
        }
        super.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTickTime);
    }
}
