package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * The four numbers the apotheosis core writes onto an enchanting table.
 * <p>
 * They are plain integers stored on the wand, next to its block limit: the world tier of Apotheosis counted
 * from its first tier, and the rank (eterna), the quanta and the arcana of Apothic Enchanting. The three stats
 * run from zero to a hundred, which is the range the record of the table clamps to as well, so a value the
 * screen offers is always a value a table can hold.
 */
public final class ApotheosisLevels
{
    /** How many world tiers Apotheosis has, from {@code haven} up to {@code pinnacle}. */
    public static final int TIERS = 5;

    /** Highest value of rank, quanta and arcana. */
    public static final int STAT_MAX = 100;

    /** The tier a wand starts on, the lowest of Apotheosis. */
    public static final int DEFAULT_TIER = 0;

    /** Rank, quanta and arcana a wand starts on: a plain table, not a maxed one. */
    public static final int DEFAULT_ETERNA = 30;
    public static final int DEFAULT_QUANTA = 15;
    public static final int DEFAULT_ARCANA = 0;

    private static final String KEY_TIER = "apoth_tier";
    private static final String KEY_ETERNA = "apoth_eterna";
    private static final String KEY_QUANTA = "apoth_quanta";
    private static final String KEY_ARCANA = "apoth_arcana";

    private ApotheosisLevels() {
    }

    public static int tier(ItemStack wand) {
        return read(wand, KEY_TIER, DEFAULT_TIER, TIERS - 1);
    }

    public static int eterna(ItemStack wand) {
        return read(wand, KEY_ETERNA, DEFAULT_ETERNA, STAT_MAX);
    }

    public static int quanta(ItemStack wand) {
        return read(wand, KEY_QUANTA, DEFAULT_QUANTA, STAT_MAX);
    }

    public static int arcana(ItemStack wand) {
        return read(wand, KEY_ARCANA, DEFAULT_ARCANA, STAT_MAX);
    }

    public static void setTier(ItemStack wand, int value) {
        write(wand, KEY_TIER, value, TIERS - 1);
    }

    public static void setEterna(ItemStack wand, int value) {
        write(wand, KEY_ETERNA, value, STAT_MAX);
    }

    public static void setQuanta(ItemStack wand, int value) {
        write(wand, KEY_QUANTA, value, STAT_MAX);
    }

    public static void setArcana(ItemStack wand, int value) {
        write(wand, KEY_ARCANA, value, STAT_MAX);
    }

    /**
     * @param wand  the wand to set, which may be empty
     * @param tier  the tier to write, clamped to the tiers that exist
     * @param eterna the rank to write, clamped to the range of the stats
     * @param quanta the quanta to write, clamped to the range of the stats
     * @param arcana the arcana to write, clamped to the range of the stats
     * @return whether something changed, so the holder can be told about it
     */
    public static boolean setAll(ItemStack wand, int tier, int eterna, int quanta, int arcana) {
        if(wand == null || wand.isEmpty()) return false;
        int clampedTier = Math.min(TIERS - 1, Math.max(0, tier));
        int clampedEterna = Math.min(STAT_MAX, Math.max(0, eterna));
        int clampedQuanta = Math.min(STAT_MAX, Math.max(0, quanta));
        int clampedArcana = Math.min(STAT_MAX, Math.max(0, arcana));

        return WandData.update(wand, data -> {
            data.putInt(KEY_TIER, clampedTier);
            data.putInt(KEY_ETERNA, clampedEterna);
            data.putInt(KEY_QUANTA, clampedQuanta);
            data.putInt(KEY_ARCANA, clampedArcana);
        });
    }

    private static int read(ItemStack wand, String key, int fallback, int max) {
        if(wand == null || wand.isEmpty()) return fallback;
        CompoundTag data = WandData.read(wand);
        int stored = data == null || !data.contains(key, Tag.TAG_INT) ? fallback : data.getInt(key);
        return Math.min(max, Math.max(0, stored));
    }

    private static void write(ItemStack wand, String key, int value, int max) {
        if(wand == null || wand.isEmpty()) return;
        int clamped = Math.min(max, Math.max(0, value));
        WandData.update(wand, data -> data.putInt(key, clamped));
    }
}