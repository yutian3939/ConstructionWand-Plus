package dev.polaris_light.constructionwand.plus.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;
import dev.polaris_light.constructionwand.plus.wand.undo.FluidSnapshot;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds the fluid that the blocks of a wand operation enclose to the planned snapshots.
 * <p>
 * The wand removes a flat layer of blocks, so the only fluid those blocks can surround is fluid
 * inside that same layer: a hole in the wall the wand is breaking out, with the removed blocks as its
 * whole rim. Fluid that is merely next to the operation - an ocean beside the dig, a pool on top of
 * the floor - is not surrounded by the removed blocks and is left alone.
 * <p>
 * Once such a pocket is found, the fluid body it belongs to is cleared as well, as long as that body
 * stays below {@link #MAX_FLUID_BODY} blocks. A pocket can be deeper than the wall is thick, and
 * leaving the rest of it behind would only let it flow into the hole again.
 */
public final class FluidCleanup
{
    /**
     * Largest fluid body that still counts as a pocket formed by the removed blocks.
     */
    private static final int MAX_FLUID_BODY = 64;

    private FluidCleanup() {
    }

    /**
     * @param restorable whether undoing the operation puts the fluid back
     * @param budget     the fluid storage of the wand, or {@code null} when the cleared fluid is not
     *                   stored at all; a source the budget has no room for is left in the world
     * @return a new snapshot list that also removes the enclosed fluid
     */
    public static List<ISnapshot> include(Level level, Player player, List<ISnapshot> snapshots, Direction breakFace,
                                          boolean restorable, @Nullable FluidBudget budget) {
        if(snapshots.isEmpty()) return snapshots;

        Set<BlockPos> removed = new HashSet<>(snapshots.size() * 2);
        for(ISnapshot snapshot : snapshots) removed.add(snapshot.getPos());

        Set<BlockPos> enclosed = enclosedFluid(level, removed, breakFace.getAxis());
        if(enclosed.isEmpty()) return snapshots;

        Set<BlockPos> targets = new LinkedHashSet<>();
        for(BlockPos seed : enclosed) {
            if(targets.contains(seed)) continue;
            List<BlockPos> body = fluidBody(level, seed);
            if(body == null) {
                // Part of something larger than a pocket; only what the blocks surround is removed.
                targets.add(seed);
                continue;
            }
            targets.addAll(body);
        }

        List<ISnapshot> result = new ArrayList<>(snapshots.size() + targets.size());
        result.addAll(snapshots);
        for(BlockPos pos : targets) {
            ISnapshot fluid = budget == null
                    ? FluidSnapshot.plain(level, player, pos, restorable)
                    : budget.create(level, pos);
            if(fluid != null) result.add(fluid);
        }
        return result;
    }

    /**
     * Finds the fluid inside the worked layer whose rim is made of the removed blocks only.
     */
    private static Set<BlockPos> enclosedFluid(Level level, Set<BlockPos> removed, Direction.Axis planeAxis) {
        Set<BlockPos> enclosed = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        for(BlockPos removedPos : removed) {
            for(Direction facing : Direction.values()) {
                if(facing.getAxis() == planeAxis) continue;
                BlockPos start = removedPos.relative(facing);
                if(removed.contains(start) || !visited.add(start) || !isFluid(level, start)) continue;

                Set<BlockPos> region = inPlaneRegion(level, start, removed, planeAxis);
                if(region == null) continue;
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
    private static Set<BlockPos> inPlaneRegion(Level level, BlockPos start, Set<BlockPos> removed, Direction.Axis planeAxis) {
        Set<BlockPos> region = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        region.add(start);
        queue.add(start);

        while(!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for(Direction facing : Direction.values()) {
                if(facing.getAxis() == planeAxis) continue;
                BlockPos neighbour = current.relative(facing);
                if(removed.contains(neighbour)) continue;

                if(isFluid(level, neighbour)) {
                    if(region.add(neighbour)) {
                        queue.add(neighbour);
                        if(region.size() > MAX_FLUID_BODY) return null;
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
     * @return every position of the fluid body the position belongs to, or {@code null} when the body
     * is too large to be a pocket
     */
    @Nullable
    private static List<BlockPos> fluidBody(Level level, BlockPos start) {
        List<BlockPos> body = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);

        while(!queue.isEmpty()) {
            BlockPos current = queue.poll();
            body.add(current);
            if(body.size() > MAX_FLUID_BODY) return null;

            for(Direction facing : Direction.values()) {
                BlockPos neighbour = current.relative(facing);
                if(seen.add(neighbour) && isFluid(level, neighbour)) queue.add(neighbour);
            }
        }
        return body;
    }

    private static boolean isFluid(Level level, BlockPos pos) {
        if(!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return PlusWandUtil.isFluid(state);
    }
}
