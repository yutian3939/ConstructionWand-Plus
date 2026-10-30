package dev.polaris_light.constructionwand.plus.compat.enchantinginfuser;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import fuzs.enchantinginfuser.init.ModRegistry;
import fuzs.enchantinginfuser.world.item.enchantment.EnchantingBehavior;
import fuzs.enchantinginfuser.world.level.block.InfuserBlock;
import fuzs.enchantinginfuser.world.level.block.InfuserType;

import javax.annotation.Nullable;

/**
 * The bridge to Enchanting Infuser, and the only place of this mod that names its types.
 * <p>
 * That mod reads its enchanting power from the shelves around the infuser, and it reads them through its own
 * {@code EnchantingBehavior}, a single static the whole mod asks. The apotheosis core therefore does not write a
 * value onto the block: it remembers the rank it was given for that position and installs a behaviour that hands
 * the number out in place of the shelves, so the infuser offers exactly the rank the wand screen shows.
 * <p>
 * Nothing else of the infuser's flow has to be touched: the behaviour is asked on both sides, which is also how
 * the client keeps its buttons in step with the server.
 */
public final class InfuserBridge
{
    /** Mod id of Enchanting Infuser. */
    public static final String MODID = "enchantinginfuser";

    /** The rank an infuser shows when the wand wrote it, per position and dimension. */
    private static final Map<GlobalPos, Integer> RANKS = new HashMap<>();

    /**
     * Set while this class asks the mod a question of its own, such as whether a shelf counts: that question
     * ends up asking this behaviour again, so the guard is what keeps it from circling.
     */
    private static final ThreadLocal<Boolean> ASKING = ThreadLocal.withInitial(() -> false);

    /** The behaviour this class installed, kept so it is installed once and can be restored. */
    private static EnchantingBehavior installed;
    @Nullable
    private static EnchantingBehavior previous;

    private InfuserBridge() {
    }

    /**
     * @return whether Enchanting Infuser is installed, so this class may be used at all
     */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    /**
     * @return whether the block is one of the two infusers
     */
    public static boolean isInfuser(BlockState state) {
        return state.getBlock() == ModRegistry.INFUSER_BLOCK.value()
                || state.getBlock() == ModRegistry.ADVANCED_INFUSER_BLOCK.value();
    }

    /**
     * Writes a rank onto an infuser. The value is kept for the session, and the behaviour that hands it out is
     * installed on the way.
     */
    public static void setRank(Level level, BlockPos pos, int rank) {
        ensureInstalled();
        RANKS.put(GlobalPos.of(level.dimension(), pos.immutable()), rank);
    }

    /**
     * @return the rank written onto a position, or {@code -1} when the wand never wrote one
     */
    public static int rank(Level level, BlockPos pos) {
        Integer rank = RANKS.get(GlobalPos.of(level.dimension(), pos));
        return rank == null ? -1 : rank;
    }

    public static void clear() {
        RANKS.clear();
    }

    /**
     * Installs the behaviour after the mod replaced it, which it does whenever its config reloads. The call is
     * cheap and idempotent, so it is repeated from the tick of both sides rather than hooked onto the reload.
     */
    public static synchronized void ensureInstalled() {
        if(installed != null && EnchantingBehavior.get() == installed) return;

        previous = EnchantingBehavior.get();
        installed = new Ranked(previous);
        EnchantingBehavior.set(installed);
    }

    /**
     * @return whether the shelf position belongs to an infuser the wand wrote a rank onto
     */
    private static int rankOfShelf(Level level, BlockPos shelfPos) {
        if(RANKS.isEmpty()) return -1;

        for(Map.Entry<GlobalPos, Integer> entry : RANKS.entrySet()) {
            GlobalPos key = entry.getKey();
            if(!key.dimension().equals(level.dimension())) continue;

            BlockPos infuser = key.pos();
            BlockPos offset = shelfPos.subtract(infuser);
            if(!EnchantingTableBlock.BOOKSHELF_OFFSETS.contains(offset)) continue;
            if(!isShelfOf(level, infuser, offset)) continue;
            return entry.getValue();
        }
        return -1;
    }

    /**
     * @return how many shelves the infuser has, which is what the rank is shared between
     */
    private static int shelvesOf(Level level, BlockPos infuser) {
        int shelves = 0;
        for(BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if(isShelfOf(level, infuser, offset)) shelves++;
        }
        return shelves;
    }

    /**
     * Asks the mod whether an offset holds a proper shelf, without letting that question ask this behaviour.
     */
    private static boolean isShelfOf(Level level, BlockPos infuser, BlockPos offset) {
        if(ASKING.get()) return false;

        ASKING.set(true);
        try {
            return InfuserBlock.isValidBookShelf(level, infuser, offset);
        }
        finally {
            ASKING.set(false);
        }
    }

    /**
     * The behaviour of Enchanting Infuser, wrapped: everything is answered by the behaviour that was installed
     * before this one, except the power of a shelf that belongs to an infuser the wand wrote a rank onto.
     */
    private static final class Ranked implements EnchantingBehavior
    {
        private final EnchantingBehavior parent;

        private Ranked(EnchantingBehavior parent) {
            this.parent = parent;
        }

        @Override
        public Collection<String> getScalingNamespaces() {
            return parent.getScalingNamespaces();
        }

        @Override
        public int getEnchantmentPowerLimit(InfuserType infuserType) {
            return parent.getEnchantmentPowerLimit(infuserType);
        }

        @Override
        public float getEnchantmentPower(BlockState blockState, Level level, BlockPos blockPos) {
            float base = parent.getEnchantmentPower(blockState, level, blockPos);
            // Inside a question of our own the plain value is wanted, or the check would ask this method again.
            if(ASKING.get()) return base;

            int rank = rankOfShelf(level, blockPos);
            if(rank < 0) return base;

            // The rank is what the infuser offers, not something added on top of the shelves: it is shared
            // between the shelves the infuser has, so their sum is the rank the wand was set to.
            BlockPos infuser = infuserOf(level, blockPos);
            if(infuser == null) return base;
            int shelves = shelvesOf(level, infuser);
            return shelves <= 0 ? base : (float) rank / shelves;
        }

        @Override
        public float getEnchantmentPowerLimitScale(BlockState blockState, Level level, BlockPos blockPos) {
            float base = parent.getEnchantmentPowerLimitScale(blockState, level, blockPos);
            if(ASKING.get()) return base;

            int rank = rankOfShelf(level, blockPos);
            if(rank < 0) return base;

            // The mod turns power into levels at fifteen power each; a rank of a hundred needs that headroom.
            return Math.max(base, rank / 15.0F);
        }

        @Override
        public float getMaximumCostMultiplier() {
            return parent.getMaximumCostMultiplier();
        }

        @Override
        public int getMaxLevel(Holder<Enchantment> enchantment) {
            return parent.getMaxLevel(enchantment);
        }

        @Override
        public int getMinCost(Holder<Enchantment> enchantment, int level) {
            return parent.getMinCost(enchantment, level);
        }

        @Override
        public int getMaxCost(Holder<Enchantment> enchantment, int level) {
            return parent.getMaxCost(enchantment, level);
        }
    }

    /**
     * @return the infuser a shelf position belongs to, or {@code null} when it belongs to none
     */
    @Nullable
    private static BlockPos infuserOf(Level level, BlockPos shelfPos) {
        for(GlobalPos key : RANKS.keySet()) {
            if(!key.dimension().equals(level.dimension())) continue;
            BlockPos infuser = key.pos();
            BlockPos offset = shelfPos.subtract(infuser);
            if(!EnchantingTableBlock.BOOKSHELF_OFFSETS.contains(offset)) continue;
            if(isShelfOf(level, infuser, offset)) return infuser;
        }
        return null;
    }
}