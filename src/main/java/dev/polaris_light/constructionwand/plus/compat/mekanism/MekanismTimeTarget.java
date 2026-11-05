package dev.polaris_light.constructionwand.plus.compat.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.polaris_light.constructionwand.plus.basics.TimeTarget;
import dev.polaris_light.constructionwand.plus.compat.SoftReflection;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds the block of a Mekanism machine that does the ticking, and the box of the machine around it, for a
 * click on any of its blocks.
 * <p>
 * Mekanism builds a machine out of several blocks in two different ways, and both of them hide the one
 * block that actually runs:
 * <ul>
 *   <li><b>A machine with a body.</b> A "large" machine (Mekanism's own digital miner, and every large
 *   machine of More Machine: the large chemical infuser, the large electrolytic separator, the large
 *   generators…) is one block entity with a shell of {@code TileEntityBoundingBlock} around it. The shell
 *   forwards interactions to the middle block and does no ticking of its own, so the middle block of a
 *   machine standing on the floor — the one that can be reached from above — is the only one that works.
 *   The shell carries the way back ({@code getMainPos()}), and walking it finds the body of the machine.</li>
 *   <li><b>A multiblock.</b> A structure of parts (industrial turbine, thermal evaporation plant, dynamic
 *   tank, induction matrix, fission and fusion reactor) stores its data in one shared
 *   {@code MultiblockData} and lets exactly one part run it, the part whose {@code IMultiblock#isMaster()}
 *   is set. Ticking any other part does nothing at all. The master is found through the structure's
 *   controller, and by walking the bounds of the formed structure when the controller is not the master
 *   (any part of a turbine may be the master, while the controller is the part that formed it). Those
 *   bounds are also the box of the machine.</li>
 * </ul>
 * The classes are reached by {@link SoftReflection}, so a Mekanism that is missing or has moved these
 * names simply leaves the click alone.
 */
public final class MekanismTimeTarget
{
    private static final String BOUNDING_BLOCK = "mekanism.common.tile.TileEntityBoundingBlock";
    private static final String MULTIBLOCK_PART = "mekanism.common.lib.multiblock.IMultiblock";
    private static final String STRUCTURE = "mekanism.common.lib.multiblock.Structure";
    private static final String MULTIBLOCK_DATA = "mekanism.common.lib.multiblock.MultiblockData";
    private static final String CUBOID = "mekanism.common.lib.math.voxel.VoxelCuboid";

    private static final Class<?> BODY_SHELL = SoftReflection.type(BOUNDING_BLOCK);
    private static final Class<?> PART = SoftReflection.type(MULTIBLOCK_PART);

    private static final Method GET_MAIN_POS = SoftReflection.method(BODY_SHELL, "getMainPos");
    private static final Method GET_STRUCTURE = SoftReflection.method(PART, "getStructure");
    private static final Method GET_CONTROLLER = SoftReflection.method(SoftReflection.type(STRUCTURE), "getController");
    private static final Method IS_MASTER = SoftReflection.method(PART, "isMaster");
    private static final Method GET_MULTIBLOCK = SoftReflection.method(PART, "getMultiblock");
    private static final Method IS_FORMED = SoftReflection.method(SoftReflection.type(MULTIBLOCK_DATA), "isFormed");
    private static final Method GET_BOUNDS = SoftReflection.method(SoftReflection.type(MULTIBLOCK_DATA), "getBounds");
    private static final Method MIN_POS = SoftReflection.method(SoftReflection.type(CUBOID), "getMinPos");
    private static final Method MAX_POS = SoftReflection.method(SoftReflection.type(CUBOID), "getMaxPos");

    /**
     * Upper bound for the walk over a formed structure, so a machine with absurd bounds cannot turn one
     * click into a long loop. A fusion reactor is a few hundred blocks.
     */
    private static final int MAX_TILES = 32768;

    /**
     * Upper bound for the walk over the shell of a machine with a body. A large machine is a few dozen
     * blocks; the limit is only there to keep a broken shell from running away.
     */
    private static final int MAX_SHELL = 4096;

    private MekanismTimeTarget() {
    }

    /**
     * @return the block that ticks the machine the given block belongs to and the box of that machine, or
     * {@code null} when this block is not part of a Mekanism machine that hides its ticking somewhere else
     */
    @Nullable
    public static TimeTarget target(BlockEntity blockEntity) {
        BlockPos main = mainOf(blockEntity);
        if(main != null) {
            // The middle of a machine with a body can be a part of a multiblock in turn, so it is looked
            // at before the answer is given.
            BlockEntity mainTile = blockEntity.getLevel() == null ? null : blockEntity.getLevel().getBlockEntity(main);
            if(mainTile == null) return null;
            BlockPos master = masterOf(mainTile);
            if(master != null) return withBounds(mainTile, master);
            return shellOf(blockEntity, main);
        }
        BlockPos master = masterOf(blockEntity);
        return master == null ? null : withBounds(blockEntity, master);
    }

    /**
     * @return the middle block of a machine that is built from a shell, or {@code null} when this block is
     * not such a shell
     */
    @Nullable
    private static BlockPos mainOf(BlockEntity blockEntity) {
        if(BODY_SHELL == null || !BODY_SHELL.isInstance(blockEntity)) return null;
        BlockPos main = SoftReflection.call(GET_MAIN_POS, blockEntity);
        // A shell that has not been told where it belongs answers with itself, which is no machine.
        return main == null || main.equals(blockEntity.getBlockPos()) ? null : main;
    }

    /**
     * @return the part that runs the multiblock, or {@code null} when this block is not a part of one
     */
    @Nullable
    private static BlockPos masterOf(BlockEntity blockEntity) {
        if(PART == null || !PART.isInstance(blockEntity)) return null;
        BlockPos controller = controllerOf(blockEntity);
        if(controller != null) return controller;
        return masterInBounds(blockEntity);
    }

    /**
     * @return the controller of the structure, but only when it is the part that runs it
     */
    @Nullable
    private static BlockPos controllerOf(BlockEntity blockEntity) {
        Object structure = SoftReflection.call(GET_STRUCTURE, blockEntity);
        Object controller = SoftReflection.call(GET_CONTROLLER, structure);
        if(!(controller instanceof BlockEntity controllerTile)) return null;
        return isMaster(controller) ? controllerTile.getBlockPos() : null;
    }

    /**
     * Walks the bounds of the formed structure and answers for the part that runs it. The bounds are the
     * machine itself, which is loaded whenever a part of it is clicked.
     */
    @Nullable
    private static BlockPos masterInBounds(BlockEntity blockEntity) {
        Object data = SoftReflection.call(GET_MULTIBLOCK, blockEntity);
        if(!Boolean.TRUE.equals(SoftReflection.call(IS_FORMED, data))) return null;

        Object bounds = SoftReflection.call(GET_BOUNDS, data);
        BlockPos min = SoftReflection.call(MIN_POS, bounds);
        BlockPos max = SoftReflection.call(MAX_POS, bounds);
        Level level = blockEntity.getLevel();
        if(min == null || max == null || level == null) return null;

        int seen = 0;
        for(BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if(++seen > MAX_TILES) return null;
            if(!level.isLoaded(pos)) continue;
            BlockEntity candidate = level.getBlockEntity(pos);
            if(candidate == null || candidate == blockEntity || !PART.isInstance(candidate)) continue;
            if(!isMaster(candidate)) continue;
            // Only the part that shares this machine's data counts: two machines standing side by side
            // are two structures, and both of them have a master.
            if(SoftReflection.call(GET_MULTIBLOCK, candidate) != data) continue;
            return candidate.getBlockPos();
        }
        return null;
    }

    /**
     * @return the master of the multiblock together with the box of the whole structure
     */
    private static TimeTarget withBounds(BlockEntity blockEntity, BlockPos master) {
        Object data = SoftReflection.call(GET_MULTIBLOCK, blockEntity);
        Object bounds = SoftReflection.call(GET_BOUNDS, data);
        BlockPos min = SoftReflection.call(MIN_POS, bounds);
        BlockPos max = SoftReflection.call(MAX_POS, bounds);
        return min == null || max == null ? TimeTarget.of(master) : TimeTarget.of(master, min, max);
    }

    /**
     * Walks the shell of a machine with a body, starting at the block that was clicked: every block that
     * answers with the same middle is part of the same machine.
     */
    private static TimeTarget shellOf(BlockEntity clicked, BlockPos main) {
        Level level = clicked.getLevel();
        if(level == null) return TimeTarget.of(main);

        BlockPos min = main;
        BlockPos max = main;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        BlockPos start = clicked.getBlockPos();
        seen.add(start);
        open.add(start);

        while(!open.isEmpty() && seen.size() <= MAX_SHELL) {
            BlockPos pos = open.poll();
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()),
                    Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()),
                    Math.max(max.getZ(), pos.getZ()));
            for(Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if(!seen.add(next)) continue;
                if(!isShellOf(level, next, main)) continue;
                open.add(next);
            }
        }
        return TimeTarget.of(main, min, max);
    }

    private static boolean isShellOf(Level level, BlockPos pos, BlockPos main) {
        if(BODY_SHELL == null) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if(blockEntity == null || !BODY_SHELL.isInstance(blockEntity)) return false;
        return main.equals(SoftReflection.call(GET_MAIN_POS, blockEntity));
    }

    private static boolean isMaster(Object part) {
        return Boolean.TRUE.equals(SoftReflection.call(IS_MASTER, part));
    }
}
