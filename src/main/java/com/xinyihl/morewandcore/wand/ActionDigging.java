package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.action.PlaneTraversal;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.morewandcore.basics.ExtraWandOption;
import com.xinyihl.morewandcore.basics.DropDestination;
import com.xinyihl.morewandcore.basics.FluidRemoval;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.util.Collections;
import java.util.List;

/**
 * Digs the plane facing the player, keeping the vanilla drops.
 * <p>
 * The traversal mirrors the destruction core, the difference is the operation type and that the
 * drops are collected and routed to the player inventory, the bound container or the ground.
 */
public final class ActionDigging implements WandAction {
    public static final ActionDigging INSTANCE = new ActionDigging();

    private ActionDigging() {
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

        ItemStack wand = context.getWand();
        boolean silkTouch = ExtraWandOption.isSilkTouch(wand);
        DropDestination destination = ExtraWandOption.getDestination(wand);
        // Captured now so an undo still targets the store the items actually went into.
        IWandCore materialCore = context.getState().getSelectedMaterialCore();
        FluidRemoval fluidMode = ExtraWandOption.getFluidRemoval(wand);
        // With fluid storage on, fluid is only dug when it can be stored: a source the store has no room
        // for - including a wand whose material core holds no fluid at all - stays in the world.
        FluidBudget budget = fluidMode != FluidRemoval.OFF && ExtraWandOption.storesFluid(wand)
                ? new FluidBudget(context, materialCore, wand)
                : null;

        EnumFacing breakFace = hit.sideHit;
        BlockPos startingPoint = hit.getBlockPos();
        IBlockState targetBlock = context.getWorld().getBlockState(startingPoint);
        // In "on" fluids are candidates like any block, the matching mode then decides whether a
        // source and the flowing block are the same kind of block. In "smart" they are not aimed at at
        // all, they are only cleared where the removed blocks enclose them.
        boolean fluidsAsBlocks = fluidMode == FluidRemoval.ON;
        List<WandOperation> operations = PlaneTraversal.traverse(startingPoint, breakFace, context.getState().getLock(), limit, candidate -> {
            if (!WandUtil.isBlockPermeable(context.getWorld(), candidate.offset(breakFace))) {
                return null;
            }
            IBlockState candidateBlock = context.getWorld().getBlockState(candidate);
            boolean fluid = WandUtil.isFluid(candidateBlock);
            if ((fluid && !fluidsAsBlocks) || !context.matchesBlocks(targetBlock, candidateBlock)) {
                return null;
            }
            if (fluid && budget != null) {
                return budget.create(context, candidate);
            }
            return DigOperation.create(context, candidate, silkTouch, destination, materialCore, wand);
        });
        if (fluidMode != FluidRemoval.SMART) {
            return operations;
        }
        // Digging harvests, so its undo gives the blocks back without the fluid that ran into the hole -
        // unless that fluid was stored, which is what the budget then hands out operations for.
        return FluidCleanup.include(context, operations, breakFace, false, budget);
    }
}
