package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.morewandcore.basics.FluidSources;
import com.xinyihl.morewandcore.basics.UndoFeedback;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Removes one fluid block and stores its source in the wand's material core.
 * <p>
 * A source block is worth one bucket, flowing fluid is worth nothing, see {@link FluidSources}. The
 * fluid is handed to the store before the block is removed, so a store that cannot take it leaves the
 * fluid where it is instead of destroying it. The undo is the way back: it takes the stored fluid out
 * again and puts the source block back, and refuses while the fluid is not in the store.
 */
public final class StoredFluidOperation implements WandOperation {
    private final BlockPos pos;
    private final IBlockState fluid;
    @Nullable
    private final IWandCore materialCore;
    @Nullable
    private final ItemStack wand;

    private StoredFluidOperation(BlockPos pos, IBlockState fluid, @Nullable IWandCore materialCore, @Nullable ItemStack wand) {
        this.pos = pos;
        this.fluid = fluid;
        this.materialCore = materialCore;
        this.wand = wand;
    }

    /**
     * @return the operation, or {@code null} when the position does not hold a removable fluid
     */
    @Nullable
    public static StoredFluidOperation create(WandContext context, BlockPos pos, @Nullable IWandCore materialCore, @Nullable ItemStack wand) {
        World world = context.getWorld();
        IBlockState state = world.getBlockState(pos);
        if (!WandUtil.isFluid(state)) {
            return null;
        }
        if (!world.isBlockModifiable(context.getPlayer(), pos) || !world.getWorldBorder().contains(pos)) {
            return null;
        }
        return new StoredFluidOperation(pos, state, materialCore, wand);
    }

    private static int capacity(@Nullable IWandCore core, @Nullable ItemStack wand, @Nullable EntityPlayer player, FluidStack fluid) {
        if (core == null || wand == null || wand.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, core.fluidCapacity(player, wand, fluid.copy()));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static int deposit(@Nullable IWandCore core, @Nullable ItemStack wand, @Nullable EntityPlayer player, FluidStack fluid, int amount) {
        if (core == null || wand == null || wand.isEmpty() || amount <= 0) {
            return 0;
        }
        try {
            int stored = core.depositFluid(player, wand, new FluidStack(fluid.getFluid(), amount));
            return Math.max(0, Math.min(amount, stored));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken back out of the store
     */
    private static int withdraw(@Nullable IWandCore core, @Nullable ItemStack wand, @Nullable EntityPlayer player, FluidStack fluid, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (core == null || wand == null || wand.isEmpty()) {
            return amount;
        }
        try {
            int remaining = core.withdrawFluid(player, wand, new FluidStack(fluid.getFluid(), amount), amount);
            return remaining < 0 || remaining > amount ? amount : remaining;
        } catch (RuntimeException exception) {
            return amount;
        }
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public IBlockState getPreviewState() {
        return fluid;
    }

    @Override
    public ApplyResult apply(WandContext context, WandPlan.ExecutionToken token) {
        if (token == null || !token.isActive() || context.getWorld().isRemote) {
            return ApplyResult.rejected("operation execution is not authorized on this world");
        }
        World world = context.getWorld();
        IBlockState current = world.getBlockState(pos);
        if (!WandUtil.isFluid(current)) {
            return ApplyResult.rejected("fluid changed after planning");
        }
        EntityPlayer player = context.getPlayer();
        // The fluid is looked at again here: water and lava change their level the moment a neighbour
        // disappears, so what was planned can already have become the flowing block.
        FluidStack source = FluidSources.sourceFluid(world, pos);
        int stored = 0;
        if (source != null) {
            if (capacity(materialCore, wand, player, source) < source.amount) {
                return ApplyResult.rejected("no room to store the fluid");
            }
            stored = deposit(materialCore, wand, player, source, source.amount);
            if (stored < source.amount) {
                // The store shrank since planning: give back what went in and leave the fluid alone.
                release(player, source, stored);
                return ApplyResult.rejected("no room to store the fluid");
            }
        }
        try {
            if (!WandUtil.removeBlock(world, player, current, pos)) {
                release(player, source, stored);
                return ApplyResult.rejected("break event or world mutation rejected the fluid removal");
            }
            return ApplyResult.applied(new StoredFluidChange(pos, current, stored > 0 ? source : null, materialCore, wand));
        } catch (RuntimeException exception) {
            if (world.getBlockState(pos).equals(current)) {
                release(player, source, stored);
                return ApplyResult.failed("exception while removing fluid", exception);
            }
            boolean restored = world.setBlockState(pos, current, 3);
            if (restored) {
                release(player, source, stored);
                return ApplyResult.failed("exception while removing fluid", exception);
            }
            return ApplyResult.failedWithChange("exception while removing fluid", exception,
                    new StoredFluidChange(pos, current, stored > 0 ? source : null, materialCore, wand),
                    RollbackResult.notRestored("world rejected fluid rollback"));
        }
    }

    /**
     * Takes fluid back out of the store without using it, for a fluid that stayed in the world.
     */
    private void release(@Nullable EntityPlayer player, @Nullable FluidStack source, int amount) {
        if (source == null || amount <= 0) {
            return;
        }
        int missing = withdraw(materialCore, wand, player, source, amount);
        if (missing > 0 && ConstructionWandLegacy.LOGGER != null) {
            ConstructionWandLegacy.LOGGER.warn("Stored {} mB of {} for {} but could not take it back", missing, source.getLocalizedName(), pos);
        }
    }

    /**
     * Clears a fluid that flowed back into the removed fluid's position after it was taken away, so
     * the undo can put the stored source block back instead of being refused.
     *
     * @return whether the position is now free for the fluid to be put back
     */
    private static boolean clearIntrudingFluid(World world, BlockPos pos) {
        IBlockState occupying = world.getBlockState(pos);
        if (!WandUtil.isFluid(occupying)) {
            return false;
        }
        return world.setBlockToAir(pos);
    }

    private static final class StoredFluidChange implements AppliedChange {
        private final BlockPos pos;
        private final IBlockState fluid;
        /**
         * What really went into the store, {@code null} for flowing fluid, which is not stored.
         */
        @Nullable
        private final FluidStack stored;
        @Nullable
        private final IWandCore materialCore;
        @Nullable
        private final ItemStack wand;
        private boolean fluidReclaimed;

        private StoredFluidChange(BlockPos pos, IBlockState fluid, @Nullable FluidStack stored, @Nullable IWandCore materialCore, @Nullable ItemStack wand) {
            this.pos = pos;
            this.fluid = fluid;
            this.stored = stored;
            this.materialCore = materialCore;
            this.wand = wand;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public RollbackResult rollback(World world) {
            // Internal recovery after a failure, not a player undo: only the store itself is available.
            if (!reclaim(null)) {
                if (ConstructionWandLegacy.LOGGER != null && stored != null) {
                    ConstructionWandLegacy.LOGGER.warn("Reverting a stored fluid removal at {} without a player, {} mB of {} stays in the store", pos, stored.amount, stored.getLocalizedName());
                }
                return RollbackResult.notRestored("stored fluid is not available");
            }
            return putBack(world);
        }

        @Override
        public boolean canRestore(EntityPlayer player) {
            return stored == null || storedFluid(player) >= stored.amount;
        }

        @Override
        public void reportMissing(EntityPlayer player) {
            if (stored != null && storedFluid(player) < stored.amount) {
                UndoFeedback.reportFluid(player, stored);
            }
        }

        private int storedFluid(@Nullable EntityPlayer player) {
            if (materialCore == null || wand == null) {
                return 0;
            }
            try {
                return Math.max(0, materialCore.countStoredFluid(player, wand, stored));
            } catch (RuntimeException exception) {
                return 0;
            }
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player, boolean force) {
            if (!reclaim(player)) {
                if (!force) {
                    // Refuse the whole undo, the history entry is kept so it can be retried once the
                    // fluid is back in the store.
                    if (player != null && stored != null) {
                        UndoFeedback.reportFluid(player, stored);
                    }
                    return RollbackResult.notRestored("stored fluid is not available");
                }
                // Forced: the stored fluid is not available, so this source block is simply not put
                // back. The missing part is skipped instead of being duplicated.
                return RollbackResult.restored();
            }
            if (stored == null) {
                // Flowing fluid is not stored and therefore not put back; its source brings it back.
                return RollbackResult.alreadyRestored();
            }
            if (!world.isBlockModifiable(player, pos)) {
                return RollbackResult.notRestored("removed fluid is not restorable");
            }
            return putBack(world);
        }

        /**
         * Takes the fluid stored for this block back out of the store, all or nothing: when the store
         * no longer holds all of it, what was taken is put back so the undo can be retried later.
         *
         * @return whether the store no longer holds the fluid
         */
        private boolean reclaim(@Nullable EntityPlayer player) {
            if (fluidReclaimed || stored == null) {
                return true;
            }
            int missing = withdraw(materialCore, wand, player, stored, stored.amount);
            if (missing <= 0) {
                fluidReclaimed = true;
                return true;
            }
            int taken = stored.amount - missing;
            if (taken > 0) {
                deposit(materialCore, wand, player, stored, taken);
            }
            return false;
        }

        private RollbackResult putBack(World world) {
            if (world.getBlockState(pos).equals(fluid)) {
                return RollbackResult.alreadyRestored();
            }
            if (!world.isAirBlock(pos) && !clearIntrudingFluid(world, pos)) {
                return RollbackResult.notRestored("fluid position is occupied");
            }
            return world.setBlockState(pos, fluid, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected fluid restore");
        }
    }
}
