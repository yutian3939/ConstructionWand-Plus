package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.items.ItemCoreDigging;

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
    FLUID_STORAGE("fluid_storage", new String[]{"no", "yes"}, "no"),
    /**
     * Time core: how much faster the block runs. The values are the ladder of {@link TimeCoreLevels}, and
     * only the steps up to the cap of the wand tier may be picked.
     */
    TIME_MULTIPLIER("time_multiplier", TimeCoreLevels.ladderKeys(), TimeCoreLevels.ladderKeys()[0]),
    /**
     * Time core: how long one application lasts. {@code permanent} never runs out.
     */
    TIME_DURATION("time_duration", TimeCoreLevels.durationKeys(), TimeCoreLevels.durationKeys()[0]),
    /**
     * Slay core: whether every item the victim could possibly drop is handed out once, on top of the
     * normal roll.
     */
    SLAY_ALL_LOOT("slay_all_loot", new String[]{"no", "yes"}, "no");

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
     * @return whether this value may be selected on the given wand. Silk touch and the fortune component
     * clash only while the digging core is the one in use, and sending the drops to the material core is
     * only possible while the wand carries one.
     */
    public boolean isValueAllowed(ItemStack wand, int index) {
        if(this == TIME_MULTIPLIER) {
            // Only the steps this wand tier reaches are on offer.
            int[] ladder = TimeCoreLevels.LADDER;
            if(index < 0 || index >= ladder.length) return false;
            return ladder[index] <= TimeCoreLevels.cap(wand);
        }
        if(this == TIME_DURATION) return true;
        if(this == SLAY_ALL_LOOT) {
            // The extra loot is a slay core affair, a digging wand has nothing to guarantee.
            return index != YES_INDEX || !hasDiggingCore(wand);
        }
        if(this == SILK_TOUCH) {
            if(index != YES_INDEX) return true;
            if(!PassiveUpgrades.hasFortune(wand)) return true;
            // Digging would either hand out the block itself (silk touch) or the multiplied drops
            // (fortune), so the two cannot both be in effect there. The slay core uses silk touch for the
            // bonus spawn egg and the fortune component as looting, so on that core they work together.
            return !hasDiggingCore(wand);
        }
        if(this != DROPS_DESTINATION) return true;
        if(DropDestination.byIndex(index) != DropDestination.MATERIAL) return true;
        return wand != null && !wand.isEmpty() && new WandOptions(wand).getMaterialCore() != null;
    }

    /**
     * @return whether the digging core is the behaviour core this wand uses
     */
    private static boolean hasDiggingCore(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return false;
        return new WandOptions(wand).cores.get() instanceof ItemCoreDigging;
    }

    /**
     * @return the value name at an index of this option, which is the value the screen shows as well.
     * Reading a value through its index is what keeps a wand that cannot reach a stored value (a lower
     * tier wand holding 1024x) from using it anyway.
     */
    public static String getValueAt(ExtraWandOption option, int index) {
        if(index < 0 || index >= option.values.length) return option.defaultValue;
        return option.values[index];
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
     * wand that still carries {@code yes} while the digging core and the fortune component are both in
     * use digs without silk touch, exactly as the wand screen shows it.
     */
    public static boolean isSilkTouch(ItemStack wand) {
        return getDisplayIndex(wand, SILK_TOUCH) == YES_INDEX;
    }

    /**
     * @return whether silk touch cannot be used on this wand at all, which is the case while the fortune
     * component is enabled and the wand digs. It is read from the settings rather than from the value the
     * wand happens to carry: the row is just as unusable when it is set to no as when it is set to yes, and
     * the screen greys it out either way. The stored value is kept, so the setting is back in effect once
     * the component is disabled again or the slay core is selected.
     */
    public static boolean isSilkTouchDisabled(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return false;
        return !SILK_TOUCH.isValueAllowed(wand, YES_INDEX);
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
        return WandData.read(wand);
    }

    private static boolean write(ItemStack wand, ExtraWandOption option, String value) {
        return WandData.update(wand, data -> data.putString(option.id, value));
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
