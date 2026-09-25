package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds the fluid that the blocks of a wand operation enclose to the planned operations.
 * <p>
 * The wand removes a flat layer of blocks, so the only fluid those blocks can surround is fluid
 * inside that same layer: a hole in the wall the wand is breaking out, with the removed blocks as
 * its whole rim. Fluid that is merely next to the operation - an ocean beside the dig, a pool on
 * top of the floor - is not surrounded by the removed blocks and is left alone.
 * <p>
 * Once such a pocket is found, the fluid body it belongs to is cleared as well, as long as that body
 * stays below {@link #MAX_FLUID_BODY} blocks. A pocket can be deeper than the wall is thick, and
 * leaving the rest of it behind would only let it flow into the hole again.
 */
public final class FluidCleanup {
    /**
     * Largest fluid body that still counts as a pocket formed by the removed blocks.
     */
    private static final int MAX_FLUID_BODY = 64;

    private FluidCleanup() {
    }

    /**
     * @param breakFace  the face of the block the wand was pointed at, which defines the layer
     * @param restorable whether undoing the operation puts the fluid back
     * @param budget     the fluid storage of the wand, or {@code null} when the cleared fluid is not
     *                   stored at all; a source the budget has no room for is left in the world
     * @return a new operation list that also removes the enclosed fluid
     */
    public static List<WandOperation> include(WandContext context, List<WandOperation> operations, EnumFacing breakFace, boolean restorable, @Nullable FluidBudget budget) {
        if (operations.isEmpty()) {
            return operations;
        }

        World world = context.getWorld();
        Set<BlockPos> removed = new HashSet<>(operations.size() * 2);
        for (WandOperation operation : operations) {
            removed.add(operation.getPos());
        }

        Set<BlockPos> enclosed = enclosedFluid(world, removed, breakFace.getAxis());
        if (enclosed.isEmpty()) {
            return operations;
        }

        Set<BlockPos> targets = new LinkedHashSet<>();
        for (BlockPos seed : enclosed) {
            if (targets.contains(seed)) {
                continue;
            }
            List<BlockPos> body = fluidBody(world, seed);
            if (body == null) {
                // Part of something larger than a pocket; only what the blocks surround is removed.
                targets.add(seed);
                continue;
            }
            targets.addAll(body);
        }

        List<WandOperation> result = new ArrayList<>(operations.size() + targets.size());
        result.addAll(operations);
        for (BlockPos pos : targets) {
            WandOperation fluid = budget == null
                    ? FluidRemovalOperation.create(context, pos, restorable)
                    : budget.create(context, pos);
            if (fluid != null) {
                result.add(fluid);
            }
        }
        return result;
    }

    /**
     * Finds the fluid inside the worked layer whose rim is made of the removed blocks only.
     */
    private static Set<BlockPos> enclosedFluid(World world, Set<BlockPos> removed, EnumFacing.Axis planeAxis) {
        Set<BlockPos> enclosed = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos removedPos : removed) {
            for (EnumFacing facing : EnumFacing.values()) {
                if (facing.getAxis() == planeAxis) {
                    continue;
                }
                BlockPos start = removedPos.offset(facing);
                if (removed.contains(start) || !visited.add(start) || !WandUtil.isFluid(world.getBlockState(start))) {
                    continue;
                }
                Set<BlockPos> region = inPlaneRegion(world, start, removed, planeAxis);
                if (region == null) {
                    continue;
                }
                enclosed.addAll(region);
                visited.addAll(region);
            }
        }
        return enclosed;
    }

    /**
     * @return every position of the fluid region inside the layer, or {@code null} as soon as the
     * region touches something that is neither fluid of the region nor a block the wand removes
     */
    @Nullable
    private static Set<BlockPos> inPlaneRegion(World world, BlockPos start, Set<BlockPos> removed, EnumFacing.Axis planeAxis) {
        Set<BlockPos> region = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        region.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (EnumFacing facing : EnumFacing.values()) {
                if (facing.getAxis() == planeAxis) {
                    continue;
                }
                BlockPos neighbour = current.offset(facing);
                if (removed.contains(neighbour)) {
                    continue;
                }
                if (WandUtil.isFluid(world.getBlockState(neighbour))) {
                    if (region.add(neighbour)) {
                        queue.add(neighbour);
                        if (region.size() > MAX_FLUID_BODY) {
                            return null;
                        }
                    }
                    continue;
                }
                // Something else closes the rim, so the removed blocks do not surround this fluid.
                return null;
            }
        }
        return region;
    }

    /**
     * @return every position of the fluid body the position belongs to, or {@code null} when the
     * body is too large to be a pocket
     */
    @Nullable
    private static List<BlockPos> fluidBody(World world, BlockPos start) {
        List<BlockPos> body = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            body.add(current);
            if (body.size() > MAX_FLUID_BODY) {
                return null;
            }
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos neighbour = current.offset(facing);
                if (seen.add(neighbour) && WandUtil.isFluid(world.getBlockState(neighbour))) {
                    queue.add(neighbour);
                }
            }
        }
        return body;
    }
}
