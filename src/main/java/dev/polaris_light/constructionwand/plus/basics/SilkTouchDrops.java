package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves the silk touch drop of a block.
 * <p>
 * In 1.20.1 the equivalent of the old {@code getSilkTouchDrop} is
 * {@link net.minecraft.world.level.block.Block#getCloneItemStack}, which several vanilla blocks
 * (logs, leaves, quartz and friends) override, and which the wand cannot simply copy. The block is
 * asked for the stack directly and falls back to the block's own item when it reports nothing.
 */
public final class SilkTouchDrops
{
    private SilkTouchDrops() {
    }

    public static ItemStack drop(Level level, net.minecraft.core.BlockPos pos, BlockState state) {
        ItemStack silk = state.getBlock().getCloneItemStack(level, pos, state);
        if(silk != null && !silk.isEmpty()) return silk.copy();

        ItemStack fallback = new ItemStack(state.getBlock().asItem());
        return fallback.isEmpty() ? ItemStack.EMPTY : fallback;
    }
}
