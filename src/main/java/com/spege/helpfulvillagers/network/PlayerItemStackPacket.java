package com.spege.helpfulvillagers.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: sets the item currently held on a player's cursor (barter/exchange GUI). */
public class PlayerItemStackPacket implements IMessage {
    private int id;
    private ItemStack stack;

    public PlayerItemStackPacket() {
    }

    public PlayerItemStackPacket(int id, ItemStack stack) {
        this.id = id;
        this.stack = stack;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        ByteBufUtils.writeItemStack(buffer, this.stack == null ? ItemStack.EMPTY : this.stack);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.stack = ByteBufUtils.readItemStack(buffer);
    }

    public static class Handler implements IMessageHandler<PlayerItemStackPacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerItemStackPacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP sender = ctx.getServerHandler().player;
                sender.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EntityPlayer entity = (EntityPlayer) sender.world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            entity.inventory.setItemStack(message.stack);
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
