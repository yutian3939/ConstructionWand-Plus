package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import dev.polaris_light.constructionwand.plus.compat.immersiveengineering.ImmersiveEngineeringTimeTarget;
import dev.polaris_light.constructionwand.plus.compat.mekanism.MekanismTimeTarget;

/**
 * Turns a clicked block into the block that has to be ticked to speed it up, and into the box of the
 * machine around it.
 * <p>
 * Most blocks are their own machine and answer with themselves. A machine built out of many blocks is not:
 * only one of its blocks carries the ticker, usually one that is surrounded by the rest of the machine and
 * can never be aimed at by a player standing in front of it. Clicking such a machine therefore has to be
 * understood as clicking that one block, which is what this class works out before anything is ticked.
 * <p>
 * The answer is the block the acceleration is run on, plus the body it belongs to; the block the player
 * clicked stays the one the numbers are written on and the one a sneaking click releases, so the machine
 * can be aimed at from any side and shows its speed where it was pointed at, while its outline covers the
 * whole machine.
 */
public final class TimeTargets
{
    private TimeTargets() {
    }

    /**
     * @return what ticks on behalf of {@code pos}, which is {@code pos} itself for everything that is not a
     * machine of a mod with a reader here
     */
    public static TimeTarget of(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if(blockEntity == null) return TimeTarget.of(pos);

        TimeTarget target = compatTarget(blockEntity);
        return target == null ? TimeTarget.of(pos) : target;
    }

    private static TimeTarget compatTarget(BlockEntity blockEntity) {
        if(isLoaded("mekanism")) {
            TimeTarget target = MekanismTimeTarget.target(blockEntity);
            if(target != null) return target;
        }
        if(isLoaded("immersiveengineering")) {
            TimeTarget target = ImmersiveEngineeringTimeTarget.target(blockEntity);
            if(target != null) return target;
        }
        return null;
    }

    private static boolean isLoaded(String modId) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }
}
