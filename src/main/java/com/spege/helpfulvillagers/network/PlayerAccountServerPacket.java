package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: pushes a player's barter account balance back to the server's village economy. */
public class PlayerAccountServerPacket implements IMessage {
    private int playerID;
    private int villagerID;
    private int amount;

    public PlayerAccountServerPacket() {
    }

    public PlayerAccountServerPacket(EntityPlayer player, AbstractVillager villager) {
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

    public static class Handler implements IMessageHandler<PlayerAccountServerPacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerAccountServerPacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP sender = ctx.getServerHandler().player;
                sender.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EntityPlayer player = (EntityPlayer) sender.world.getEntityByID(message.playerID);
                            if (player == null) {
                                return;
                            }
                            AbstractVillager villager = (AbstractVillager) sender.world.getEntityByID(message.villagerID);
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
