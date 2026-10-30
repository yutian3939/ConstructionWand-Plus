package thetadev.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import thetadev.constructionwand.api.IWandAction;

import javax.annotation.Nullable;

/**
 * Helpers the plus content needs on top of the stock {@link thetadev.constructionwand.basics.WandUtil}.
 * <p>
 * The important difference is the removal: the stock helper refuses blocks with a block entity and
 * unbreakable blocks for players that are not in creative, while the digging core has its own rule
 * (configurable bedrock) and the destruction core removes whatever it is pointed at.
 */
public final class PlusWandUtil
{
    private PlusWandUtil() {
    }

    /**
     * Whether the state is a fluid, including the modded ones that do not use a vanilla liquid.
     */
    public static boolean isFluid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    /**
     * Whether the block cannot be mined by any tool, which is how vanilla and mods flag blocks like
     * bedrock (negative hardness). The destruction core ignores this and removes anything; the
     * digging core refuses such blocks unless the configuration lifts it, because it would hand them
     * out as items that can be placed but never removed again.
     */
    public static boolean isBlockUnbreakable(Level level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0.0F;
    }

    /**
     * Whether the position is free for a block to be placed back into it: air, or a fluid that is
     * cleared first. Anything solid refuses the restore instead of overwriting it.
     */
    public static boolean clearIntrudingFluid(Level level, BlockPos pos) {
        BlockState occupying = level.getBlockState(pos);
        if(!isFluid(occupying)) return false;
        return level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Removes one block, honouring the world protection checks and firing the break event the same way
     * the stock wand does.
     *
     * @param allowUnbreakable whether blocks no tool can mine (bedrock, barrier, ...) are removed too
     * @return whether the block is gone
     */
    public static boolean breakBlock(Level level, Player player, BlockPos pos, BlockState expected, boolean allowUnbreakable) {
        BlockState current = level.getBlockState(pos);
        if(current.isAir()) return false;
        if(!matchesPlannedState(current, expected)) return false;
        if(!level.mayInteract(player, pos)) return false;

        if(!player.isCreative() && !allowUnbreakable) {
            if(isBlockUnbreakable(level, pos, current) || level.getBlockEntity(pos) != null) return false;
        }

        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, current, player);
        MinecraftForge.EVENT_BUS.post(breakEvent);
        if(breakEvent.isCanceled()) return false;

        // Not Level#removeBlock: that one puts the fluid's own block back into the position, so a
        // fluid would never actually disappear. Removing a fluid means replacing it with air.
        return level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Whether the position still holds what an operation planned. Fluids are the one exception: water
     * and lava re-evaluate their level as soon as a neighbouring block disappears, so a planned fluid
     * can legitimately be another level by the time the operation runs.
     */
    public static boolean matchesPlannedState(BlockState current, BlockState planned) {
        return current.equals(planned) || isSameFluid(current, planned);
    }

    public static boolean isSameFluid(BlockState first, BlockState second) {
        if(!isFluid(first) || !isFluid(second)) return false;
        return first.getFluidState().getType() == second.getFluidState().getType();
    }

    /**
     * @return whether the position is inside the world and may be modified by the player
     */
    public static boolean isPositionModifiable(Level level, Player player, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.mayInteract(player, pos);
    }

    /**
     * @return whether the action removes blocks, which is when a fluid can be aimed at directly
     */
    public static boolean removesBlocks(@Nullable IWandAction action) {
        return action instanceof thetadev.constructionwand.wand.action.ActionDestruction
                || action instanceof thetadev.constructionwand.plus.wand.ActionDigging;
    }

    /**
     * @return whether the wand treats fluids as ordinary blocks
     */
    public static boolean fluidRemovalOn(ItemStack wand) {
        return ExtraWandOption.getFluidRemoval(wand) == FluidRemovalMode.ON;
    }
}
