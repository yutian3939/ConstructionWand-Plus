package dev.polaris_light.constructionwand.plus.wand;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.basics.WandLimit;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour of the slay core. It kills entities instead of touching blocks, so it plans nothing: the
 * actual killing happens from the item interaction handlers (a left click on a creature and the
 * sneaking right click) in {@link dev.polaris_light.constructionwand.items.wand.ItemWand}.
 */
public class ActionSlay implements IWandAction
{
    @Override
    public int getLimit(ItemStack wand) {
        return WandLimit.resolve(wand, ConfigServer.getSlayRadius(wand.getItem()));
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
