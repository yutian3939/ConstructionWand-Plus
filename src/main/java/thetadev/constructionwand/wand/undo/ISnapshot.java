package thetadev.constructionwand.wand.undo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thetadev.constructionwand.plus.material.IMaterialCore;

import javax.annotation.Nullable;
import java.util.List;

public interface ISnapshot
{
    BlockPos getPos();

    BlockState getBlockState();

    ItemStack getRequiredItems();

    boolean execute(Level world, Player player, BlockHitResult rayTraceResult);

    boolean canRestore(Level world, Player player);

    boolean restore(Level world, Player player);

    void forceRestore(Level world);

    /**
     * The material this snapshot takes back from the player or the wand's store when it is undone, or
     * {@code null} when it hands nothing out.
     * <p>
     * The undo collects them from every snapshot of the operation and checks the total before anything
     * is taken. A per block check would not do: the snapshots share the player inventory and the
     * material core, so every one of them can look satisfied on its own while the operation as a whole
     * does not have enough, and the undo would then run out of material halfway through.
     */
    @Nullable
    default Reclaim getReclaim() {
        return null;
    }

    /**
     * What one snapshot takes back.
     *
     * @param store the material core the items may be taken from, {@code null} when only the player
     *              inventory holds them
     * @param wand  the wand the store belongs to, {@code null} when there is no store
     * @param items the items that were handed out, in the amounts that were handed out
     */
    record Reclaim(@Nullable IMaterialCore store, @Nullable ItemStack wand, List<ItemStack> items)
    {
        public Reclaim {
            items = List.copyOf(items);
        }
    }
}
