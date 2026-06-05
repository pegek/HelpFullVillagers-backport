package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.EntityBuilder;
import com.spege.helpfulvillagers.enums.EnumConstructionType;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class ConstructionJobPacket implements IMessage {
    private int villagerID;
    private int playerID;
    private int command;

    public ConstructionJobPacket() {
    }

    public ConstructionJobPacket(int villagerID, int playerID, int command) {
        this.villagerID = villagerID;
        this.playerID = playerID;
        this.command = command;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.villagerID);
        buffer.writeInt(this.playerID);
        buffer.writeInt(this.command);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.villagerID = buffer.readInt();
        this.playerID = buffer.readInt();
        this.command = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<ConstructionJobPacket, IMessage> {
        @Override
        public IMessage onMessage(ConstructionJobPacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                // Use a scheduled task on the main thread
                p.getServerWorld().addScheduledTask(() -> {
                    World world = p.world;
                    try {
                        EntityBuilder entity = (EntityBuilder) world.getEntityByID(message.villagerID);
                        if (entity != null) {
                            EntityPlayer player = (EntityPlayer) world.getEntityByID(message.playerID);
                            if (player != null) {
                                EnumConstructionType type = EnumConstructionType.values()[message.command];
                                entity.processJobRequest(type, player);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            return null;
        }
    }
}
