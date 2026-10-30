package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandUpgrade;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Uninstalls a passive upgrade and hands the component back to the player. Installing one still only
 * works in the crafting grid.
 */
public record PacketRemoveUpgrade(String upgradeId) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketRemoveUpgrade> CODEC = CustomPacketPayload.codec(
            PacketRemoveUpgrade::encode,
            PacketRemoveUpgrade::new);
    public static final Type<PacketRemoveUpgrade> ID = new Type<>(ConstructionWand.loc("remove_upgrade"));

    public PacketRemoveUpgrade(FriendlyByteBuf buffer) {
        this(buffer.readUtf(128));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(upgradeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketRemoveUpgrade msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null) return;

                        ResourceLocation id = ResourceLocation.tryParse(msg.upgradeId);
                        if(id == null) return;

                        Item item = BuiltInRegistries.ITEM.get(id);
                        // An unknown id resolves to air, which is not an upgrade and is rejected here.
                        if(!(item instanceof IWandUpgrade upgrade)) return;

                        WandOptions options = new WandOptions(wand);
                        if(!options.upgrades.hasUpgrade(upgrade)) return;
                        if(!options.upgrades.removeUpgrade(upgrade)) return;

                        returnToPlayer(player, item);
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.remove_upgrade.failed", e.getMessage()));
                        return null;
                    });
        }

        static void returnToPlayer(ServerPlayer player, Item item) {
            ItemStack returned = new ItemStack(item);
            if(!player.getInventory().add(returned)) player.drop(returned, false);
            player.getInventory().setChanged();
            ConstructionWand.LOGGER.debug("Removed wand upgrade {} from a wand", BuiltInRegistries.ITEM.getKey(item));
        }
    }
}
