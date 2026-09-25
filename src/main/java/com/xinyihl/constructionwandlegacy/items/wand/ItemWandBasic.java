package com.xinyihl.constructionwandlegacy.items.wand;

import com.xinyihl.constructionwandlegacy.wand.WandTier;
import net.minecraft.item.ItemStack;

public class ItemWandBasic extends ItemWand {
    private final ItemStack repairItem;

    public ItemWandBasic(WandTier tier, ItemStack repairItem) {
        super(tier);
        this.repairItem = repairItem;
        setMaxDamage(getSpec().getDurability());
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getSpec().getDurability();
    }

    @Override
    public int remainingDurability(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getItemDamage() + 1);
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return ItemStack.areItemsEqual(repair, repairItem);
    }
}
