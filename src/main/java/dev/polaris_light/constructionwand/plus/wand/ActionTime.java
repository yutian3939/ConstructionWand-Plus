package dev.polaris_light.constructionwand.plus.wand;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour of the time core. It accelerates the block it is pointed at instead of placing anything, so
 * it plans nothing: the actual work happens from the interaction handler, see
 * {@link dev.polaris_light.constructionwand.plus.basics.TimeCoreEvents}.
 * <p>
 * Planning nothing is also what keeps the wand from placing blocks while this core is selected, on every
 * path that leads through a wand job.
 */
public class ActionTime implements IWandAction
{
    @Override
    public int getLimit(ItemStack wand) {
        // One block per click, the ladder of that block carries the progression.
        return 1;
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshots(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                        WandOptions options, IWandSupplier supplier, int limit) {
        return new ArrayList<>();
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshotsFromAir(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                               WandOptions options, IWandSupplier supplier, int limit) {
        return new ArrayList<>();
    }
}
