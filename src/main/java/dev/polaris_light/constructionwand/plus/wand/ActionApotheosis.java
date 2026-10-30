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
 * Behaviour of the apotheosis core. It writes the numbers the wand screen shows onto an enchanting table
 * instead of placing anything, so it plans nothing: the click itself is handled from the interaction handler,
 * see {@link dev.polaris_light.constructionwand.plus.basics.ApotheosisCoreEvents}.
 * <p>
 * Planning nothing is also what keeps the wand from placing blocks while this core is selected, on every path
 * that leads through a wand job.
 */
public class ActionApotheosis implements IWandAction
{
    @Override
    public int getLimit(ItemStack wand) {
        // One table per click.
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
    public List<ISnapshot> getSnapshotsFromAir(Level level, Player player, BlockHitResult rayTraceResult,
                                               ItemStack wand, WandOptions options, IWandSupplier supplier, int limit) {
        return new ArrayList<>();
    }
}