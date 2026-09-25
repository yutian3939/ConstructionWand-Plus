package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
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

public abstract class ItemCore extends Item implements IWandCore {
    protected ItemCore() {
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".option.cores." + getRegistryName() + ".desc"));
        tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.core_tip"));
    }
}
