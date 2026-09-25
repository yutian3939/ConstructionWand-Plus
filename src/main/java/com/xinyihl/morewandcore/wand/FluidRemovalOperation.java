package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Removes one fluid block, so a hole the wand dug does not stay flooded.
 * <p>
 * The removal is a world change like any other: it fires a break event, honours the usual
 * protection checks and is recorded in the undo history, which puts the fluid back.
 */
public final class FluidRemovalOperation implements WandOperation {
    private final BlockPos pos;
    private final IBlockState fluid;
    private final boolean restorable;

    private FluidRemovalOperation(BlockPos pos, IBlockState fluid, boolean restorable) {
        this.pos = pos;
        this.fluid = fluid;
        this.restorable = restorable;
    }

    /**
     * @param restorable whether undoing the operation puts the fluid back
     * @return the operation, or {@code null} when the position does not hold a removable fluid
     */
    @Nullable
    public static FluidRemovalOperation create(WandContext context, BlockPos pos, boolean restorable) {
        World world = context.getWorld();
        IBlockState state = world.getBlockState(pos);
        if (!WandUtil.isFluid(state)) {
            return null;
        }
        if (!world.isBlockModifiable(context.getPlayer(), pos) || !world.getWorldBorder().contains(pos)) {
            return null;
        }
        return new FluidRemovalOperation(pos, state, restorable);
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
        // Water and lava re-evaluate their flow the moment a neighbouring block disappears, so the
        // fluid can already have changed its level since planning. Any fluid left there is removed
        // and the state that was really there is the one the undo puts back.
        IBlockState current = world.getBlockState(pos);
        if (!WandUtil.isFluid(current)) {
            return ApplyResult.rejected("fluid changed after planning");
        }
        try {
            if (!WandUtil.removeBlock(world, context.getPlayer(), current, pos)) {
                return ApplyResult.rejected("break event or world mutation rejected the fluid removal");
            }
            return ApplyResult.applied(new FluidChange(pos, current, restorable));
        } catch (RuntimeException exception) {
            if (world.getBlockState(pos).equals(current)) {
                return ApplyResult.failed("exception while removing fluid", exception);
            }
            boolean restored = world.setBlockState(pos, current, 3);
            if (restored) {
                return ApplyResult.failed("exception while removing fluid", exception);
            }
            return ApplyResult.failedWithChange("exception while removing fluid", exception, new FluidChange(pos, current, restorable), RollbackResult.notRestored("world rejected fluid rollback"));
        }
    }

    private static final class FluidChange implements AppliedChange {
        private final BlockPos pos;
        private final IBlockState fluid;
        private final boolean restorable;

        private FluidChange(BlockPos pos, IBlockState fluid, boolean restorable) {
            this.pos = pos;
            this.fluid = fluid;
            this.restorable = restorable;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public RollbackResult rollback(World world) {
            // Internal recovery after a failure, not a player undo: the world has to be consistent.
            return putBack(world);
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player) {
            if (!restorable) {
                // The digging core counts the cleared fluid as part of the harvest, so its undo gives
                // the blocks back but not the fluid that ran into the hole.
                return RollbackResult.restored();
            }
            if (!world.isBlockModifiable(player, pos)) {
                return RollbackResult.notRestored("removed fluid is not restorable");
            }
            return putBack(world);
        }

        private RollbackResult putBack(World world) {
            if (world.getBlockState(pos).equals(fluid)) {
                return RollbackResult.restored();
            }
            if (!world.isAirBlock(pos)) {
                return RollbackResult.notRestored("fluid position is occupied");
            }
            return world.setBlockState(pos, fluid, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected fluid restore");
        }
    }
}
