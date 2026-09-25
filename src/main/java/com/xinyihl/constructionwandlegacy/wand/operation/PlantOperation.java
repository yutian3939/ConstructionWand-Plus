package com.xinyihl.constructionwandlegacy.wand.operation;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

import javax.annotation.Nullable;

public final class PlantOperation implements WandOperation {
    private final BlockPos pos;
    private final ItemStack seedStack;
    private final IBlockState previewState;

    private PlantOperation(BlockPos pos, ItemStack seedStack, IBlockState previewState) {
        this.pos = pos;
        this.seedStack = seedStack.copy();
        this.seedStack.setCount(1);
        this.previewState = previewState;
    }

    @Nullable
    public static PlantOperation create(WandContext context, BlockPos cropPos, ItemStack seedStack) {
        IBlockState plantState = validate(context.getWorld(), context.getPlayer(), cropPos, seedStack);
        return plantState == null ? null : new PlantOperation(cropPos, seedStack, plantState);
    }

    private static WandOperation.RollbackResult restoreBefore(World world, BlockPos pos, BlockSnapshot snapshot, RestorationState detached, boolean alreadyReverted) {
        if (alreadyReverted) {
            WandOperation.RollbackResult detachedResult = detached.restore(world, pos);
            if (detachedResult.isRestored()) {
                return detachedResult;
            }
        }
        try {
            if (snapshot.restore(true, true)) {
                return WandOperation.RollbackResult.restored();
            }
        } catch (RuntimeException exception) {
            WandOperation.RollbackResult fallback = detached.restore(world, pos);
            return fallback.isRestored() ? fallback : WandOperation.RollbackResult.failed("snapshot restoration threw", exception);
        }
        return detached.restore(world, pos);
    }

    @Nullable
    private static IBlockState validate(World world, EntityPlayer player, BlockPos cropPos, ItemStack seedStack) {
        if (seedStack.isEmpty() || !(seedStack.getItem() instanceof IPlantable) || !world.isAirBlock(cropPos) || !world.isBlockModifiable(player, cropPos) || !player.canPlayerEdit(cropPos, EnumFacing.UP, seedStack)) {
            return null;
        }
        IPlantable plantable = (IPlantable) seedStack.getItem();
        BlockPos farmlandPos = cropPos.down();
        IBlockState farmland = world.getBlockState(farmlandPos);
        if (!farmland.getBlock().canSustainPlant(farmland, world, farmlandPos, EnumFacing.UP, plantable)) {
            return null;
        }
        return plantable.getPlant(world, cropPos);
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public IBlockState getPreviewState() {
        return previewState;
    }

    @Override
    public ApplyResult apply(WandContext context, WandPlan.ExecutionToken token) {
        if (token == null || !token.isActive() || context.getWorld().isRemote) {
            return ApplyResult.rejected("operation execution is not authorized on this world");
        }
        World world = context.getWorld();
        RestorationState before = RestorationState.capture(world, pos);
        BlockSnapshot beforeSnapshot = BlockSnapshot.getBlockSnapshot(world, pos);
        try {
            IBlockState plantState = validate(world, context.getPlayer(), pos, seedStack);
            if (plantState == null) {
                return ApplyResult.rejected("planting is no longer valid");
            }

            BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, pos);
            if (!world.setBlockState(pos, plantState, 3)) {
                WandOperation.RollbackResult rollback = restoreBefore(world, pos, beforeSnapshot, before, false);
                if (rollback.isRestored()) {
                    return ApplyResult.rejected("world rejected crop placement");
                }
                return ApplyResult.failedWithChange("crop placement rejection left a world mutation", null, new PlantChange(pos, before, world.getBlockState(pos)), rollback);
            }
            IBlockState farmland = world.getBlockState(pos.down());
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, farmland, context.getPlayer());
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                boolean reverted = world.setBlockState(pos, snapshot.getReplacedBlock(), 3);
                WandOperation.RollbackResult rollback = restoreBefore(world, pos, beforeSnapshot, before, reverted);
                if (rollback.isRestored()) {
                    return ApplyResult.rejected("crop placement event was canceled");
                }
                return ApplyResult.failedWithChange("crop event cancellation could not restore state", null, new PlantChange(pos, before, world.getBlockState(pos)), rollback);
            }

            IBlockState after = world.getBlockState(pos);
            return ApplyResult.applied(new PlantChange(pos, before, after));
        } catch (RuntimeException exception) {
            WandOperation.RollbackResult rollback = restoreBefore(world, pos, beforeSnapshot, before, false);
            if (rollback.isRestored()) {
                return ApplyResult.failed("exception while planting crop", exception);
            }
            return ApplyResult.failedWithChange("exception while planting crop", exception, new PlantChange(pos, before, world.getBlockState(pos)), rollback);
        }
    }

    private static final class PlantChange implements AppliedChange {
        private final BlockPos pos;
        private final RestorationState before;
        private final IBlockState after;

        private PlantChange(BlockPos pos, RestorationState before, IBlockState after) {
            this.pos = pos;
            this.before = before;
            this.after = after;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public RollbackResult rollback(World world) {
            IBlockState current = world.getBlockState(pos);
            if (current.equals(before.getState())) {
                return RollbackResult.restored();
            }
            if (!current.equals(after)) {
                return RollbackResult.notRestored("crop changed before rollback");
            }
            return before.restore(world, pos);
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player) {
            if (!world.isBlockModifiable(player, pos)) {
                return RollbackResult.notRestored("crop is not restorable");
            }
            IBlockState current = world.getBlockState(pos);
            if (current.equals(before.getState())) {
                return RollbackResult.alreadyRestored();
            }
            if (!current.equals(after) || !WandUtil.removeBlock(world, player, after, pos)) {
                return RollbackResult.notRestored("break event rejected crop");
            }
            return before.restore(world, pos);
        }
    }
}
