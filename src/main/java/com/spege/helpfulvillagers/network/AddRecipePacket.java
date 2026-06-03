package com.spege.helpfulvillagers.network;

import java.util.ArrayList;

import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: add (0), replace (1) or delete (2) a villager's custom recipe. */
public class AddRecipePacket implements IMessage {
    private int id;
    private VillagerRecipe recipe;
    private int flag;

    public AddRecipePacket() {
    }

    public AddRecipePacket(int id, VillagerRecipe recipe, int flag) {
        this.id = id;
        this.recipe = recipe;
        this.flag = flag;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        int length = this.recipe.getTotalInputs().size();
        buffer.writeInt(length);
        for (int j = 0; j < length; ++j) {
            ItemStack input = this.recipe.getTotalInputs().get(j);
            ByteBufUtils.writeItemStack(buffer, input);
        }
        ByteBufUtils.writeItemStack(buffer, this.recipe.getOutput());
        buffer.writeBoolean(this.recipe.isSmelted());
        buffer.writeInt(this.flag);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        ArrayList<ItemStack> inputs = new ArrayList<ItemStack>();
        int length = buffer.readInt();
        for (int j = 0; j < length; ++j) {
            ItemStack input = ByteBufUtils.readItemStack(buffer);
            inputs.add(input);
        }
        ItemStack output = ByteBufUtils.readItemStack(buffer);
        boolean smelt = buffer.readBoolean();
        this.recipe = new VillagerRecipe(inputs, output, smelt);
        this.flag = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<AddRecipePacket, IMessage> {
        @Override
        public IMessage onMessage(final AddRecipePacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) player.world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            switch (message.flag) {
                                case 0:
                                    entity.addCustomRecipe(message.recipe);
                                    break;
                                case 1:
                                    entity.replaceCustomRecipe(message.recipe);
                                    break;
                                case 2:
                                    entity.deleteCustomRecipe(message.recipe);
                                    break;
                            }
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
