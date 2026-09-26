package com.xinyihl.constructionwandlegacy.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Dedicated creative tab for everything this mod adds, so the wand, its cores and its upgrades are
 * not scattered across the vanilla tools and misc tabs. The icon is the infinity wand and the items
 * are ordered by category: wands, behaviour cores, material cores, then upgrades.
 */
public final class WandCreativeTab extends CreativeTabs {
    public static final WandCreativeTab INSTANCE = new WandCreativeTab();

    private WandCreativeTab() {
        super("constructionwandlegacy");
    }

    @Override
    public ItemStack createIcon() {
        return ModItems.WAND_INFINITY == null ? ItemStack.EMPTY : new ItemStack(ModItems.WAND_INFINITY);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void displayAllRelevantItems(NonNullList<ItemStack> items) {
        // Wands, weakest to strongest.
        add(items, ModItems.WAND_STONE);
        add(items, ModItems.WAND_IRON);
        add(items, ModItems.WAND_DIAMOND);
        add(items, ModItems.WAND_INFINITY);

        // Behaviour cores.
        add(items, ModItems.CORE_ANGEL);
        add(items, ModItems.CORE_DESTRUCTION);
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_CORE_DIGGING);
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_CORE_SLAY);

        // Material cores.
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_CORE_STORAGE);
        add(items, ModItems.CORE_PROJECTE);
        add(items, ModItems.CORE_AE);

        // Upgrades.
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_UPGRADE_FORTUNE);
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_UPGRADE_AUTO_SMELT);
        add(items, com.xinyihl.morewandcore.item.ModItems.ITEM_UPGRADE_FLIGHT);
    }

    private static void add(NonNullList<ItemStack> items, Item item) {
        if (item != null) {
            items.add(new ItemStack(item));
        }
    }
}
