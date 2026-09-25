package com.xinyihl.morewandcore.item;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A passive wand upgrade: it implements {@link IWandUpgrade} without being a core, so it is installed
 * on the wand and always active instead of being selectable. Several of them can be carried at once.
 */
public class ItemWandUpgrade extends Item implements IWandUpgrade {
    private final String descriptionKey;

    public ItemWandUpgrade(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GRAY + I18n.format(descriptionKey));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format(Tags.MOD_ID + ".upgrade.hint"));
    }
}
