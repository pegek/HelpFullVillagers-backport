package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs which entity a villager is following as its leader ({@code -1} = none). */
public class LeaderPacket implements IMessage {
    private int id;
    private int leaderID;

    public LeaderPacket() {
    }

    public LeaderPacket(int id, int leaderID) {
        this.id = id;
        this.leaderID = leaderID;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeInt(this.leaderID);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.leaderID = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<LeaderPacket, IMessage> {
        @Override
        public IMessage onMessage(final LeaderPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            net.minecraft.world.World world = Minecraft.getMinecraft().world;
                            AbstractVillager entity = (AbstractVillager) world.getEntityByID(message.id);
                            entity.leader = message.leaderID < 0 ? null : (EntityLivingBase) world.getEntityByID(message.leaderID);
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
