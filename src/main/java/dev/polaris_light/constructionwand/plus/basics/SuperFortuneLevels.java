package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.basics.ConfigServer;

/**
 * The factor of the super fortune upgrade.
 * <p>
 * It is a single number rather than a handful of steps: the upgrade copies drops, so every value between
 * one and the cap of the wand's tier means something. One is the bottom of the range, which is the same as
 * not multiplying at all; zero is deliberately not offered, because a factor of zero would delete the
 * drops of the block or the mob instead of leaving them alone.
 */
public final class SuperFortuneLevels
{
    /** The lowest factor the slider offers. */
    public static final int MIN = 1;

    /** The factor a wand uses when nothing was chosen. */
    public static final int DEFAULT = 2;

    /** Root tag of the setting, next to the block limit of the wand. */
    private static final String KEY = "super_fortune";

    private SuperFortuneLevels() {
    }

    /**
     * @return the highest factor this wand may use, which is the cap of its tier
     */
    public static int cap(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return DEFAULT;
        return Math.max(MIN, ConfigServer.getSuperFortuneCap(wand.getItem()));
    }

    /**
     * @return the factor the wand is set to, clamped to the range it may use
     */
    public static int get(ItemStack wand) {
        int cap = cap(wand);
        CompoundTag data = WandData.read(wand);
        int stored = data == null || !data.contains(KEY, Tag.TAG_INT) ? DEFAULT : data.getInt(KEY);
        return Math.min(cap, Math.max(MIN, stored));
    }

    public static boolean set(ItemStack wand, int factor) {
        if(wand == null || wand.isEmpty()) return false;
        int clamped = Math.min(cap(wand), Math.max(MIN, factor));
        return WandData.update(wand, data -> data.putInt(KEY, clamped));
    }

    /**
     * @return the factor to apply to the drops: one when the upgrade is not installed
     */
    public static int multiplier(ItemStack wand) {
        if(!PassiveUpgrades.hasSuperFortune(wand)) return 1;
        return get(wand);
    }
}