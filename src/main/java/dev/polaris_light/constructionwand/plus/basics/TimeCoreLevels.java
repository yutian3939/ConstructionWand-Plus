package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.items.ModItems;

import javax.annotation.Nullable;

/**
 * The acceleration the time core offers: the multipliers a block may be run at, how long one application
 * lasts, and how far each wand tier may go.
 * <p>
 * Both settings are ordinary wand settings, chosen on the wand screen and stored on the wand, so a player
 * picks the speed and the time instead of walking a ladder by clicking the same block over and over.
 */
public final class TimeCoreLevels
{
    /**
     * The multipliers a wand may be set to, in the order the screen offers them.
     */
    public static final int[] LADDER = {4, 16, 32, 128, 512, 1024};

    public static final int CAP_STONE = 16;
    public static final int CAP_IRON = 128;
    public static final int CAP_DIAMOND = 512;
    public static final int CAP_INFINITY = 1024;

    /**
     * The durations the screen offers, with the ticks each of them lasts. {@link #PERMANENT} never runs
     * out, which is the longest setting there is.
     */
    public static final String DURATION_PERMANENT = "permanent";
    private static final String[] DURATIONS = {"30s", "2m", "5m", "10m", DURATION_PERMANENT};
    private static final int[] DURATION_TICKS = {600, 2400, 6000, 12000, -1};

    private TimeCoreLevels() {
    }

    /**
     * The value names of the multiplier setting, which are the ladder written out.
     */
    public static String[] ladderKeys() {
        String[] keys = new String[LADDER.length];
        for(int index = 0; index < LADDER.length; index++) keys[index] = String.valueOf(LADDER[index]);
        return keys;
    }

    public static String[] durationKeys() {
        return DURATIONS.clone();
    }

    /**
     * @return the multiplier a value name stands for, or the first step when it is unknown
     */
    public static int rateOf(@Nullable String key) {
        if(key != null) {
            for(int index = 0; index < LADDER.length; index++) {
                if(String.valueOf(LADDER[index]).equals(key)) return LADDER[index];
            }
        }
        return LADDER[0];
    }

    /**
     * @return how many ticks a duration value lasts, or {@code -1} for {@link #DURATION_PERMANENT}
     */
    public static int durationTicksOf(@Nullable String key) {
        if(key != null) {
            for(int index = 0; index < DURATIONS.length; index++) {
                if(DURATIONS[index].equals(key)) return DURATION_TICKS[index];
            }
        }
        return DURATION_TICKS[0];
    }

    /**
     * @return the highest multiplier this wand may reach. An unknown wand is treated as the stone one, so
     * a wand added by another mod never gets more than the entry tier.
     */
    public static int cap(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return CAP_STONE;
        if(wand.getItem() == ModItems.WAND_INFINITY.get()) return CAP_INFINITY;
        if(wand.getItem() == ModItems.WAND_DIAMOND.get()) return CAP_DIAMOND;
        if(wand.getItem() == ModItems.WAND_IRON.get()) return CAP_IRON;
        return CAP_STONE;
    }

    /**
     * @return how the ladder reads in a tooltip, e.g. {@code 4x -> 16x -> ...}
     */
    public static String ladderText() {
        StringBuilder text = new StringBuilder();
        for(int value : LADDER) {
            if(text.length() > 0) text.append(" -> ");
            text.append(value).append('x');
        }
        return text.toString();
    }
}
