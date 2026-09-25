package com.xinyihl.morewandcore.storage;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.item.ItemCoreStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;

/**
 * Binds the containers a storage core wand draws from.
 * <p>
 * Construction Wand Legacy only allows this for its own default core, so the gesture is handled here
 * for the addon core. Every click adds one container to the wand: the first one bound is used first,
 * and the configuration screen reorders the list and removes entries from it.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ContainerBindingEvents {
    private ContainerBindingEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !player.isSneaking()) {
            return;
        }
        ItemStack wand = player.getHeldItem(event.getHand());
        if (wand.isEmpty() || !(wand.getItem() instanceof ItemWand)) {
            return;
        }
        // The storage core is a material core, so it lives in the material selection.
        if (!(WandDataCodec.read(wand).getSelectedMaterialCore() instanceof ItemCoreStorage)) {
            return;
        }
        TileEntity tile = player.world.getTileEntity(event.getPos());
        // A container that only holds fluid is a binding as well: it is where the fluid storage setting
        // puts the fluid the digging core clears.
        if (tile == null || (!tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                && !tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null))) {
            return;
        }

        // Cancel on both sides so the client does not predict a wand action for this click.
        event.setCanceled(true);
        if (player.world.isRemote) {
            return;
        }

        BoundContainerSourceFactory.BindResult result = BoundContainerSourceFactory.storeBinding(wand, event.getPos(), player.world.provider.getDimension());
        if (result == BoundContainerSourceFactory.BindResult.FAILED) {
            return;
        }
        if (result == BoundContainerSourceFactory.BindResult.FULL) {
            player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".tooltip.binding_full", BoundContainerSourceFactory.getMaxContainers()), true);
            return;
        }
        int bound = BoundContainerSourceFactory.readBindings(wand).size();
        player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".tooltip.storage_bound", bound), true);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        }
    }
}
