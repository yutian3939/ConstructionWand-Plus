package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.morewandcore.basics.DropDelivery;
import com.xinyihl.morewandcore.basics.DropDestination;
import com.xinyihl.morewandcore.basics.DropReclaim;
import com.xinyihl.morewandcore.basics.ExperienceHelper;
import com.xinyihl.morewandcore.basics.ExperienceIntake;
import com.xinyihl.morewandcore.basics.SilkTouchDrops;
import com.xinyihl.morewandcore.basics.UndoFeedback;
import com.xinyihl.morewandcore.basics.WandUpgrades;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Breaks a single block and hands out its drops.
 * <p>
 * Unlike the destruction core this operation keeps the vanilla drop table, optionally replaced by
 * the silk touch drop, and routes the result through the wand's material core or the player
 * inventory, following the wand settings. The experience the block is worth goes straight to the
 * player, and is taken back when the digging is undone.
 */
public final class DigOperation implements WandOperation {
    private final BlockPos pos;
    private final IBlockState block;
    private final boolean silkTouch;
    private final DropDestination destination;
    @Nullable
    private final IWandCore materialCore;
    @Nullable
    private final ItemStack wand;

    private DigOperation(BlockPos pos, IBlockState block, boolean silkTouch, DropDestination destination, @Nullable IWandCore materialCore, @Nullable ItemStack wand) {
        this.pos = pos;
        this.block = block;
        this.silkTouch = silkTouch;
        this.destination = destination;
        this.materialCore = materialCore;
        this.wand = wand;
    }

    @Nullable
    public static DigOperation create(WandContext context, BlockPos pos, boolean silkTouch, DropDestination destination, @Nullable IWandCore materialCore, @Nullable ItemStack wand) {
        World world = context.getWorld();
        if (!WandUtil.isBlockRemovable(world, context.getPlayer(), pos)) {
            return null;
        }
        // Unlike the destruction core, which removes whatever it is pointed at, the digging core
        // refuses blocks that no player tool can mine: a bedrock (or barrier) item would be
        // placeable and could never be removed again. The configuration can lift this so bedrock
        // can be dug.
        if (WandUtil.isBlockUnbreakable(world, pos) && !ModConfig.digging.breakUnbreakable) {
            return null;
        }
        return new DigOperation(pos, world.getBlockState(pos), silkTouch, destination, materialCore, wand);
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public IBlockState getPreviewState() {
        return block;
    }

    @Override
    public ApplyResult apply(WandContext context, WandPlan.ExecutionToken token) {
        if (token == null || !token.isActive() || context.getWorld().isRemote) {
            return ApplyResult.rejected("operation execution is not authorized on this world");
        }
        World world = context.getWorld();
        if (!WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
            return ApplyResult.rejected("target block changed after planning");
        }
        EntityPlayer player = context.getPlayer();
        try {
            boolean grantsExperience = grantsExperience(world, player);
            int fortune = fortuneOf(player);
            boolean autoSmelt = WandUpgrades.hasAutoSmelt(wand);
            // Without smelting the block itself rolls its fortune, with smelting the furnace result is
            // what gets multiplied, so a fortune wand turns one iron ore into several ingots.
            List<ItemStack> drops = collectDrops(world, player, autoSmelt ? 0 : fortune);
            int experience = grantsExperience ? collectExperience(world, player, fortune) : 0;

            // Modded blocks report their experience by spawning orbs from their break hooks, those are
            // caught here and added to what the player receives. The window spans every hook the wand
            // runs for this block, the break itself and the harvest event.
            ExperienceIntake.begin(player);
            int absorbed = 0;
            boolean removed;
            try {
                removed = WandUtil.removeBlock(world, player, block, pos);
                if (removed) {
                    ForgeEventFactory.fireBlockHarvesting(drops, world, pos, block, 0, 1.0F, silkTouch, player);
                    if (autoSmelt) {
                        experience += smelt(drops);
                        applyFortune(world, drops, fortune);
                    }
                }
            } finally {
                absorbed = ExperienceIntake.end();
            }
            if (grantsExperience) {
                experience += absorbed;
            }
            if (!removed) {
                if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                    return ApplyResult.rejected("break event or world mutation rejected the operation");
                }
                RollbackResult rollback = world.setBlockState(pos, block, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected dig rollback");
                if (rollback.isRestored()) {
                    return ApplyResult.rejected("break event or world mutation rejected the operation");
                }
                return ApplyResult.failedWithChange("break rejection left a world mutation", null, new DigChange(pos, block, Collections.emptyList(), destination, materialCore, wand, 0), rollback);
            }

            List<ItemStack> delivered = new ArrayList<>(drops.size());
            for (ItemStack drop : drops) {
                DropDelivery.deliver(world, player, pos, drop, destination, materialCore, wand, delivered);
            }
            if (experience > 0) {
                player.addExperience(experience);
            }
            return ApplyResult.applied(new DigChange(pos, block, delivered, destination, materialCore, wand, experience));
        } catch (RuntimeException exception) {
            boolean restored = world.setBlockState(pos, block, 3);
            if (restored) {
                return ApplyResult.failed("exception while digging block", exception);
            }
            return ApplyResult.failedWithChange("exception while digging block", exception, new DigChange(pos, block, Collections.emptyList(), destination, materialCore, wand, 0), RollbackResult.notRestored("world rejected dig rollback"));
        }
    }

    /**
     * Follows the vanilla rule for experience: creative players and a disabled {@code doTileDrops}
     * game rule get nothing.
     */
    private static boolean grantsExperience(World world, EntityPlayer player) {
        return world.getGameRules().getBoolean("doTileDrops") && !player.isCreative();
    }

    /**
     * Experience the block reports through the vanilla hook, which modded blocks may simply not use.
     */
    private int collectExperience(World world, EntityPlayer player, int fortune) {
        if (!grantsExperience(world, player)) {
            return 0;
        }
        return Math.max(0, block.getBlock().getExpDrop(block, world, pos, fortune));
    }

    private List<ItemStack> collectDrops(World world, EntityPlayer player, int fortune) {
        List<ItemStack> drops = new ArrayList<>();
        if (!world.getGameRules().getBoolean("doTileDrops")) {
            return drops;
        }
        Block blockObject = block.getBlock();
        if (WandUtil.isBlockUnbreakable(world, pos)) {
            // Unbreakable blocks have no drop table; hand out the block itself when configured to,
            // so a dug bedrock is not simply lost.
            if (ModConfig.digging.dropUnbreakable) {
                ItemStack drop = SilkTouchDrops.drop(blockObject, block);
                if (!drop.isEmpty()) {
                    drops.add(drop);
                }
            }
            return drops;
        }
        if (silkTouch) {
            // Silk touch keeps the block itself, so fortune does not apply, exactly like vanilla.
            if (!blockObject.canSilkHarvest(world, pos, block, player)) {
                return drops;
            }
            ItemStack silkDrop = SilkTouchDrops.drop(blockObject, block);
            if (!silkDrop.isEmpty()) {
                drops.add(silkDrop);
            }
            return drops;
        }
        for (ItemStack drop : blockObject.getDrops(world, pos, block, fortune)) {
            if (drop != null && !drop.isEmpty()) {
                drops.add(drop.copy());
            }
        }
        return drops;
    }

    /**
     * The fortune level the drops and the experience are rolled with: the enchantment the wand
     * carries, or the configured level of the fortune component when that is higher.
     */
    private int fortuneOf(EntityPlayer player) {
        ItemStack tool = wand == null || wand.isEmpty() ? player.getHeldItemMainhand() : wand;
        int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool);
        if (WandUpgrades.hasFortune(wand)) {
            fortune = Math.max(fortune, WandUpgrades.fortuneLevel());
        }
        return Math.max(0, fortune);
    }

    /**
     * Rolls the vanilla ore bonus over the already smelted drops, which is what makes a fortune and
     * auto-smelting wand hand out several iron ingots for one iron ore: the ore block drops itself
     * and therefore never gets a fortune bonus of its own. Mirrors the formula {@code BlockOre} uses.
     */
    private static void applyFortune(World world, List<ItemStack> drops, int fortune) {
        if (fortune <= 0) {
            return;
        }
        for (int index = 0; index < drops.size(); index++) {
            ItemStack drop = drops.get(index);
            int bonus = world.rand.nextInt(fortune + 2) - 1;
            if (bonus <= 0) {
                continue;
            }
            ItemStack multiplied = drop.copy();
            multiplied.setCount(drop.getCount() * (bonus + 1));
            drops.set(index, multiplied);
        }
    }

    /**
     * Replaces the drops with their furnace results, the way an auto-smelting wand should.
     * <p>
     * The experience is looked up with the smelted stack, which is what the furnace does as well:
     * the recipe table is keyed by the result, not by the ore that goes in. Values below zero are the
     * "not specified" marker of the smelting API and count as none.
     *
     * @return the smelting experience the results are worth
     */
    private static int smelt(List<ItemStack> drops) {
        FurnaceRecipes recipes = FurnaceRecipes.instance();
        int experience = 0;
        for (int index = 0; index < drops.size(); index++) {
            ItemStack drop = drops.get(index);
            ItemStack result = recipes.getSmeltingResult(drop);
            if (result.isEmpty()) {
                continue;
            }
            ItemStack smelted = result.copy();
            smelted.setCount(result.getCount() * drop.getCount());
            float perItem = recipes.getSmeltingExperience(result);
            if (perItem > 0.0F) {
                experience += Math.round(perItem * smelted.getCount());
            }
            drops.set(index, smelted);
        }
        return experience;
    }

    /**
     * Clears a fluid that flowed into a dug position after the block was removed, so the undo can put
     * the block back instead of being refused. Anything solid still blocks the undo.
     *
     * @return whether the position is now free for the block to be placed
     */
    private static boolean clearIntrudingFluid(World world, BlockPos pos) {
        IBlockState occupying = world.getBlockState(pos);
        if (!WandUtil.isFluid(occupying)) {
            return false;
        }
        return world.setBlockToAir(pos);
    }

    private static final class DigChange implements AppliedChange {
        private final BlockPos pos;
        private final IBlockState block;
        private final List<ItemStack> delivered;
        private final DropDestination destination;
        @Nullable
        private final IWandCore materialCore;
        @Nullable
        private final ItemStack wand;
        private final int experience;
        private boolean dropsReclaimed;

        private DigChange(BlockPos pos, IBlockState block, List<ItemStack> delivered, DropDestination destination, @Nullable IWandCore materialCore, @Nullable ItemStack wand, int experience) {
            this.pos = pos;
            this.block = block;
            this.delivered = Collections.unmodifiableList(new ArrayList<>(delivered));
            this.destination = destination;
            this.materialCore = materialCore;
            this.wand = wand;
            this.experience = Math.max(0, experience);
        }

        /**
         * Takes the delivered items back, all or nothing. Nothing is removed when the items are not
         * available, so the undo can be refused and retried after they found their way back. The
         * flag keeps the removal idempotent so a retried recovery cannot consume the same items
         * twice.
         *
         * @return whether the items were taken back
         */
        private boolean takeDrops(@Nullable EntityPlayer player) {
            if (dropsReclaimed) {
                return true;
            }
            if (!delivered.isEmpty()) {
                ItemStack missing = DropReclaim.reclaim(player, materialCore, wand, destination, delivered);
                if (missing != null) {
                    return false;
                }
            }
            dropsReclaimed = true;
            // The experience was handed out together with the drops, so undoing takes it back too.
            ExperienceHelper.reclaim(player, experience);
            return true;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public RollbackResult rollback(World world) {
            // This path has no player at hand, only the material core can be used to take the items
            // back. It is reached when an already applied operation fails afterwards.
            if (!delivered.isEmpty() && !dropsReclaimed) {
                ItemStack missing = DropReclaim.reclaim(null, materialCore, wand, destination, delivered);
                dropsReclaimed = missing == null;
                if (missing != null && ConstructionWandLegacy.LOGGER != null) {
                    ConstructionWandLegacy.LOGGER.warn("Reverting a digging operation at {} without a player, {} x {} stays with the player", pos, missing.getCount(), missing.getDisplayName());
                }
            }
            if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                return RollbackResult.restored();
            }
            if (!world.isAirBlock(pos) && !clearIntrudingFluid(world, pos)) {
                return RollbackResult.notRestored("dug position is occupied");
            }
            return world.setBlockState(pos, block, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected dig rollback");
        }

        @Override
        public boolean canRestore(EntityPlayer player) {
            return DropReclaim.missingReclaim(player, materialCore, wand, delivered) == null;
        }

        @Override
        public void reportMissing(EntityPlayer player) {
            ItemStack missing = DropReclaim.missingReclaim(player, materialCore, wand, delivered);
            if (missing != null) {
                UndoFeedback.report(player, missing);
            }
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player, boolean force) {
            if (!takeDrops(player)) {
                if (!force) {
                    // Refuse the whole undo: the blocks stay broken and the history entry is kept, so
                    // the player can retry once the items are available again. Every change of this
                    // attempt reports here, UndoFeedback merges them into one chat line.
                    reportMissing(player);
                    return RollbackResult.notRestored("not enough items to undo the digging operation");
                }
                // Forced: the harvested drops are not available, so this block is simply not put
                // back. Putting it back without the drops would duplicate what the player already
                // used up.
                return RollbackResult.restored();
            }
            if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                return RollbackResult.alreadyRestored();
            }
            if (!world.isBlockModifiable(player, pos)) {
                return RollbackResult.notRestored("dug block is not restorable");
            }
            if (!world.isAirBlock(pos) && !clearIntrudingFluid(world, pos)) {
                return RollbackResult.notRestored("dug position is occupied");
            }
            return WandUtil.placeBlock(world, player, block, pos) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected dig restore");
        }
    }
}
