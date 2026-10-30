package thetadev.constructionwand.plus.material;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The translation key of the block a wand is bound to, written down when the binding is made.
 * <p>
 * The wand screen normally looks the block up in the world, which is exact but needs the chunk to be
 * loaded. Keeping the key next to the bound position lets the screen still name the block from an
 * unloaded chunk or another dimension, and because a key is stored rather than a name, it is shown in
 * whatever language the player uses at the time.
 */
public final class BoundBlockKey
{
    private BoundBlockKey() {
    }

    /**
     * @return the translation key of the block at {@code pos}, e.g. {@code block.minecraft.chest},
     * or {@code null} when the position cannot be read
     */
    @Nullable
    public static String of(Level level, BlockPos pos) {
        if(level == null || pos == null) return null;
        try {
            if(!level.isLoaded(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if(state.isAir()) return null;
            return state.getBlock().getDescriptionId();
        }
        catch(RuntimeException exception) {
            return null;
        }
    }
}
