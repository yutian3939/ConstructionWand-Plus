package com.xinyihl.constructionwandlegacy.basics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The translation key of the block a wand is bound to, written down when the binding is made.
 * <p>
 * The wand screen normally looks the block up in the world, which is exact but needs the chunk to be
 * loaded. Keeping the key next to the bound position lets the screen still name the block from an
 * unloaded chunk or another dimension, and because a key is stored rather than a name, it is shown in
 * whatever language the player uses at the time.
 */
public final class BoundBlockKey {
    private BoundBlockKey() {
    }

    /**
     * @return the translation key of the block at {@code pos}, e.g. {@code tile.minecraft.chest.name},
     * or {@code null} when the position cannot be read
     */
    @Nullable
    public static String of(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        try {
            if (!world.isBlockLoaded(pos)) {
                return null;
            }
            IBlockState state = world.getBlockState(pos);
            return state.getBlock().getTranslationKey() + ".name";
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
