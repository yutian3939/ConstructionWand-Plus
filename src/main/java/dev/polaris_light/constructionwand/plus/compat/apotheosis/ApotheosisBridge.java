package dev.polaris_light.constructionwand.plus.compat.apotheosis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.polaris_light.constructionwand.plus.basics.ApotheosisLevels;

import javax.annotation.Nullable;

/**
 * The only place of this mod that names Apotheosis or Apothic Enchanting types.
 * <p>
 * Like the ProjectE access class, it may only be touched while both mods are installed; the core itself only
 * exists then, which is what keeps this safe. Everything here is a thin call into their public classes: the
 * stats of a table are the record {@code EnchantmentTableStats}, and the world tier is the enum of
 * {@code WorldTier} with its own setter.
 */
public final class ApotheosisBridge
{
    /** Mod id of the world tier and gem dust mod. */
    public static final String MODID = "apotheosis";
    /** Mod id of the enchanting table and stat mod. */
    public static final String ENCHANTING_MODID = "apothic_enchanting";

    private ApotheosisBridge() {
    }

    /**
     * @return whether both mods are installed, so this class may be used at all
     */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID) && ModList.get().isLoaded(ENCHANTING_MODID);
    }

    /**
     * @return whether the block is an enchanting table of any kind. Apotheosis replaces the vanilla table with
     * its own class at runtime, and its extra table is a subclass of the vanilla one as well, so this covers
     * the vanilla table, the apothic one and the raven one in a single test.
     */
    public static boolean isTable(BlockState state) {
        return state.getBlock() instanceof EnchantingTableBlock;
    }

    /**
     * @return the number of world tiers Apotheosis offers
     */
    public static int tierCount() {
        return WorldTier.values().length;
    }

    /**
     * @return the name the player reads for a tier, or an empty string when the index is out of range
     */
    public static String tierName(int index) {
        WorldTier[] tiers = WorldTier.values();
        if(index < 0 || index >= tiers.length) return "";
        return tiers[index].getSerializedName();
    }

    /**
     * Writes the world tier of the player. Apotheosis only changes a tier from its own screen when the tier is
     * unlocked; the core is meant to hand the player the choice, so this setter is used directly, which also
     * applies and removes the tier augments of both tiers.
     */
    public static void applyTier(ServerPlayer player, int index) {
        WorldTier[] tiers = WorldTier.values();
        WorldTier tier = tiers[Math.min(tiers.length - 1, Math.max(0, index))];
        WorldTier.setTier(player, tier);
    }

    /**
     * Writes the three stats onto the enchanting table the player has open, using the values the wand is set
     * to. The values of the table's own shelves are kept as they are: only rank, quanta and arcana are
     * replaced, so the clues, the blacklist, the treasure flag and the stability of the table still come from
     * the room the table stands in.
     *
     * @param pos the table the player right clicked, which is where its shelves are read from
     * @return whether the open menu was a table of Apothic Enchanting
     */
    public static boolean applyStats(ServerPlayer player, Level level, BlockPos pos) {
        if(!(player.containerMenu instanceof ApothEnchantmentMenu menu)) return false;

        EnchantmentTableStats base = EnchantmentTableStats.gatherStats(level, pos);
        EnchantmentTableStats stats = new EnchantmentTableStats(
                ApotheosisLevels.eterna(player.getMainHandItem()),
                ApotheosisLevels.quanta(player.getMainHandItem()),
                ApotheosisLevels.arcana(player.getMainHandItem()),
                base.clues(), base.blacklist(), base.treasure(), base.stable());
        menu.setStats(stats);
        return true;
    }

    /**
     * @return whether the player currently has an enchanting table of Apothic Enchanting open
     */
    public static boolean hasTableOpen(ServerPlayer player) {
        return player.containerMenu instanceof ApothEnchantmentMenu;
    }

    /**
     * @return the level of the player, which is where the shelves of the table are read from
     */
    @Nullable
    public static ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
    }
}