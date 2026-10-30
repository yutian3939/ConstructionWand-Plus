package thetadev.constructionwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.ConstructionWand;

import java.util.function.Supplier;

/**
 * Asks the server to undo the most recent wand operation.
 * <p>
 * The undo used to ride on a right click while the undo combination was held, which needed a gate to
 * keep that click from also acting on the world. The key binding sends this empty packet instead, so
 * the player can undo without aiming at a block.
 */
public class PacketUndoRequest
{
    public PacketUndoRequest() {
    }

    public static void encode(PacketUndoRequest msg, FriendlyByteBuf buffer) {
    }

    public static PacketUndoRequest decode(FriendlyByteBuf buffer) {
        return new PacketUndoRequest();
    }

    public static class Handler
    {
        public static void handle(final PacketUndoRequest msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ctx.get().enqueueWork(() -> ConstructionWand.instance.undoHistory.undo(player, player.level()));
            ctx.get().setPacketHandled(true);
        }
    }
}
