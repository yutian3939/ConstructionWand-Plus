package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * What a bound container is called, written down when the binding is made.
 * <p>
 * The wand screen normally looks the block up in the world, which is exact but needs the chunk to be
 * loaded. Keeping the key and the name next to the bound position lets the screen still name the block
 * from an unloaded chunk or another dimension.
 * <p>
 * Both are kept because they answer different questions: the key follows the language the player uses at
 * the time, and the name is what the block called itself when it was bound. A key alone is not enough,
 * because a modded block may translate its name with a placeholder it fills in itself (an iron chest
 * names itself "Diamond Chest" from {@code %s Chest}), and that placeholder would otherwise be shown.
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
        return of(level, pos, null);
    }

    @Nullable
    public static String of(@Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceKey<Level> dimension) {
        if(level == null || pos == null) return null;
        if(dimension != null && !level.dimension().equals(dimension)) return null;
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

    /**
     * @return the name the block gives itself, already resolved, or {@code null} when it cannot be read
     */
    @Nullable
    public static String nameOf(Level level, BlockPos pos) {
        return nameOf(level, pos, null);
    }

    @Nullable
    public static String nameOf(@Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceKey<Level> dimension) {
        if(level == null || pos == null) return null;
        if(dimension != null && !level.dimension().equals(dimension)) return null;
        try {
            if(!level.isLoaded(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if(state.isAir()) return null;
            Component name = state.getBlock().getName();
            return name.getString();
        }
        catch(RuntimeException exception) {
            return null;
        }
    }
}
