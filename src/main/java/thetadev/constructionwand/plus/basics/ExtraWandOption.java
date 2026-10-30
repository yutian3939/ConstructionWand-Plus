package thetadev.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.option.WandOptions;

import javax.annotation.Nullable;

/**
 * Additional per-wand settings.
 * <p>
 * The stock options live in their own closed enum, so the extra settings live in a separate root tag
 * of the wand stack ({@value #TAG_ROOT}) instead of mixing them into {@code wand_options}.
 * <p>
 * Every option is a list of named values that the configuration screen cycles through, mirroring how
 * the stock options behave and leaving room for more than two states.
 */
public enum ExtraWandOption
{
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
    FLUID_REMOVAL("fluid_removal", FluidRemovalMode.keys(), FluidRemovalMode.OFF.getId()),
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
        CompoundTag data = findData(wand);
        if(data == null || !data.contains(option.id, net.minecraft.nbt.Tag.TAG_STRING)) return option.defaultValue;

        String stored = data.getString(option.id);
        if(option.indexOf(stored) >= 0) return stored;

        String legacy = option.legacyValue(stored);
        return legacy == null ? option.defaultValue : legacy;
    }

    public static int getIndex(ItemStack wand, ExtraWandOption option) {
        return Math.max(0, option.indexOf(getValue(wand, option)));
    }

    /**
     * @return the index to show and to cycle from: the stored one, or the default when the stored
     * value cannot be used on this wand
     */
    public static int getDisplayIndex(ItemStack wand, ExtraWandOption option) {
        int index = getIndex(wand, option);
        if(option.isValueAllowed(wand, index)) return index;
        int fallback = option.indexOf(option.defaultValue);
        return Math.max(0, fallback);
    }

    /**
     * @return whether this value may be selected on the given wand. Silk touch cannot be switched on
     * while the fortune component is enabled, the two do not go together, and sending the drops to the
     * material core is only possible while the wand carries one.
     */
    public boolean isValueAllowed(ItemStack wand, int index) {
        if(this == SILK_TOUCH) return index != YES_INDEX || !PassiveUpgrades.hasFortune(wand);
        if(this != DROPS_DESTINATION) return true;
        if(DropDestination.byIndex(index) != DropDestination.MATERIAL) return true;
        return wand != null && !wand.isEmpty() && new WandOptions(wand).getMaterialCore() != null;
    }

    public static boolean setIndex(ItemStack wand, ExtraWandOption option, int index) {
        if(index < 0 || index >= option.values.length) return false;
        return write(wand, option, option.values[index]);
    }

    /**
     * @return the index the option moved to, or {@code -1} when nothing was written
     */
    public static int cycleIndex(ItemStack wand, ExtraWandOption option, boolean forward) {
        int next = Math.floorMod(getIndex(wand, option) + (forward ? 1 : -1), option.values.length);
        return setIndex(wand, option, next) ? next : -1;
    }

    /**
     * @return whether silk touch is in effect. The stored value is read through the allowed one, so a
     * wand that still carries {@code yes} from before the fortune component was enabled digs without
     * silk touch, exactly as the wand screen shows it.
     */
    public static boolean isSilkTouch(ItemStack wand) {
        return getDisplayIndex(wand, SILK_TOUCH) == YES_INDEX;
    }

    /**
     * @return whether silk touch is switched off because the fortune component is enabled. The stored
     * value is kept, so it is back in effect once the component is disabled again.
     */
    public static boolean isSilkTouchBlocked(ItemStack wand) {
        return getIndex(wand, SILK_TOUCH) == YES_INDEX && !SILK_TOUCH.isValueAllowed(wand, YES_INDEX);
    }

    public static FluidRemovalMode getFluidRemoval(ItemStack wand) {
        return FluidRemovalMode.byIndex(getIndex(wand, FLUID_REMOVAL));
    }

    public static DropDestination getDestination(ItemStack wand) {
        DropDestination destination = DropDestination.byIndex(getIndex(wand, DROPS_DESTINATION));
        // Without a material core there is nowhere to send the drops to, so the inventory is used.
        if(destination == DropDestination.MATERIAL) {
            return DROPS_DESTINATION.isValueAllowed(wand, destination.getIndex()) ? destination : DropDestination.INVENTORY;
        }
        return destination;
    }

    public static boolean storesFluid(ItemStack wand) {
        return getIndex(wand, FLUID_STORAGE) == YES_INDEX;
    }

    @Nullable
    private static CompoundTag findData(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = wand.getTag();
        if(root == null || !root.contains(TAG_ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
        return root.getCompound(TAG_ROOT);
    }

    private static boolean write(ItemStack wand, ExtraWandOption option, String value) {
        if(wand == null || wand.isEmpty()) return false;
        CompoundTag root = wand.getOrCreateTag();
        CompoundTag data = root.contains(TAG_ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND) ? root.getCompound(TAG_ROOT) : new CompoundTag();
        data.putString(option.id, value);
        root.put(TAG_ROOT, data);
        return true;
    }

    private int indexOf(String value) {
        if(value == null) return -1;
        for(int index = 0; index < values.length; index++) {
            if(values[index].equals(value)) return index;
        }
        return -1;
    }

    /**
     * Maps the two-valued settings of earlier versions onto the current ones: {@code yes} was the
     * fluid handling that is called {@code smart} now.
     */
    @Nullable
    private String legacyValue(String stored) {
        if(this != FLUID_REMOVAL) return null;
        if("no".equals(stored)) return FluidRemovalMode.OFF.getId();
        return "yes".equals(stored) ? FluidRemovalMode.SMART.getId() : null;
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

    public String getValue(int index) {
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    public String getKeyTranslation() {
        return ConstructionWand.MODID + ".option." + id;
    }

    public String getValueTranslation(int index) {
        return getKeyTranslation() + "." + getValue(index);
    }

    public String getDescTranslation(int index) {
        return getValueTranslation(index) + ".desc";
    }
}
