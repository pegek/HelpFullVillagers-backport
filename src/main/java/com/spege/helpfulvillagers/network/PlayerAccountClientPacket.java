package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs a player's barter account balance with a merchant's village economy. */
public class PlayerAccountClientPacket implements IMessage {
    private int playerID;
    private int villagerID;
    private int amount;

    public PlayerAccountClientPacket() {
    }

    public PlayerAccountClientPacket(EntityPlayer player, AbstractVillager villager) {
        this.playerID = player.getEntityId();
        this.villagerID = villager.getEntityId();
        this.amount = villager.homeVillage.economy.getAccount(player);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.playerID);
        buffer.writeInt(this.villagerID);
        buffer.writeInt(this.amount);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.playerID = buffer.readInt();
        this.villagerID = buffer.readInt();
        this.amount = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<PlayerAccountClientPacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerAccountClientPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            net.minecraft.world.World world = Minecraft.getMinecraft().world;
                            EntityPlayer player = (EntityPlayer) world.getEntityByID(message.playerID);
                            if (player == null) {
                                return;
                            }
                            AbstractVillager villager = (AbstractVillager) world.getEntityByID(message.villagerID);
                            if (villager == null) {
                                return;
                            }
                            villager.homeVillage.economy.setAccount(player, message.amount);
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
