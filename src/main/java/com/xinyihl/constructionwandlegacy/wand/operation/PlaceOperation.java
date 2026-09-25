package com.xinyihl.constructionwandlegacy.wand.operation;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;

import javax.annotation.Nullable;
import java.util.Collection;

public final class PlaceOperation implements WandOperation {
    private final BlockPos pos;
    private final ItemStack itemStack;
    private final ItemBlock item;
    @Nullable
    private final IBlockState supportingBlock;
    private final boolean targetMode;
    private final IBlockState previewState;

    private PlaceOperation(BlockPos pos, ItemStack itemStack, ItemBlock item, @Nullable IBlockState supportingBlock, boolean targetMode, IBlockState previewState) {
        this.pos = pos;
        this.itemStack = itemStack.copy();
        this.itemStack.setCount(1);
        this.item = item;
        this.supportingBlock = supportingBlock;
        this.targetMode = targetMode;
        this.previewState = previewState;
    }

    @Nullable
    public static PlaceOperation create(WandContext context, BlockPos pos, ItemStack itemStack, @Nullable IBlockState supportingBlock) {
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof ItemBlock)) {
            return null;
        }
        ItemBlock item = (ItemBlock) itemStack.getItem();
        boolean targetMode = supportingBlock != null && context.getState().getDirection() == WandState.Direction.TARGET;
        IBlockState preview = calculateState(context, pos, itemStack, item, supportingBlock, targetMode);
        if (preview == null || !context.getPlacementRules().isPlacementAllowed(itemStack, preview)) {
            return null;
        }
        return new PlaceOperation(pos, itemStack, item, supportingBlock, targetMode, preview);
    }

    private static WandOperation.RollbackResult restoreBefore(World world, BlockPos pos, BlockSnapshot snapshot, RestorationState detached) {
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState calculateState(WandContext context, BlockPos pos, ItemStack itemStack, ItemBlock item, @Nullable IBlockState supportingBlock, boolean targetMode) {
        World world = context.getWorld();
        EntityPlayer player = context.getPlayer();
        RayTraceResult hit = context.getRayTraceResult();
        Block block = item.getBlock();
        EnumFacing facing = hit == null || hit.sideHit == null ? EnumFacing.UP : hit.sideHit;

        float hitX = 0.5F;
        float hitY = 0.5F;
        float hitZ = 0.5F;
        if (hit != null && hit.hitVec != null) {
            Vec3d relative = hit.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
            hitX = (float) relative.x;
            hitY = (float) relative.y;
            hitZ = (float) relative.z;
        }

        if (!world.mayPlace(block, pos, false, facing, player)) {
            return null;
        }

        IBlockState state = block.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, itemStack.getMetadata(), player, EnumHand.MAIN_HAND);
        if (targetMode && supportingBlock != null) {
            Collection<IProperty<?>> sourceProperties = supportingBlock.getPropertyKeys();
            for (IProperty property : state.getPropertyKeys()) {
                if (!context.getPlacementRules().isPropertyCopyAllowed(property)) {
                    continue;
                }
                IProperty<?> source = sourceProperties.stream().filter(candidate -> candidate.getName().equals(property.getName())).findFirst().orElse(null);
                if (source == null) {
                    continue;
                }
                Comparable value = supportingBlock.getValue((IProperty) source);
                if (property.getAllowedValues().contains(value)) {
                    state = state.withProperty(property, value);
                }
            }
        }

        AxisAlignedBB bounds = state.getCollisionBoundingBox(world, pos);
        return bounds == null || world.checkNoEntityCollision(bounds.offset(pos)) ? state : null;
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
            IBlockState recalculated = calculateState(context, pos, itemStack, item, supportingBlock, targetMode);
            if (recalculated == null) {
                return ApplyResult.rejected("placement is no longer valid");
            }
            if (!WandUtil.placeBlockAt(world, context.getPlayer(), pos, itemStack, recalculated, context.getRayTraceResult())) {
                WandOperation.RollbackResult rollback = restoreBefore(world, pos, beforeSnapshot, before);
                if (rollback.isRestored()) {
                    return ApplyResult.rejected("placement event or world mutation rejected the operation");
                }
                return ApplyResult.failedWithChange("placement rejection could not restore the original block", null, new PlaceChange(pos, before, world.getBlockState(pos)), rollback);
            }
            IBlockState after = world.getBlockState(pos);
            return ApplyResult.applied(new PlaceChange(pos, before, after));
        } catch (RuntimeException exception) {
            WandOperation.RollbackResult rollback = restoreBefore(world, pos, beforeSnapshot, before);
            if (rollback.isRestored()) {
                return ApplyResult.failed("exception while placing block", exception);
            }
            return ApplyResult.failedWithChange("exception while placing block", exception, new PlaceChange(pos, before, world.getBlockState(pos)), rollback);
        }
    }

    private static final class PlaceChange implements AppliedChange {
        private final BlockPos pos;
        private final RestorationState before;
        private final IBlockState after;

        private PlaceChange(BlockPos pos, RestorationState before, IBlockState after) {
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
                return RollbackResult.notRestored("placed block changed before rollback");
            }
            return before.restore(world, pos);
        }

        @Override
        public RollbackResult restore(World world, EntityPlayer player) {
            if (!world.isBlockModifiable(player, pos)) {
                return RollbackResult.notRestored("placed block is not restorable");
            }
            IBlockState current = world.getBlockState(pos);
            if (current.equals(before.getState())) {
                return RollbackResult.alreadyRestored();
            }
            if (!current.equals(after) || !WandUtil.removeBlock(world, player, after, pos)) {
                return RollbackResult.notRestored("break event rejected placed block");
            }
            return before.restore(world, pos);
        }
    }
}
