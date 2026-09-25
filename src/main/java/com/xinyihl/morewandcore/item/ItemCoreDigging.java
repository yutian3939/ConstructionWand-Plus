package com.xinyihl.morewandcore.item;

import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.wand.ActionDigging;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Wand core that breaks blocks and keeps their drops.
 */
public class ItemCoreDigging extends Item implements IWandCore {

    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x1FBF8F;

    public ItemCoreDigging() {
        setMaxStackSize(1);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public WandAction getWandAction() {
        return ActionDigging.INSTANCE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".option.cores." + getRegistryName() + ".desc"));
        tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.core_tip"));
    }
}
