package com.spege.helpfulvillagers.network;

import java.util.ArrayList;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.crafting.CraftQueue;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs the village's craft queue (for the crafting GUI). */
public class CraftQueueClientPacket implements IMessage {
    int id;
    private ArrayList<CraftItem> craftQueue = new ArrayList<CraftItem>();
    private int queueSize;

    public CraftQueueClientPacket() {
    }

    public CraftQueueClientPacket(int id, ArrayList<CraftItem> craftQueue) {
        this.id = id;
        this.craftQueue.addAll(craftQueue);
        this.queueSize = craftQueue.size();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeInt(this.queueSize);
        for (CraftItem i : this.craftQueue) {
            ByteBufUtils.writeItemStack(buffer, i.getItem());
            ByteBufUtils.writeUTF8String(buffer, i.getName());
            buffer.writeInt(i.getPriority());
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.queueSize = buffer.readInt();
        for (int i = 0; i < this.queueSize; ++i) {
            ItemStack item = ByteBufUtils.readItemStack(buffer);
            String name = ByteBufUtils.readUTF8String(buffer);
            int priority = buffer.readInt();
            this.craftQueue.add(new CraftItem(item, name, priority));
        }
    }

    public static class Handler implements IMessageHandler<CraftQueueClientPacket, IMessage> {
        @Override
        public IMessage onMessage(final CraftQueueClientPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            if (entity.homeVillage == null) {
                                entity.homeVillage = new HelpfulVillage();
                            }
                            entity.homeVillage.craftQueue = new CraftQueue(message.craftQueue);
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
