package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs which of the 13 guild-hall types the village has unlocked. */
public class UnlockedHallsPacket implements IMessage {
    private int id;
    private boolean[] unlockedHalls = new boolean[13];

    public UnlockedHallsPacket() {
    }

    public UnlockedHallsPacket(int id, boolean[] unlockedHalls) {
        this.id = id;
        System.arraycopy(unlockedHalls, 0, this.unlockedHalls, 0, this.unlockedHalls.length);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        for (int i = 0; i < this.unlockedHalls.length; ++i) {
            buffer.writeBoolean(this.unlockedHalls[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        for (int i = 0; i < this.unlockedHalls.length; ++i) {
            this.unlockedHalls[i] = buffer.readBoolean();
        }
    }

    public static class Handler implements IMessageHandler<UnlockedHallsPacket, IMessage> {
        @Override
        public IMessage onMessage(final UnlockedHallsPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            entity.homeVillage = new HelpfulVillage();
                            System.arraycopy(message.unlockedHalls, 0, entity.homeVillage.unlockedHalls, 0, message.unlockedHalls.length);
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
