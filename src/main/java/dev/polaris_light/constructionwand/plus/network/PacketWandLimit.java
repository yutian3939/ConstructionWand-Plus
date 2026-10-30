package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.basics.WandLimit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets the per-wand block limit chosen on the wand screen. The configured maximum of the wand tier
 * stays the cap, so a client cannot raise its own limit beyond what the server allows.
 */
public record PacketWandLimit(int limit) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketWandLimit> CODEC = CustomPacketPayload.codec(
            PacketWandLimit::encode,
            PacketWandLimit::new);
    public static final Type<PacketWandLimit> ID = new Type<>(ConstructionWand.loc("wand_limit"));

    public PacketWandLimit(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketWandLimit msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null || !(wand.getItem() instanceof ItemWand)) return;

                        // The cap is the configured maximum of the wand tier, not the value it uses now, or
                        // a lowered setting could never be raised again.
                        int configured = WandLimit.configuredMax(wand);
                        int limit = Math.max(1, Math.min(msg.limit, configured));
                        if(WandLimit.set(wand, limit)) player.getInventory().setChanged();
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.wand_limit.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
