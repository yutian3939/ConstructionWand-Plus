package thetadev.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.basics.WandLimit;

import java.util.function.Supplier;

/**
 * Sets the per-wand block limit chosen on the wand screen. The configured maximum of the wand tier
 * stays the cap, so a client cannot raise its own limit beyond what the server allows.
 */
public class PacketWandLimit
{
    public final int limit;

    public PacketWandLimit(int limit) {
        this.limit = limit;
    }

    public static void encode(PacketWandLimit msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.limit);
    }

    public static PacketWandLimit decode(FriendlyByteBuf buffer) {
        return new PacketWandLimit(buffer.readVarInt());
    }

    public static class Handler
    {
        public static void handle(final PacketWandLimit msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null || !(wand.getItem() instanceof ItemWand)) return;

            thetadev.constructionwand.basics.option.WandOptions options =
                    new thetadev.constructionwand.basics.option.WandOptions(wand);
            int configured = Math.max(1, options.cores.get().getWandAction().getLimit(wand));
            int limit = Math.max(1, Math.min(msg.limit, configured));
            if(WandLimit.set(wand, limit)) player.getInventory().setChanged();
        }
    }
}
