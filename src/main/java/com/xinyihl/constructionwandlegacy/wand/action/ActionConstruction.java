package com.xinyihl.constructionwandlegacy.wand.action;

import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.util.Collections;
import java.util.List;

public final class ActionConstruction implements WandAction {
    public static final ActionConstruction INSTANCE = new ActionConstruction();

    private ActionConstruction() {
    }

    @Override
    public int getLimit(ItemStack wand) {
        return ((ItemWand) wand.getItem()).getPlacementLimit();
    }

    @Override
    public int getLimit(WandContext context) {
        return context.getPlacementLimit();
    }

    @Override
    public List<WandOperation> plan(WandContext context, OperationResolver resolver, int limit) {
        RayTraceResult hit = context.getRayTraceResult();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) {
            return Collections.emptyList();
        }

        EnumFacing placeDirection = hit.sideHit;
        IBlockState targetBlock = context.getWorld().getBlockState(hit.getBlockPos());
        BlockPos startingPoint = hit.getBlockPos().offset(placeDirection);
        return PlaneTraversal.traverse(startingPoint, placeDirection, context.getState().getLock(), limit, candidate -> {
            BlockPos supportPos = candidate.offset(placeDirection.getOpposite());
            IBlockState supportingBlock = context.getWorld().getBlockState(supportPos);
            return context.matchesBlocks(targetBlock, supportingBlock) ? resolver.createPlacement(candidate, supportingBlock) : null;
        });
    }
}
