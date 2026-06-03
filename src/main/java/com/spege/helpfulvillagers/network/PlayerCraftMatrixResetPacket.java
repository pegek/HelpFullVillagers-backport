package com.spege.helpfulvillagers.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: clears the player's 2x2 crafting matrix (after a teach-recipe interaction). */
public class PlayerCraftMatrixResetPacket implements IMessage {
    private int id;

    public PlayerCraftMatrixResetPacket() {
    }

    public PlayerCraftMatrixResetPacket(int id) {
        this.id = id;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<PlayerCraftMatrixResetPacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerCraftMatrixResetPacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EntityPlayer entity = (EntityPlayer) player.world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            if (entity.openContainer instanceof ContainerPlayer) {
                                ContainerPlayer container = (ContainerPlayer) entity.openContainer;
                                for (int i = 0; i < container.craftMatrix.getSizeInventory(); ++i) {
                                    container.craftMatrix.setInventorySlotContents(i, ItemStack.EMPTY);
                                }
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
