package dev.polaris_light.constructionwand.plus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;
import dev.polaris_light.constructionwand.plus.items.CoreNoMaterial;
import dev.polaris_light.constructionwand.plus.items.ItemCoreAE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreStorage;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;

/**
 * Container binding: sneak and right click a container with the wand to bind it, repeat that to
 * unbind it.
 * <p>
 * The event is cancelled on both sides so the client never predicts a wand action or opens the
 * container it just clicked; the binding itself is only written on the server.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
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

        // The bound containers belong to the storage core and to a wand without a material core.
        WandOptions options = new WandOptions(wand);
        IWandCore materialCore = options.materialCores.get();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // The AE core binds the controller of an ME network instead of a container.
        if(materialCore instanceof ItemCoreAE) {
            if(!AeGridAccess.isController(level, pos)) return;

            // Consume the click on both sides, the data is written below on the server only. The
            // consuming result keeps the client from following up with the item's air use, see above.
            consume(event);
            if(level.isClientSide) return;

            if(AeGridAccess.isBoundAt(wand, pos, level)) {
                AeGridAccess.clearBinding(wand);
                player.displayClientMessage(Component.translatable(
                        ConstructionWand.MODID + ".tooltip.ae_unbound"), true);
            }
            else {
                AeGridAccess.writeBinding(wand, pos, level);
                player.displayClientMessage(Component.translatable(
                        ConstructionWand.MODID + ".tooltip.ae_bound"), true);
            }
            markDirty(player);
            return;
        }

        if(!(materialCore instanceof ItemCoreStorage) && !(materialCore instanceof CoreNoMaterial)) return;
        if(!ContainerBinding.isContainer(level, pos)) return;

        // Consume the click on both sides, the data is written below on the server only. The consuming
        // result keeps the client from following up with the item's air use, see above.
        consume(event);
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

    /**
     * Consumes the click on both sides, so the client does not predict a wand action or open the
     * container it just clicked. The server still runs this handler, because the client sends its use
     * packet either way and only skips its own prediction; what keeps the click from also becoming the
     * wand's own sneaking right click in the air is the screen being consumed at the input, see
     * {@code client.KeybindHandler#onInteractionKey}.
     */
    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
