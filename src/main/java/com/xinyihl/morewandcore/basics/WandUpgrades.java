package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import com.xinyihl.morewandcore.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Reads the passive upgrade components off a wand.
 * <p>
 * The components stack, so the effects they describe are combined: the fortune component replaces
 * the fortune the wand would otherwise have, and the auto-smelting component runs after the drops
 * are settled, which means both work together.
 */
public final class WandUpgrades {
    private WandUpgrades() {
    }

    public static boolean has(ItemStack wand, Item upgrade) {
        if (wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade)) {
            return false;
        }
        return WandDataCodec.read(wand).hasUpgrade((IWandUpgrade) upgrade);
    }

    public static boolean hasFortune(ItemStack wand) {
        return has(wand, ModItems.ITEM_UPGRADE_FORTUNE);
    }

    public static boolean hasAutoSmelt(ItemStack wand) {
        return has(wand, ModItems.ITEM_UPGRADE_AUTO_SMELT);
    }

    /**
     * @return the fortune level the fortune component grants, from the configuration
     */
    public static int fortuneLevel() {
        return Math.max(1, ModConfig.wandUpgrades.fortuneLevel);
    }
}
