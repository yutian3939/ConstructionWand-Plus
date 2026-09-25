package com.xinyihl.constructionwandlegacy.wand.action;

import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.function.Function;

/**
 * Shared breadth-first traversal for construction and destruction planes.
 */
public final class PlaneTraversal {
    private PlaneTraversal() {
    }

    /**
     * Evaluates positions in the legacy breadth-first order. A {@code null} value rejects a position
     * and prevents traversal from expanding through it.
     */
    public static <T> List<T> traverse(BlockPos origin, EnumFacing face, WandState.Lock lock, int limit, Function<BlockPos, T> evaluator) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(evaluator, "evaluator");

        List<T> result = new ArrayList<>();
        if (limit <= 0) {
            return result;
        }

        Axes axes = axesFor(face, lock);
        if (!axes.useFirst && !axes.useSecond) {
            return result;
        }

        ArrayDeque<BlockPos> candidates = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        enqueue(candidates, visited, origin);

        while (!candidates.isEmpty() && result.size() < limit) {
            BlockPos current = candidates.removeFirst();
            T evaluated = evaluator.apply(current);
            if (evaluated == null) {
                continue;
            }

            result.add(evaluated);
            if (result.size() < limit) {
                addPlaneCandidates(candidates, visited, current, axes);
            }
        }
        return result;
    }

    private static Axes axesFor(EnumFacing face, WandState.Lock lock) {
        switch (face) {
            case DOWN:
            case UP:
                return new Axes(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, permits(lock, WandState.Lock.NORTHSOUTH), permits(lock, WandState.Lock.EASTWEST));
            case NORTH:
            case SOUTH:
                return new Axes(EnumFacing.EAST, EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN, permits(lock, WandState.Lock.HORIZONTAL), permits(lock, WandState.Lock.VERTICAL));
            case EAST:
            case WEST:
                return new Axes(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP, EnumFacing.DOWN, permits(lock, WandState.Lock.HORIZONTAL), permits(lock, WandState.Lock.VERTICAL));
            default:
                throw new IllegalArgumentException("Unsupported face: " + face);
        }
    }

    private static boolean permits(WandState.Lock actual, WandState.Lock requested) {
        return actual == WandState.Lock.NOLOCK || actual == requested;
    }

    private static void addPlaneCandidates(ArrayDeque<BlockPos> candidates, Set<BlockPos> visited, BlockPos origin, Axes axes) {
        if (axes.useFirst) {
            enqueue(candidates, visited, origin.offset(axes.firstA));
            enqueue(candidates, visited, origin.offset(axes.firstB));
        }
        if (axes.useSecond) {
            enqueue(candidates, visited, origin.offset(axes.secondA));
            enqueue(candidates, visited, origin.offset(axes.secondB));
        }
        if (axes.useFirst && axes.useSecond) {
            enqueue(candidates, visited, origin.offset(axes.firstA).offset(axes.secondA));
            enqueue(candidates, visited, origin.offset(axes.firstA).offset(axes.secondB));
            enqueue(candidates, visited, origin.offset(axes.firstB).offset(axes.secondA));
            enqueue(candidates, visited, origin.offset(axes.firstB).offset(axes.secondB));
        }
    }

    private static void enqueue(ArrayDeque<BlockPos> candidates, Set<BlockPos> visited, BlockPos candidate) {
        if (visited.add(candidate)) {
            candidates.offerLast(candidate);
        }
    }

    private static final class Axes {
        private final EnumFacing firstA;
        private final EnumFacing firstB;
        private final EnumFacing secondA;
        private final EnumFacing secondB;
        private final boolean useFirst;
        private final boolean useSecond;

        private Axes(EnumFacing firstA, EnumFacing firstB, EnumFacing secondA, EnumFacing secondB, boolean useFirst, boolean useSecond) {
            this.firstA = firstA;
            this.firstB = firstB;
            this.secondA = secondA;
            this.secondB = secondB;
            this.useFirst = useFirst;
            this.useSecond = useSecond;
        }
    }
}
