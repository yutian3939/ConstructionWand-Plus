package thetadev.constructionwand.plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;

import java.util.function.Supplier;

/**
 * Uninstalls a passive upgrade and hands the component back to the player. Installing one still only
 * works in the crafting grid.
 */
public class PacketRemoveUpgrade
{
    public final String upgradeId;

    public PacketRemoveUpgrade(String upgradeId) {
        this.upgradeId = upgradeId;
    }

    public static void encode(PacketRemoveUpgrade msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.upgradeId);
    }

    public static PacketRemoveUpgrade decode(FriendlyByteBuf buffer) {
        return new PacketRemoveUpgrade(buffer.readUtf(128));
    }

    public static class Handler
    {
        public static void handle(final PacketRemoveUpgrade msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null) return;

            ResourceLocation id = ResourceLocation.tryParse(msg.upgradeId);
            if(id == null) return;

            Item upgrade = ForgeRegistries.ITEMS.getValue(id);
            if(upgrade == null) return;

            WandOptions options = new WandOptions(wand);
            if(!options.upgrades.hasUpgrade((thetadev.constructionwand.api.IWandUpgrade) upgrade)) return;
            if(!options.upgrades.removeUpgrade((thetadev.constructionwand.api.IWandUpgrade) upgrade)) return;

            returnToPlayer(player, upgrade);
        }

        static void returnToPlayer(ServerPlayer player, Item item) {
            ItemStack returned = new ItemStack(item);
            if(!player.getInventory().add(returned)) player.drop(returned, false);
            player.getInventory().setChanged();
            ConstructionWand.LOGGER.debug("Removed wand upgrade {} from a wand", ForgeRegistries.ITEMS.getKey(item));
        }
    }
}
