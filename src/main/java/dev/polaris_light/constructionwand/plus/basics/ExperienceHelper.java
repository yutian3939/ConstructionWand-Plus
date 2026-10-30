package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.world.entity.player.Player;

/**
 * Moves experience between the world and the player.
 * <p>
 * The digging core grants the experience of the blocks it digs right to the player instead of
 * spawning orbs, and takes that experience back when the digging is undone. Without the reclaim a
 * dig followed by an undo would hand out experience for free, because the undo puts the blocks back.
 */
public final class ExperienceHelper
{
    private ExperienceHelper() {
    }

    /**
     * @return the experience the player owns right now, levels included
     */
    public static int total(Player player) {
        if(player == null) return 0;
        int total = Math.round(player.experienceProgress * experienceForLevel(player.experienceLevel));
        for(int level = 0; level < player.experienceLevel; level++) total += experienceForLevel(level);
        return total;
    }

    /**
     * Takes the amount back, as far as the player still owns it. Experience that was already spent,
     * on an enchantment for example, cannot be reclaimed and is simply left alone.
     */
    public static void reclaim(Player player, int amount) {
        if(player == null || amount <= 0) return;
        setTotal(player, total(player) - amount);
    }

    private static void setTotal(Player player, int total) {
        int remaining = Math.max(0, total);
        int level = 0;
        while(remaining >= experienceForLevel(level)) {
            remaining -= experienceForLevel(level);
            level++;
        }
        player.experienceLevel = level;
        // The progress bar is measured against the level that was just set.
        player.experienceProgress = remaining / (float) experienceForLevel(level);
        player.totalExperience = Math.max(0, total);
    }

    /**
     * Experience needed to go from {@code level} to the next one, mirroring the vanilla formula.
     */
    public static int experienceForLevel(int level) {
        if(level >= 30) return 112 + (level - 30) * 9;
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }
}
