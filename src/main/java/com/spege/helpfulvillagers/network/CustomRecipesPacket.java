package com.spege.helpfulvillagers.network;

import java.util.ArrayList;
import java.util.Collections;

import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs a villager's custom (player-taught) recipes to the interacting player. */
public class CustomRecipesPacket implements IMessage {
    private int id;
    private int size;
    private ArrayList<VillagerRecipe> recipes = new ArrayList<VillagerRecipe>();

    public CustomRecipesPacket() {
    }

    public CustomRecipesPacket(int id, ArrayList<VillagerRecipe> recipes) {
        this.id = id;
        this.size = recipes.size();
        this.recipes.addAll(recipes);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeInt(this.size);
        for (VillagerRecipe i : this.recipes) {
            int length = i.getTotalInputs().size();
            buffer.writeInt(length);
            for (int j = 0; j < length; ++j) {
                ItemStack input = i.getTotalInputs().get(j);
                ByteBufUtils.writeItemStack(buffer, input);
            }
            ByteBufUtils.writeItemStack(buffer, i.getOutput());
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.size = buffer.readInt();
        ArrayList<ItemStack> inputs = new ArrayList<ItemStack>();
        for (int i = 0; i < this.size; ++i) {
            int length = buffer.readInt();
            for (int j = 0; j < length; ++j) {
                ItemStack input = ByteBufUtils.readItemStack(buffer);
                inputs.add(input);
            }
            ItemStack output = ByteBufUtils.readItemStack(buffer);
            this.recipes.add(new VillagerRecipe(inputs, output, false));
            inputs.clear();
        }
    }

    public static class Handler implements IMessageHandler<CustomRecipesPacket, IMessage> {
        @Override
        public IMessage onMessage(final CustomRecipesPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            entity.resetRecipes();
                            entity.customRecipes.addAll(message.recipes);
                            entity.knownRecipes.addAll(message.recipes);
                            Collections.sort(entity.knownRecipes);
                        } catch (NullPointerException e) {
                            // ignore
                        }
                    }
                });
            }
            return null;
        }
    }
}
