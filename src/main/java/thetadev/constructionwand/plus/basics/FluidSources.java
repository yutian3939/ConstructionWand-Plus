package thetadev.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.Nullable;

/**
 * What a fluid block in the world is worth when it is stored.
 * <p>
 * One source block counts as one bucket. Flowing fluid counts as nothing: it holds only part of a
 * bucket and flows back in from the source that feeds it, so clearing it stores nothing and undoing
 * does not put it back either.
 */
public final class FluidSources
{
    /**
     * Amount one source block is stored as.
     */
    public static final int SOURCE_AMOUNT = FluidType.BUCKET_VOLUME;

    private FluidSources() {
    }

    /**
     * @return the bucket this position holds as a source block, or {@code null} when there is no
     * fluid or the fluid is only flowing
     */
    @Nullable
    public static FluidStack sourceFluid(Level level, BlockPos pos) {
        if(level == null || pos == null) return null;
        try {
            FluidState state = level.getFluidState(pos);
            if(state.isEmpty() || !state.isSource()) return null;
            Fluid fluid = state.getType();
            return fluid == null ? null : new FluidStack(fluid, SOURCE_AMOUNT);
        } catch(RuntimeException exception) {
            return null;
        }
    }
}
