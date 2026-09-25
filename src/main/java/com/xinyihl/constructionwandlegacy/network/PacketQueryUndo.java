package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketQueryUndo implements IMessage {
    private boolean undoPressed;
    private boolean valid;

    public PacketQueryUndo() {
    }

    public PacketQueryUndo(boolean undoPressed) {
        this.undoPressed = undoPressed;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        try {
            if (!NetworkProtocol.readHeader(buf) || buf.readableBytes() != 1) {
                return;
            }
            int encoded = buf.readUnsignedByte();
            if (encoded > 1) {
                return;
            }
            undoPressed = encoded == 1;
            valid = true;
        } catch (IndexOutOfBoundsException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid undo query");
        }
        NetworkProtocol.writeHeader(buf);
        buf.writeBoolean(undoPressed);
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isUndoPressed() {
        return undoPressed;
    }

    public static class Handler implements IMessageHandler<PacketQueryUndo, IMessage> {
        @Override
        public IMessage onMessage(PacketQueryUndo message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> ConstructionWandLegacy.instance.getRuntime().getUndoService().updateClient(player, message.undoPressed));
            return null;
        }
    }
}
