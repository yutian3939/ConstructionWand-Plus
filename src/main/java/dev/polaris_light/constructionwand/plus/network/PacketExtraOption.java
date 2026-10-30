package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.plus.basics.ExtraWandOption;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One of the plus settings of the wand, as chosen on the wand screen. The settings live in their own
 * root tag, so they travel in their own packet.
 */
public record PacketExtraOption(int option, int value) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketExtraOption> CODEC = CustomPacketPayload.codec(
            PacketExtraOption::encode,
            PacketExtraOption::new);
    public static final Type<PacketExtraOption> ID = new Type<>(ConstructionWand.loc("extra_option"));

    public PacketExtraOption(ExtraWandOption option, int value) {
        this(option.ordinal(), value);
    }

    public PacketExtraOption(FriendlyByteBuf buffer) {
        this(buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(option);
        buffer.writeByte(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketExtraOption msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null) return;
                        if(msg.option < 0 || msg.option >= ExtraWandOption.values().length) return;

                        ExtraWandOption.setIndex(wand, ExtraWandOption.values()[msg.option], msg.value);
                        player.getInventory().setChanged();
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.extra_option.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
