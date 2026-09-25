package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/**
 * Additional per-wand settings.
 * <p>
 * Construction Wand Legacy keeps its own options in a closed enum, so the extra settings live in a
 * separate root tag of the wand stack instead of mixing them into {@code wand_options}.
 * <p>
 * Every option is a list of named values that the configuration screen cycles through, mirroring how
 * the stock options behave and leaving room for more than two states.
 */
public enum ExtraWandOption {
    /**
     * Digging core: drop the silk touch variant of the block instead of its normal drops.
     */
    SILK_TOUCH("silk_touch", new String[]{"no", "yes"}, "no"),
    /**
     * Digging core: where the collected drops are routed to.
     */
    DROPS_DESTINATION("drops_destination", DropDestination.keys(), DropDestination.INVENTORY.getId()),
    /**
     * Destruction and digging core: how fluids are treated. {@code smart} clears only the fluid the
     * removed blocks enclose, {@code on} treats fluids as ordinary blocks.
     */
    FLUID_REMOVAL("fluid_removal", FluidRemoval.keys(), FluidRemoval.OFF.getId()),
    /**
     * Digging core: whether the fluid it clears is stored in the material core instead of being
     * dropped. Only source blocks are stored, one bucket each; sources that do not fit stay in the
     * world.
     */
    FLUID_STORAGE("fluid_storage", new String[]{"no", "yes"}, "no");

    /**
     * Root NBT tag of these settings on the wand item stack.
     */
    public static final String TAG_ROOT = "wand_extra_options";

    /**
     * Index of the enabled value of every yes/no option.
     */
    private static final int YES_INDEX = 1;

    private final String id;
    private final String[] values;
    private final String defaultValue;

    ExtraWandOption(String id, String[] values, String defaultValue) {
        this.id = id;
        this.values = values;
        this.defaultValue = defaultValue;
    }

    public static String getValue(ItemStack wand, ExtraWandOption option) {
        NBTTagCompound data = findData(wand);
        if (data == null || !data.hasKey(option.id, Constants.NBT.TAG_STRING)) {
            return option.defaultValue;
        }
        String stored = data.getString(option.id);
        if (option.indexOf(stored) >= 0) {
            return stored;
        }
        String legacy = option.legacyValue(stored);
        return legacy == null ? option.defaultValue : legacy;
    }

    public static int getIndex(ItemStack wand, ExtraWandOption option) {
        return Math.max(0, option.indexOf(getValue(wand, option)));
    }

    public static boolean setIndex(ItemStack wand, ExtraWandOption option, int index) {
        if (index < 0 || index >= option.values.length) {
            return false;
        }
        return write(wand, option, option.values[index]);
    }

    /**
     * @return the index the option moved to, or {@code -1} when nothing was written
     */
    public static int cycleIndex(ItemStack wand, ExtraWandOption option, boolean forward) {
        int next = Math.floorMod(getIndex(wand, option) + (forward ? 1 : -1), option.values.length);
        return setIndex(wand, option, next) ? next : -1;
    }

    public static boolean isSilkTouch(ItemStack wand) {
        return getIndex(wand, SILK_TOUCH) == YES_INDEX;
    }

    public static FluidRemoval getFluidRemoval(ItemStack wand) {
        return FluidRemoval.byIndex(getIndex(wand, FLUID_REMOVAL));
    }

    public static DropDestination getDestination(ItemStack wand) {
        return DropDestination.byIndex(getIndex(wand, DROPS_DESTINATION));
    }

    public static boolean storesFluid(ItemStack wand) {
        return getIndex(wand, FLUID_STORAGE) == YES_INDEX;
    }

    @Nullable
    private static NBTTagCompound findData(ItemStack wand) {
        if (wand == null || wand.isEmpty()) {
            return null;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null || !root.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompoundTag(TAG_ROOT);
    }

    private static boolean write(ItemStack wand, ExtraWandOption option, String value) {
        if (wand == null || wand.isEmpty()) {
            return false;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            wand.setTagCompound(root);
        }
        NBTTagCompound data = root.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND) ? root.getCompoundTag(TAG_ROOT) : new NBTTagCompound();
        data.setString(option.id, value);
        root.setTag(TAG_ROOT, data);
        return true;
    }

    private int indexOf(String value) {
        if (value == null) {
            return -1;
        }
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Maps the two-valued settings of earlier versions onto the current ones: {@code yes} was the
     * fluid handling that is called {@code smart} now.
     */
    @Nullable
    private String legacyValue(String stored) {
        if (this != FLUID_REMOVAL) {
            return null;
        }
        if ("no".equals(stored)) {
            return FluidRemoval.OFF.getId();
        }
        return "yes".equals(stored) ? FluidRemoval.SMART.getId() : null;
    }

    public String getId() {
        return id;
    }

    public int getValueCount() {
        return values.length;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getKeyTranslation() {
        return Tags.MOD_ID + ".option." + id;
    }

    public String getValueTranslation(int index) {
        return getKeyTranslation() + "." + values[Math.max(0, Math.min(index, values.length - 1))];
    }

    public String getDescriptionTranslation(int index) {
        return getValueTranslation(index) + ".desc";
    }
}
