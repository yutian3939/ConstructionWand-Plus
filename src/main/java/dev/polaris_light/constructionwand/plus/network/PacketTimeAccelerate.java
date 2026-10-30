package dev.polaris_light.constructionwand.plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreEvents;

/**
 * Asks the server to accelerate a block, or the whole body of blocks it belongs to. The client sends it
 * while ctrl is held, because the server cannot see that key: ctrl turns a click into "all of them".
 */
public record PacketTimeAccelerate(BlockPos pos, boolean connected) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketTimeAccelerate> CODEC = CustomPacketPayload.codec(
            PacketTimeAccelerate::encode,
            PacketTimeAccelerate::new);
    public static final Type<PacketTimeAccelerate> ID = new Type<>(ConstructionWand.loc("time_accelerate"));

    public PacketTimeAccelerate(FriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(connected);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketTimeAccelerate msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;
                        if(!(player.level() instanceof ServerLevel level)) return;
                        TimeCoreEvents.accelerate(player, level, msg.pos(), msg.connected());
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.time_accelerate.failed",
                                e.getMessage()));
                        return null;
                    });
        }
    }
}