package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Asks the server to undo the last wand operation.
 * <p>
 * The undo used to ride on a right click while the undo combination was held, which needed a gate to
 * keep that click from also digging. It is a key binding of its own now, so the request is a packet
 * of its own and nothing has to be swallowed afterwards.
 */
public class PacketUndoRequest implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketUndoRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketUndoRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> ConstructionWandLegacy.instance.getRuntime().getUndoService().undo(player));
            return null;
        }
    }
}
