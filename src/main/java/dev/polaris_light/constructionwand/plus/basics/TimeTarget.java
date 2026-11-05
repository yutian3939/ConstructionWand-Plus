package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;

/**
 * The block a click is turned into, and the box of the machine it is part of.
 * <p>
 * A plain block is its own machine and answers with a box of one block. A machine built out of many blocks
 * answers with the block that has to be ticked plus the whole body, so the readout can be drawn around the
 * machine instead of around the one casing that was clicked.
 *
 * @param tick the block the extra ticks are run on
 * @param min  the lowest corner of the machine, in world coordinates
 * @param max  the highest corner of the machine, in world coordinates, inclusive
 */
public record TimeTarget(BlockPos tick, BlockPos min, BlockPos max)
{
    /**
     * @return a plain block, which is its own machine
     */
    public static TimeTarget of(BlockPos pos) {
        return new TimeTarget(pos, pos, pos);
    }

    /**
     * @param first  one corner of the machine
     * @param second the other corner, in any order
     */
    public static TimeTarget of(BlockPos tick, BlockPos first, BlockPos second) {
        return new TimeTarget(tick, mins(first, second), maxs(first, second));
    }

    /**
     * @return whether this is a machine that takes up more than one block
     */
    public boolean machine() {
        return !min.equals(max);
    }

    private static BlockPos mins(BlockPos first, BlockPos second) {
        return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos maxs(BlockPos first, BlockPos second) {
        return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }
}
