package dev.polaris_light.constructionwand.plus.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.basics.DropDestination;
import dev.polaris_light.constructionwand.plus.basics.ExtraWandOption;
import dev.polaris_light.constructionwand.plus.basics.FluidRemovalMode;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.plus.wand.undo.DigSnapshot;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * Digs the plane facing the player, keeping the vanilla drops.
 * <p>
 * The traversal mirrors the destruction core, the difference is the snapshot type and that the drops
 * are collected and routed to the player inventory, the bound container or the ground.
 */
public class ActionDigging implements IWandAction
{
    @Override
    public int getLimit(ItemStack wand) {
        return ConfigServer.getWandProperties(wand.getItem()).getLimit();
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshots(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                        WandOptions options, IWandSupplier supplier, int limit) {
        if(rayTraceResult == null || rayTraceResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return new ArrayList<>();

        Direction breakFace = rayTraceResult.getDirection();
        BlockPos startingPoint = rayTraceResult.getBlockPos();
        BlockState targetBlock = level.getBlockState(startingPoint);

        boolean silkTouch = ExtraWandOption.isSilkTouch(wand);
        boolean allowUnbreakable = ConfigServer.DIGGING_BREAK_UNBREAKABLE.get();
        DropDestination destination = ExtraWandOption.getDestination(wand);
        // Captured now so an undo still targets the store the items actually went into.
        IMaterialCore materialCore = materialCore(options);
        FluidRemovalMode fluidMode = ExtraWandOption.getFluidRemoval(wand);
        // With fluid storage on, fluid is only dug when it can be stored: a source the store has no
        // room for - including a wand whose material core holds no fluid at all - stays in the world.
        // The capacity is only known on the server, so the client plans without the limit and the
        // preview still shows the fluid; see FluidBudget.
        FluidBudget budget = fluidMode != FluidRemovalMode.OFF && ExtraWandOption.storesFluid(wand)
                ? new FluidBudget(player, materialCore, wand, !level.isClientSide) : null;
        // In "on" fluids are candidates like any block. In "smart" they are not aimed at at all, they
        // are only cleared where the removed blocks enclose them.
        boolean fluidsAsBlocks = fluidMode == FluidRemovalMode.ON;

        List<ISnapshot> snapshots = traverse(startingPoint, breakFace, options, limit, candidate -> {
            if(!WandUtil.isBlockPermeable(level, candidate.relative(breakFace))) return null;

            BlockState candidateBlock = level.getBlockState(candidate);
            boolean fluid = PlusWandUtil.isFluid(candidateBlock);
            if(fluid && !fluidsAsBlocks) return null;
            if(!options.matchBlocks(targetBlock.getBlock(), candidateBlock.getBlock())) return null;
            if(fluid && budget != null) return budget.create(level, candidate);

            return create(level, player, candidate, silkTouch, allowUnbreakable, destination, materialCore, wand);
        });

        if(fluidMode != FluidRemovalMode.SMART) return snapshots;
        // Digging harvests, so its undo gives the blocks back without the fluid that ran into the hole
        // unless that fluid was stored, which is what the budget then hands out snapshots for.
        return FluidCleanup.include(level, player, snapshots, breakFace, false, budget);
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshotsFromAir(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                               WandOptions options, IWandSupplier supplier, int limit) {
        return new ArrayList<>();
    }

    @Nullable
    private static ISnapshot create(Level level, Player player, BlockPos pos, boolean silkTouch, boolean allowUnbreakable,
                                    DropDestination destination, @Nullable IMaterialCore materialCore, ItemStack wand) {
        if(!level.isInWorldBounds(pos)) return null;
        if(!level.mayInteract(player, pos)) return null;
        if(ConfigServer.MAX_RANGE.get() > 0 && WandUtil.blockDistance(player.blockPosition(), pos) > ConfigServer.MAX_RANGE.get()) return null;

        BlockState state = level.getBlockState(pos);
        if(state.isAir()) return null;

        if(!player.isCreative()) {
            if(level.getBlockEntity(pos) != null) return null;
            // Unlike the destruction core, which removes whatever it is pointed at, the digging core
            // refuses blocks that no player tool can mine unless the configuration lifts this: a
            // bedrock item would be placeable and could never be removed again.
            if(PlusWandUtil.isBlockUnbreakable(level, pos, state) && !allowUnbreakable) return null;
        }

        return new DigSnapshot(pos, state, silkTouch, allowUnbreakable, destination, materialCore, wand);
    }

    @Nullable
    static IMaterialCore materialCore(WandOptions options) {
        return options.materialCores.get() instanceof IMaterialCore core ? core : null;
    }

    /**
     * Breadth first traversal of the plane the wand was pointed at, in the same order the stock wand
     * uses. A {@code null} value rejects a position and stops the traversal from expanding through it.
     */
    public static List<ISnapshot> traverse(BlockPos origin, Direction face, WandOptions options, int limit, Function<BlockPos, ISnapshot> evaluator) {
        List<ISnapshot> result = new ArrayList<>();
        if(limit <= 0) return result;

        Axes axes = Axes.forFace(face, options);
        if(!axes.useFirst && !axes.useSecond) return result;

        LinkedList<BlockPos> candidates = new LinkedList<>();
        HashSet<BlockPos> visited = new HashSet<>();
        candidates.add(origin);
        visited.add(origin);

        while(!candidates.isEmpty() && result.size() < limit) {
            BlockPos current = candidates.removeFirst();
            ISnapshot evaluated;
            try {
                evaluated = evaluator.apply(current);
            } catch(RuntimeException exception) {
                // Can't do anything, could be anything. Skip if anything goes wrong.
                continue;
            }
            if(evaluated == null) continue;

            result.add(evaluated);
            if(result.size() < limit) axes.expand(candidates, visited, current);
        }
        return result;
    }

    private static final class Axes
    {
        private final Direction firstA;
        private final Direction firstB;
        private final Direction secondA;
        private final Direction secondB;
        private final boolean useFirst;
        private final boolean useSecond;

        private Axes(Direction firstA, Direction firstB, Direction secondA, Direction secondB, boolean useFirst, boolean useSecond) {
            this.firstA = firstA;
            this.firstB = firstB;
            this.secondA = secondA;
            this.secondB = secondB;
            this.useFirst = useFirst;
            this.useSecond = useSecond;
        }

        static Axes forFace(Direction face, WandOptions options) {
            return switch(face) {
                case DOWN, UP -> new Axes(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
                        options.testLock(WandOptions.LOCK.NORTHSOUTH), options.testLock(WandOptions.LOCK.EASTWEST));
                case NORTH, SOUTH -> new Axes(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN,
                        options.testLock(WandOptions.LOCK.HORIZONTAL), options.testLock(WandOptions.LOCK.VERTICAL));
                case EAST, WEST -> new Axes(Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN,
                        options.testLock(WandOptions.LOCK.HORIZONTAL), options.testLock(WandOptions.LOCK.VERTICAL));
            };
        }

        void expand(LinkedList<BlockPos> candidates, HashSet<BlockPos> visited, BlockPos origin) {
            if(useFirst) {
                enqueue(candidates, visited, origin.relative(firstA));
                enqueue(candidates, visited, origin.relative(firstB));
            }
            if(useSecond) {
                enqueue(candidates, visited, origin.relative(secondA));
                enqueue(candidates, visited, origin.relative(secondB));
            }
            if(useFirst && useSecond) {
                enqueue(candidates, visited, origin.relative(firstA).relative(secondA));
                enqueue(candidates, visited, origin.relative(firstA).relative(secondB));
                enqueue(candidates, visited, origin.relative(firstB).relative(secondA));
                enqueue(candidates, visited, origin.relative(firstB).relative(secondB));
            }
        }

        private static void enqueue(LinkedList<BlockPos> candidates, HashSet<BlockPos> visited, BlockPos candidate) {
            if(visited.add(candidate)) candidates.add(candidate);
        }
    }
}
