package dev.polaris_light.constructionwand.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Asks the server to undo the most recent wand operation.
 * <p>
 * The undo also rides on a right click while the undo combination is held, which needs the player to
 * aim at a block they operated on. This empty packet is what the undo key binding sends instead, so
 * the last operation can be undone from anywhere.
 */
public record PacketUndoRequest() implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, PacketUndoRequest> CODEC = CustomPacketPayload.codec(
            PacketUndoRequest::encode,
            PacketUndoRequest::new);
    public static final Type<PacketUndoRequest> ID = new Type<>(ConstructionWand.loc("undo_request"));

    public PacketUndoRequest(FriendlyByteBuf buffer) {
        this();
    }

    public void encode(FriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler {
        public static void handle(final PacketUndoRequest msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                            ConstructionWand.undoHistory.undo(player, player.level());
                        }
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.undo_request.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
