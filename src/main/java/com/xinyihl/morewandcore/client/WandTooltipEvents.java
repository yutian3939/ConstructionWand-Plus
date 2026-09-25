package com.xinyihl.morewandcore.client;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreAE;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.basics.ContainerBinding;
import com.xinyihl.morewandcore.item.ItemCoreDigging;
import com.xinyihl.morewandcore.item.ItemCoreStorage;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Adds the addon hints to the wand tooltip: the container binding of the storage core and how to
 * take the installed cores off again.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class WandTooltipEvents {
    private WandTooltipEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return;
        }
        WandState state = WandDataCodec.read(stack);
        IWandCore behavior = state.getSelectedCore();
        IWandCore material = state.getSelectedMaterialCore();
        boolean storageSelected = material instanceof ItemCoreStorage;
        if (storageSelected || behavior instanceof ItemCoreDigging) {
            int bound = ContainerBinding.count(stack);
            if (bound > 0) {
                event.getToolTip().add(TextFormatting.GREEN + I18n.format(Tags.MOD_ID + ".tooltip.storage_bound", bound));
            } else if (storageSelected) {
                event.getToolTip().add(TextFormatting.GRAY + I18n.format(Tags.MOD_ID + ".tooltip.storage_unbound"));
            }
        }
        // An AE core is bound to an ME network instead of a container, so it gets its own hint.
        if (material instanceof ItemCoreAE && !CompatRegistrar.hasAE2Binding(stack, material)) {
            event.getToolTip().add(TextFormatting.GRAY + I18n.format(Tags.MOD_ID + ".tooltip.ae_unbound"));
        }
        if (!WandDataCodec.coreItems(stack).isEmpty()) {
            event.getToolTip().add(TextFormatting.DARK_GRAY + I18n.format(Tags.MOD_ID + ".tooltip.core_removal"));
        }
    }
}
