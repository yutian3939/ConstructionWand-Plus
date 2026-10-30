package dev.polaris_light.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.plus.basics.SuperFortuneLevels;

/**
 * Carries the super fortune factor chosen on the wand screen. The factor is a plain number, so it travels
 * as one; the server clamps it to the cap of the wand's tier, a client cannot ask for more.
 */
public record PacketSuperFortune(int value) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketSuperFortune> CODEC = CustomPacketPayload.codec(
            PacketSuperFortune::encode,
            PacketSuperFortune::new);
    public static final Type<PacketSuperFortune> ID = new Type<>(ConstructionWand.loc("super_fortune"));

    public PacketSuperFortune(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketSuperFortune msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null || wand.isEmpty()) return;
                        if(SuperFortuneLevels.set(wand, msg.value())) player.getInventory().setChanged();
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.extra_option.failed",
                                e.getMessage()));
                        return null;
                    });
        }
    }
}