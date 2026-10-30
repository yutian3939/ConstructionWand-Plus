package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.plus.basics.PassiveUpgrades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Switches one installed passive upgrade on or off without uninstalling it.
 */
public record PacketToggleUpgrade(String upgradeId, boolean disabled) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketToggleUpgrade> CODEC = CustomPacketPayload.codec(
            PacketToggleUpgrade::encode,
            PacketToggleUpgrade::new);
    public static final Type<PacketToggleUpgrade> ID = new Type<>(ConstructionWand.loc("toggle_upgrade"));

    public PacketToggleUpgrade(FriendlyByteBuf buffer) {
        this(buffer.readUtf(128), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(upgradeId);
        buffer.writeBoolean(disabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketToggleUpgrade msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null) return;

                        Item upgrade = PassiveUpgrades.byRegistryName(wand, msg.upgradeId);
                        if(upgrade == null) return;

                        PassiveUpgrades.setDisabled(wand, upgrade, msg.disabled);
                        player.getInventory().setChanged();
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.toggle_upgrade.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
