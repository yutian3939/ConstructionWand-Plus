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
import dev.polaris_light.constructionwand.plus.basics.ApotheosisLevels;
import dev.polaris_light.constructionwand.plus.compat.apotheosis.ApotheosisBridge;

/**
 * Carries the four numbers of the apotheosis core that were picked on the wand screen. They travel as plain
 * integers; the server clamps each of them to its own range, so a client cannot ask for a tier that does not
 * exist or a stat outside zero to a hundred.
 */
public record PacketApotheosis(int tier, int eterna, int quanta, int arcana) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketApotheosis> CODEC = CustomPacketPayload.codec(
            PacketApotheosis::encode,
            PacketApotheosis::new);
    public static final Type<PacketApotheosis> ID = new Type<>(ConstructionWand.loc("apotheosis_levels"));

    public PacketApotheosis(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(tier);
        buffer.writeVarInt(eterna);
        buffer.writeVarInt(quanta);
        buffer.writeVarInt(arcana);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketApotheosis msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null || wand.isEmpty()) return;
                        if(ApotheosisLevels.setAll(wand, msg.tier(), msg.eterna(), msg.quanta(), msg.arcana())) {
                            player.getInventory().setChanged();
                        }
                        // The world tier belongs to the player rather than to a table, so it is applied as soon
                        // as the slider moves instead of waiting for the next right click.
                        if(ApotheosisBridge.isLoaded()) ApotheosisBridge.applyTier(player, msg.tier());
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.extra_option.failed",
                                e.getMessage()));
                        return null;
                    });
        }
    }
}