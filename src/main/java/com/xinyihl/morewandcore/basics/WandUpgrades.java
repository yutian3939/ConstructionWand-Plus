package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import com.xinyihl.morewandcore.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Reads the passive upgrade components off a wand.
 * <p>
 * The components stack, so the effects they describe are combined: the fortune component replaces
 * the fortune the wand would otherwise have, and the auto-smelting component runs after the drops
 * are settled, which means both work together.
 * <p>
 * Every installed component can additionally be disabled in place from the wand screen: it stays on
 * the wand but its effect is off until it is enabled again.
 */
public final class WandUpgrades {
    private static final String TAG_DISABLED = "disabled_upgrades";

    private WandUpgrades() {
    }

    /**
     * Whether the component is installed on the wand, regardless of its enabled state.
     */
    public static boolean has(ItemStack wand, Item upgrade) {
        if (wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade)) {
            return false;
        }
        return WandDataCodec.read(wand).hasUpgrade((IWandUpgrade) upgrade);
    }

    /**
     * Whether the component is installed and currently enabled.
     */
    public static boolean isEnabled(ItemStack wand, Item upgrade) {
        return has(wand, upgrade) && !isDisabled(wand, upgrade);
    }

    /**
     * Whether an installed component is currently disabled.
     */
    public static boolean isDisabled(ItemStack wand, Item upgrade) {
        if (wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade)) {
            return false;
        }
        ResourceLocation id = upgrade.getRegistryName();
        if (id == null) {
            return false;
        }
        NBTTagCompound data = data(wand);
        if (data == null) {
            return false;
        }
        NBTTagList list = data.getTagList(TAG_DISABLED, Constants.NBT.TAG_STRING);
        String key = id.toString();
        for (int index = 0; index < list.tagCount(); index++) {
            if (key.equals(list.getStringTagAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enables or disables an installed component in place. Disabling keeps the component on the wand
     * but switches its effect off.
     *
     * @return whether the wand was written
     */
    public static boolean setDisabled(ItemStack wand, Item upgrade, boolean disabled) {
        if (wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade) || !has(wand, upgrade)) {
            return false;
        }
        ResourceLocation id = upgrade.getRegistryName();
        if (id == null) {
            return false;
        }
        String key = id.toString();
        return updateData(wand, data -> {
            NBTTagList list = data.hasKey(TAG_DISABLED, Constants.NBT.TAG_LIST)
                    ? data.getTagList(TAG_DISABLED, Constants.NBT.TAG_STRING)
                    : new NBTTagList();
            NBTTagList result = new NBTTagList();
            for (int index = 0; index < list.tagCount(); index++) {
                String entry = list.getStringTagAt(index);
                if (!key.equals(entry)) {
                    result.appendTag(new NBTTagString(entry));
                }
            }
            if (disabled) {
                result.appendTag(new NBTTagString(key));
            }
            data.setTag(TAG_DISABLED, result);
        });
    }

    public static boolean hasFortune(ItemStack wand) {
        return isEnabled(wand, ModItems.ITEM_UPGRADE_FORTUNE);
    }

    public static boolean hasAutoSmelt(ItemStack wand) {
        return isEnabled(wand, ModItems.ITEM_UPGRADE_AUTO_SMELT);
    }

    public static boolean hasFlight(ItemStack wand) {
        return isEnabled(wand, ModItems.ITEM_UPGRADE_FLIGHT);
    }

    /**
     * @return the fortune level the fortune component grants, from the configuration
     */
    public static int fortuneLevel() {
        return Math.max(1, ModConfig.wandUpgrades.fortuneLevel);
    }

    @Nullable
    private static NBTTagCompound data(ItemStack wand) {
        if (wand == null || wand.isEmpty()) {
            return null;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null || !root.hasKey(ExtraWandOption.TAG_ROOT, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompoundTag(ExtraWandOption.TAG_ROOT);
    }

    private static boolean updateData(ItemStack wand, Consumer<NBTTagCompound> updater) {
        if (wand == null || wand.isEmpty() || updater == null) {
            return false;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            wand.setTagCompound(root);
        }
        NBTTagCompound data = root.hasKey(ExtraWandOption.TAG_ROOT, Constants.NBT.TAG_COMPOUND)
                ? root.getCompoundTag(ExtraWandOption.TAG_ROOT)
                : new NBTTagCompound();
        updater.accept(data);
        root.setTag(ExtraWandOption.TAG_ROOT, data);
        return true;
    }
}
