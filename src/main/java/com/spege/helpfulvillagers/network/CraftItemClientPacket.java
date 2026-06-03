package com.spege.helpfulvillagers.network;

import java.util.ArrayList;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs the villager's current craft job + collected/needed materials (craft GUI). */
public class CraftItemClientPacket implements IMessage {
    private int id;
    private CraftItem currentCraftItem;
    private ArrayList<ItemStack> materialsCollected = new ArrayList<ItemStack>();
    private int collectedSize;
    private ArrayList<ItemStack> materialsNeeded = new ArrayList<ItemStack>();
    private int neededSize;

    public CraftItemClientPacket() {
    }

    public CraftItemClientPacket(int id, CraftItem craftItem, ArrayList<ItemStack> materialsCollected,
            ArrayList<ItemStack> materialsNeeded) {
        this.id = id;
        this.currentCraftItem = craftItem;
        this.materialsCollected.addAll(materialsCollected);
        this.collectedSize = materialsCollected.size();
        this.materialsNeeded.addAll(materialsNeeded);
        this.neededSize = materialsNeeded.size();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        if (this.currentCraftItem != null) {
            buffer.writeBoolean(true);
            ByteBufUtils.writeItemStack(buffer, this.currentCraftItem.getItem());
            ByteBufUtils.writeUTF8String(buffer, this.currentCraftItem.getName());
            buffer.writeInt(this.currentCraftItem.getPriority());
        } else {
            buffer.writeBoolean(false);
        }
        buffer.writeInt(this.collectedSize);
        for (ItemStack i : this.materialsCollected) {
            ByteBufUtils.writeItemStack(buffer, i);
        }
        buffer.writeInt(this.neededSize);
        for (ItemStack i : this.materialsNeeded) {
            ByteBufUtils.writeItemStack(buffer, i);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int i;
        this.id = buffer.readInt();
        boolean read = buffer.readBoolean();
        if (read) {
            ItemStack item = ByteBufUtils.readItemStack(buffer);
            String name = ByteBufUtils.readUTF8String(buffer);
            int priority = buffer.readInt();
            this.currentCraftItem = new CraftItem(item, name, priority);
        } else {
            this.currentCraftItem = null;
        }
        this.collectedSize = buffer.readInt();
        for (i = 0; i < this.collectedSize; ++i) {
            this.materialsCollected.add(ByteBufUtils.readItemStack(buffer));
        }
        this.neededSize = buffer.readInt();
        for (i = 0; i < this.neededSize; ++i) {
            this.materialsNeeded.add(ByteBufUtils.readItemStack(buffer));
        }
    }

    public static class Handler implements IMessageHandler<CraftItemClientPacket, IMessage> {
        @Override
        public IMessage onMessage(final CraftItemClientPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            entity.currentCraftItem = message.currentCraftItem;
                            entity.inventory.materialsCollected.clear();
                            entity.inventory.materialsCollected.addAll(message.materialsCollected);
                            entity.materialsNeeded.clear();
                            entity.materialsNeeded.addAll(message.materialsNeeded);
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
