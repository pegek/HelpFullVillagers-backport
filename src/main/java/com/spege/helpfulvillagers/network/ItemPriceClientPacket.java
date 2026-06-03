package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.econ.ItemPrice;
import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs one item's economy price/supply/demand into the village economy. */
public class ItemPriceClientPacket implements IMessage {
    private int id;
    private ItemStack item;
    private int price;
    private double supply;
    private double demand;

    public ItemPriceClientPacket() {
    }

    public ItemPriceClientPacket(AbstractVillager villager, ItemPrice itemPrice) {
        this.id = villager.getEntityId();
        this.item = itemPrice.getItem();
        this.price = itemPrice.getOriginalPrice();
        this.supply = itemPrice.getSupply();
        this.demand = itemPrice.getDemand();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeInt(this.price);
        buffer.writeDouble(this.supply);
        buffer.writeDouble(this.demand);
        ByteBufUtils.writeItemStack(buffer, this.item == null ? ItemStack.EMPTY : this.item);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.price = buffer.readInt();
        this.supply = buffer.readDouble();
        this.demand = buffer.readDouble();
        this.item = ByteBufUtils.readItemStack(buffer);
    }

    public static class Handler implements IMessageHandler<ItemPriceClientPacket, IMessage> {
        @Override
        public IMessage onMessage(final ItemPriceClientPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            ItemPrice itemPrice = new ItemPrice(message.item, message.price, message.supply, message.demand);
                            entity.homeVillage.economy.putItemPrice(itemPrice);
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
