package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs a villager's home-village center coordinates. */
public class VillageSyncPacket implements IMessage {
    private int[] coords = new int[3];
    private int id;

    public VillageSyncPacket() {
    }

    public VillageSyncPacket(HelpfulVillage village, AbstractVillager villager) {
        this.coords[0] = village.initialCenter.getX();
        this.coords[1] = village.initialCenter.getY();
        this.coords[2] = village.initialCenter.getZ();
        this.id = villager.getEntityId();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        for (int i = 0; i < 3; ++i) {
            buffer.writeInt(this.coords[i]);
        }
        buffer.writeInt(this.id);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        for (int i = 0; i < 3; ++i) {
            this.coords[i] = buffer.readInt();
        }
        this.id = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<VillageSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final VillageSyncPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (entity == null) {
                                return;
                            }
                            BlockPos coords = new BlockPos(message.coords[0], message.coords[1], message.coords[2]);
                            // Client-side homeVillage is a lightweight data container populated by sync
                            // packets (the authoritative villages live on the server). Create it on demand
                            // and never touch the shared static villages list from the client thread.
                            if (entity.homeVillage == null) {
                                entity.homeVillage = new HelpfulVillage();
                            }
                            entity.homeVillage.initialCenter = coords;
                            entity.villageCenter = coords;
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
