package com.xinyihl.constructionwandlegacy.wand.action;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.morewandcore.basics.ExtraWandOption;
import com.xinyihl.morewandcore.basics.FluidRemoval;
import com.xinyihl.morewandcore.wand.FluidCleanup;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.util.Collections;
import java.util.List;

public final class ActionDestruction implements WandAction {
    public static final ActionDestruction INSTANCE = new ActionDestruction();

    private ActionDestruction() {
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
    public boolean removesBlocks() {
        return true;
    }

    @Override
    public List<WandOperation> plan(WandContext context, OperationResolver resolver, int limit) {
        RayTraceResult hit = context.getRayTraceResult();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) {
            return Collections.emptyList();
        }

        EnumFacing breakFace = hit.sideHit;
        BlockPos startingPoint = hit.getBlockPos();
        IBlockState targetBlock = context.getWorld().getBlockState(startingPoint);
        FluidRemoval fluidMode = ExtraWandOption.getFluidRemoval(context.getWand());
        // In "on" fluids are candidates like any block, the matching mode then decides whether a
        // source and the flowing block are the same kind of block. In "smart" they are not aimed at at
        // all, they are only cleared where the removed blocks enclose them.
        boolean fluidsAsBlocks = fluidMode == FluidRemoval.ON;
        List<WandOperation> operations = PlaneTraversal.traverse(startingPoint, breakFace, context.getState().getLock(), limit, candidate -> {
            if (!WandUtil.isBlockPermeable(context.getWorld(), candidate.offset(breakFace))) {
                return null;
            }
            IBlockState candidateBlock = context.getWorld().getBlockState(candidate);
            if (!fluidsAsBlocks && WandUtil.isFluid(candidateBlock)) {
                return null;
            }
            return context.matchesBlocks(targetBlock, candidateBlock) ? resolver.createDestruction(candidate) : null;
        });
        // Destruction only touches the world, so its undo puts the enclosed fluid back. It harvests
        // nothing, so the fluid is never stored and no budget is passed in.
        return fluidMode == FluidRemoval.SMART ? FluidCleanup.include(context, operations, breakFace, true, null) : operations;
    }
}
