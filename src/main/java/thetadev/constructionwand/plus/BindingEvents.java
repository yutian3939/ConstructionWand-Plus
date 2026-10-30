package thetadev.constructionwand.plus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.items.CoreNoMaterial;
import thetadev.constructionwand.plus.items.ItemCoreStorage;
import thetadev.constructionwand.plus.material.ContainerBinding;

/**
 * Container binding: sneak and right click a container with the wand to bind it, repeat that to
 * unbind it.
 * <p>
 * The event is cancelled on both sides so the client never predicts a wand action or opens the
 * container it just clicked; the binding itself is only written on the server.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class BindingEvents
{
    private BindingEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled()) return;

        Player player = event.getEntity();
        if(player == null || !player.isCrouching()) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!(wand.getItem() instanceof ItemWand)) return;

        // The bound containers belong to the storage core and to a wand without a material core; the
        // ME network and EMC cores have their own storage.
        WandOptions options = new WandOptions(wand);
        IWandCore materialCore = options.materialCores.get();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // The AE core binds an ME controller instead of a container.
        if(materialCore instanceof thetadev.constructionwand.plus.compat.ae2.ItemCoreAE) {
            if(!thetadev.constructionwand.plus.compat.ae2.AE2Compat.isController(level, pos)) return;

            event.setCanceled(true);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            // A consuming result, so the client does not try the item's air use afterwards: that second
            // packet would reach the sneaking right click of the wand, and with the slay core it would
            // wipe out the mobs around the player while they are only binding a container.
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if(level.isClientSide) return;

            thetadev.constructionwand.plus.compat.ae2.AE2Compat.writeBinding(wand, pos, level);
            player.displayClientMessage(Component.translatable(
                    ConstructionWand.MODID + ".tooltip.ae_bound"), true);
            markDirty(player);
            return;
        }

        if(!(materialCore instanceof ItemCoreStorage) && !(materialCore instanceof CoreNoMaterial)) return;
        if(!ContainerBinding.isContainer(level, pos)) return;

        // Consume the click on both sides, the data is written below on the server only. The consuming
        // result keeps the client from following up with the item's air use, see above.
        event.setCanceled(true);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if(level.isClientSide) return;

        if(ContainerBinding.isBound(wand, pos, level)) {
            if(ContainerBinding.removeBinding(wand, pos, level)) {
                player.displayClientMessage(Component.translatable(
                        ConstructionWand.MODID + ".tooltip.container_unbound", ContainerBinding.count(wand)), true);
                markDirty(player);
            }
            return;
        }

        ContainerBinding.BindResult result = ContainerBinding.storeBinding(wand, pos, level);
        if(result == ContainerBinding.BindResult.FAILED) return;
        if(result == ContainerBinding.BindResult.FULL) {
            player.displayClientMessage(Component.translatable(
                    ConstructionWand.MODID + ".tooltip.binding_full", ContainerBinding.getMaxContainers()), true);
            return;
        }
        player.displayClientMessage(Component.translatable(
                ConstructionWand.MODID + ".tooltip.container_bound", ContainerBinding.count(wand)), true);
        markDirty(player);
    }

    private static void markDirty(Player player) {
        player.getInventory().setChanged();
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }
}
