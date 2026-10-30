package thetadev.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;

import java.util.function.Supplier;

/**
 * Uninstalls a behaviour or material core and hands it back to the player.
 */
public class PacketRemoveCore
{
    public final String coreId;

    public PacketRemoveCore(String coreId) {
        this.coreId = coreId;
    }

    public static void encode(PacketRemoveCore msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.coreId);
    }

    public static PacketRemoveCore decode(FriendlyByteBuf buffer) {
        return new PacketRemoveCore(buffer.readUtf(128));
    }

    public static class Handler
    {
        public static void handle(final PacketRemoveCore msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null) return;

            ResourceLocation id = ResourceLocation.tryParse(msg.coreId);
            if(id == null) return;

            Item item = ForgeRegistries.ITEMS.getValue(id);
            if(!(item instanceof IWandCore core)) return;

            WandOptions options = new WandOptions(wand);
            boolean removed = options.cores.hasUpgrade(core)
                    ? options.cores.removeUpgrade(core)
                    : options.materialCores.removeUpgrade(core);
            if(!removed) return;

            PacketRemoveUpgrade.Handler.returnToPlayer(player, item);
        }
    }
}
