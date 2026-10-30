package thetadev.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.plus.basics.ExtraWandOption;

import java.util.function.Supplier;

/**
 * One of the plus settings of the wand, as chosen on the wand screen. The settings live in their own
 * root tag, so they travel in their own packet.
 */
public class PacketExtraOption
{
    public final int option;
    public final int value;

    public PacketExtraOption(ExtraWandOption option, int value) {
        this.option = option.ordinal();
        this.value = value;
    }

    private PacketExtraOption(int option, int value) {
        this.option = option;
        this.value = value;
    }

    public static void encode(PacketExtraOption msg, FriendlyByteBuf buffer) {
        buffer.writeByte(msg.option);
        buffer.writeByte(msg.value);
    }

    public static PacketExtraOption decode(FriendlyByteBuf buffer) {
        return new PacketExtraOption(buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }

    public static class Handler
    {
        public static void handle(final PacketExtraOption msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null) return;
            if(msg.option < 0 || msg.option >= ExtraWandOption.values().length) return;

            ExtraWandOption.setIndex(wand, ExtraWandOption.values()[msg.option], msg.value);
            player.getInventory().setChanged();
        }
    }
}
