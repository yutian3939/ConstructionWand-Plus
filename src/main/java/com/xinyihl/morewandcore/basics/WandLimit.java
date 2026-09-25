package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/**
 * How many blocks one action of a single wand may affect.
 * <p>
 * The configured maximum of the wand's tier stays the cap: the settings screen can lower the limit of
 * a wand, never raise it beyond what the server allows. A wand without an explicit limit simply
 * follows the configuration.
 */
public final class WandLimit {
    private static final String KEY = "block_limit";

    private WandLimit() {
    }

    /**
     * @return the limit stored on the wand, or {@code 0} when it follows the configuration
     */
    public static int getStored(ItemStack wand) {
        NBTTagCompound data = data(wand);
        if (data == null || !data.hasKey(KEY, Constants.NBT.TAG_INT)) {
            return 0;
        }
        return Math.max(0, data.getInteger(KEY));
    }

    /**
     * @return the limit the wand really uses, never above {@code configuredMax}
     */
    public static int resolve(ItemStack wand, int configuredMax) {
        int max = Math.max(1, configuredMax);
        int stored = getStored(wand);
        return stored <= 0 ? max : Math.min(stored, max);
    }

    public static boolean set(ItemStack wand, int limit) {
        if (wand == null || wand.isEmpty() || limit <= 0) {
            return false;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            wand.setTagCompound(root);
        }
        NBTTagCompound data = root.hasKey(ExtraWandOption.TAG_ROOT, Constants.NBT.TAG_COMPOUND) ? root.getCompoundTag(ExtraWandOption.TAG_ROOT) : new NBTTagCompound();
        data.setInteger(KEY, limit);
        root.setTag(ExtraWandOption.TAG_ROOT, data);
        return true;
    }

    public static String getKeyTranslation() {
        return Tags.MOD_ID + ".option.block_limit";
    }

    public static String getValueTranslation() {
        return Tags.MOD_ID + ".option.block_limit.value";
    }

    public static String getDescriptionTranslation() {
        return Tags.MOD_ID + ".option.block_limit.desc";
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
}
