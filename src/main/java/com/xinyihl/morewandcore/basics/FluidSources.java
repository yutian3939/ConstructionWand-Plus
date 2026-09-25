package com.xinyihl.morewandcore.basics;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;

/**
 * What a fluid block in the world is worth when it is stored.
 * <p>
 * One source block counts as one bucket. Flowing fluid counts as nothing: it holds only part of a
 * bucket and flows back in from the source that feeds it, so clearing it stores nothing and undoing
 * does not put it back either.
 */
public final class FluidSources {
    /**
     * Amount one source block is stored as.
     */
    public static final int SOURCE_AMOUNT = Fluid.BUCKET_VOLUME;

    private FluidSources() {
    }

    /**
     * @return the bucket this position holds as a source block, or {@code null} when there is no fluid,
     * no source, or a fluid the fluid registry does not know
     */
    @Nullable
    public static FluidStack sourceFluid(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        try {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            Fluid fluid;
            if (block instanceof IFluidBlock) {
                IFluidBlock fluidBlock = (IFluidBlock) block;
                // Modded fluid blocks report how full they are: only a complete block is a source.
                if (fluidBlock.getFilledPercentage(world, pos) < 1.0F) {
                    return null;
                }
                fluid = fluidBlock.getFluid();
            } else if (block instanceof BlockLiquid) {
                // Vanilla water and lava keep their level in the block state, level zero is the source.
                if (state.getValue(BlockLiquid.LEVEL) != 0) {
                    return null;
                }
                fluid = FluidRegistry.lookupFluidForBlock(block);
            } else {
                return null;
            }
            return fluid == null ? null : new FluidStack(fluid, SOURCE_AMOUNT);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
