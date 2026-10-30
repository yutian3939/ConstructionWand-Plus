package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;
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
 * Uninstalls a behaviour or material core and hands it back to the player.
 */
public record PacketRemoveCore(String coreId) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketRemoveCore> CODEC = CustomPacketPayload.codec(
            PacketRemoveCore::encode,
            PacketRemoveCore::new);
    public static final Type<PacketRemoveCore> ID = new Type<>(ConstructionWand.loc("remove_core"));

    public PacketRemoveCore(FriendlyByteBuf buffer) {
        this(buffer.readUtf(128));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(coreId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketRemoveCore msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

                        ItemStack wand = WandUtil.holdingWand(player);
                        if(wand == null) return;

                        ResourceLocation id = ResourceLocation.tryParse(msg.coreId);
                        if(id == null) return;

                        Item item = BuiltInRegistries.ITEM.get(id);
                        // An unknown id resolves to air, which is not a wand core and is rejected here.
                        if(!(item instanceof IWandCore core)) return;

                        WandOptions options = new WandOptions(wand);
                        boolean removed = options.cores.hasUpgrade(core)
                                ? options.cores.removeUpgrade(core)
                                : options.materialCores.removeUpgrade(core);
                        if(!removed) return;

                        PacketRemoveUpgrade.Handler.returnToPlayer(player, item);
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.remove_core.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
