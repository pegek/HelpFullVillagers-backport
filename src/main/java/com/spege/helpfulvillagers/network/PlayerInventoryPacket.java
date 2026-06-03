package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client -> server: overwrites a player's inventory (used by the barter/exchange GUI to hand items
 * to/from a villager). {@code id} is the target player entity id. See {@link AbstractVillager} barter.
 */
public class PlayerInventoryPacket implements IMessage {
    private int id;
    private ItemStack[] main = new ItemStack[36];
    private boolean sendMain;
    private ItemStack[] equipment = new ItemStack[4];
    private boolean sendEquip;

    public PlayerInventoryPacket() {
    }

    public PlayerInventoryPacket(int id, ItemStack[] main, ItemStack[] equipment) {
        this.id = id;
        if (main != null) {
            System.arraycopy(main, 0, this.main, 0, main.length);
            this.sendMain = true;
        } else {
            this.sendMain = false;
        }
        if (equipment != null) {
            System.arraycopy(equipment, 0, this.equipment, 0, equipment.length);
            this.sendEquip = true;
        } else {
            this.sendEquip = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int i;
        buffer.writeInt(this.id);
        buffer.writeBoolean(this.sendMain);
        buffer.writeBoolean(this.sendEquip);
        if (this.sendMain) {
            for (i = 0; i < this.main.length; ++i) {
                ByteBufUtils.writeItemStack(buffer, this.main[i] == null ? ItemStack.EMPTY : this.main[i]);
            }
        }
        if (this.sendEquip) {
            for (i = 0; i < this.equipment.length; ++i) {
                ByteBufUtils.writeItemStack(buffer, this.equipment[i] == null ? ItemStack.EMPTY : this.equipment[i]);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int i;
        this.id = buffer.readInt();
        this.sendMain = buffer.readBoolean();
        this.sendEquip = buffer.readBoolean();
        if (this.sendMain) {
            for (i = 0; i < this.main.length; ++i) {
                this.main[i] = ByteBufUtils.readItemStack(buffer);
            }
        }
        if (this.sendEquip) {
            for (i = 0; i < this.equipment.length; ++i) {
                this.equipment[i] = ByteBufUtils.readItemStack(buffer);
            }
        }
    }

    public static class Handler implements IMessageHandler<PlayerInventoryPacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerInventoryPacket message, MessageContext ctx) {
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
                            for (int i = 0; i < message.main.length; ++i) {
                                entity.inventory.setInventorySlotContents(i, message.main[i]);
                            }
                            for (int i = 0; i < message.equipment.length; ++i) {
                                entity.inventory.setInventorySlotContents(i + message.main.length, message.equipment[i]);
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
