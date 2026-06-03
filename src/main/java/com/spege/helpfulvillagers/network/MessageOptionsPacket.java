package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs the configured death/birth message verbosity option. */
public class MessageOptionsPacket implements IMessage {
    private int messageType;
    private int option;

    public MessageOptionsPacket() {
    }

    public MessageOptionsPacket(EnumMessage messageType, int option) {
        this.option = option;
        switch (messageType) {
            case DEATH:
                this.messageType = 0;
                break;
            case BIRTH:
                this.messageType = 1;
                break;
            default:
                this.messageType = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.messageType);
        buffer.writeInt(this.option);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.messageType = buffer.readInt();
        this.option = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<MessageOptionsPacket, IMessage> {
        @Override
        public IMessage onMessage(MessageOptionsPacket packet, MessageContext ctx) {
            // Only updates static config ints (no world access), so no main-thread scheduling needed.
            if (ctx.side == Side.CLIENT) {
                switch (packet.messageType) {
                    case 0:
                        HelpfulVillagers.deathMessageOption = packet.option;
                        break;
                    case 1:
                        HelpfulVillagers.birthMessageOption = packet.option;
                        break;
                }
            }
            return null;
        }
    }
}
