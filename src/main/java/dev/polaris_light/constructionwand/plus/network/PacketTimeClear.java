package dev.polaris_light.constructionwand.plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreEvents;

import javax.annotation.Nullable;

/**
 * Asks the server to let go of accelerated blocks: one of them when a position is given, all of them when
 * it is not. The wand screen offers both, so a single block can be released without hunting for it in the
 * world and a player who lost track can release everything at once.
 */
public record PacketTimeClear(@Nullable BlockPos pos) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketTimeClear> CODEC = CustomPacketPayload.codec(
            PacketTimeClear::encode,
            PacketTimeClear::new);
    public static final Type<PacketTimeClear> ID = new Type<>(ConstructionWand.loc("time_clear"));

    public PacketTimeClear() {
        this((BlockPos) null);
    }

    public PacketTimeClear(FriendlyByteBuf buffer) {
        this(buffer.readBoolean() ? buffer.readBlockPos() : null);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(pos != null);
        if(pos != null) buffer.writeBlockPos(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketTimeClear msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        if(msg.pos() != null) {
                            TimeAcceleration.Result result = TimeAcceleration.remove(player.serverLevel(), msg.pos());
                            player.displayClientMessage(Component.translatable(
                                    ConstructionWand.MODID + ".time.cancelled." + result.name().toLowerCase()), true);
                            TimeCoreEvents.broadcast();
                            return;
                        }

                        int removed = TimeAcceleration.clearOwnedBy(player.getUUID());
                        player.displayClientMessage(Component.translatable(
                                ConstructionWand.MODID + ".time.cleared", removed), true);
                        if(removed > 0) TimeCoreEvents.broadcast();
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.time_clear.failed",
                                e.getMessage()));
                        return null;
                    });
        }
    }
}