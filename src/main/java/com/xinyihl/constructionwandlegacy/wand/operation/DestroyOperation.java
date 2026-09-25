package com.xinyihl.constructionwandlegacy.wand.operation;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public final class DestroyOperation implements WandOperation {
    private final BlockPos pos;
    private final IBlockState block;

    private DestroyOperation(BlockPos pos, IBlockState block) {
        this.pos = pos;
        this.block = block;
    }

    @Nullable
    public static DestroyOperation create(WandContext context, BlockPos pos) {
        if (!WandUtil.isBlockRemovable(context.getWorld(), context.getPlayer(), pos)) {
            return null;
        }
        return new DestroyOperation(pos, context.getWorld().getBlockState(pos));
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
        try {
            if (!WandUtil.removeBlock(world, context.getPlayer(), block, pos)) {
                if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                    return ApplyResult.rejected("break event or world mutation rejected the operation");
                }
                RollbackResult rollback = world.setBlockState(pos, block, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected destruction rollback");
                if (rollback.isRestored()) {
                    return ApplyResult.rejected("break event or world mutation rejected the operation");
                }
                return ApplyResult.failedWithChange("break rejection left a world mutation", null, new DestroyChange(pos, block), rollback);
            }
            return ApplyResult.applied(new DestroyChange(pos, block));
        } catch (RuntimeException exception) {
            boolean restored = world.setBlockState(pos, block, 3);
            if (restored) {
                return ApplyResult.failed("exception while destroying block", exception);
            }
            return ApplyResult.failedWithChange("exception while destroying block", exception, new DestroyChange(pos, block), RollbackResult.notRestored("world rejected destruction rollback"));
        }
    }

    /**
     * Clears a fluid that flowed into a destroyed position after the block was removed, so the undo
     * can put the block back instead of being refused. Anything solid still blocks the undo.
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

    private static final class DestroyChange implements AppliedChange {
        private final BlockPos pos;
        private final IBlockState block;

        private DestroyChange(BlockPos pos, IBlockState block) {
            this.pos = pos;
            this.block = block;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public RollbackResult rollback(World world) {
            if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                return RollbackResult.restored();
            }
            if (!world.isAirBlock(pos) && !clearIntrudingFluid(world, pos)) {
                return RollbackResult.notRestored("destroyed position is occupied");
            }
            return world.setBlockState(pos, block, 3) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected destruction rollback");
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player, boolean force) {
            if (WandUtil.matchesPlannedState(world.getBlockState(pos), block)) {
                return RollbackResult.restored();
            }
            if (!world.isAirBlock(pos) && !clearIntrudingFluid(world, pos)) {
                return RollbackResult.notRestored("destroyed position is occupied");
            }
            return WandUtil.placeBlock(world, player, block, pos) ? RollbackResult.restored() : RollbackResult.notRestored("world rejected destruction restore");
        }
    }
}
