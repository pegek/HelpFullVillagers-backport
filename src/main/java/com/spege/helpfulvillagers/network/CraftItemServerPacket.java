package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: queues (or immediately sets) a craft job for a villager, then syncs the queue. */
public class CraftItemServerPacket implements IMessage {
    private int id;
    private CraftItem craftItem;
    private boolean craftNow;

    public CraftItemServerPacket() {
    }

    public CraftItemServerPacket(int id, CraftItem craftItem, boolean craftNow) {
        this.id = id;
        this.craftItem = craftItem;
        this.craftNow = craftNow;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        ByteBufUtils.writeItemStack(buffer, this.craftItem.getItem());
        ByteBufUtils.writeUTF8String(buffer, this.craftItem.getName());
        buffer.writeInt(this.craftItem.getPriority());
        buffer.writeBoolean(this.craftNow);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        ItemStack item = ByteBufUtils.readItemStack(buffer);
        String name = ByteBufUtils.readUTF8String(buffer);
        int priority = buffer.readInt();
        this.craftItem = new CraftItem(item, name, priority);
        this.craftNow = buffer.readBoolean();
    }

    public static class Handler implements IMessageHandler<CraftItemServerPacket, IMessage> {
        @Override
        public IMessage onMessage(final CraftItemServerPacket message, MessageContext ctx) {
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
                            if (message.craftNow) {
                                entity.currentCraftItem = message.craftItem;
                            } else {
                                entity.addCraftItem(message.craftItem);
                                HelpfulVillagers.network.sendToAll(
                                        new CraftQueueClientPacket(message.id, entity.homeVillage.craftQueue.getAll()));
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
