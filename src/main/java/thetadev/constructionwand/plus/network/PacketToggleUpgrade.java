package thetadev.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;

import java.util.function.Supplier;

/**
 * Switches one installed passive upgrade on or off without uninstalling it.
 */
public class PacketToggleUpgrade
{
    public final String upgradeId;
    public final boolean disabled;

    public PacketToggleUpgrade(String upgradeId, boolean disabled) {
        this.upgradeId = upgradeId;
        this.disabled = disabled;
    }

    public static void encode(PacketToggleUpgrade msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.upgradeId);
        buffer.writeBoolean(msg.disabled);
    }

    public static PacketToggleUpgrade decode(FriendlyByteBuf buffer) {
        return new PacketToggleUpgrade(buffer.readUtf(128), buffer.readBoolean());
    }

    public static class Handler
    {
        public static void handle(final PacketToggleUpgrade msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null) return;

            Item upgrade = PassiveUpgrades.byRegistryName(wand, msg.upgradeId);
            if(upgrade == null) return;

            PassiveUpgrades.setDisabled(wand, upgrade, msg.disabled);
            player.getInventory().setChanged();
        }
    }
}
