package thetadev.constructionwand.plus.compat.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.wand.supplier.SupplierInventory;

import javax.annotation.Nonnull;

/**
 * Supplier backed by the ME network the wand is bound to. Like the storage core this core restricts
 * the wand to its remote storage: the player inventory is not used.
 */
public class SupplierAE extends SupplierInventory
{
    private final ItemStack wand;

    public SupplierAE(Player player, WandOptions options, ItemStack wand) {
        super(player, options);
        this.wand = wand;
    }

    @Override
    protected void addBlockItem(BlockItem item) {
        int count = AE2Compat.count(player.level(), player, wand, new ItemStack(item));
        if(count > 0) {
            itemCounts.put(item, count);
            itemPool.add(item);
        }
    }

    @Override
    public int takeItemStack(@Nonnull ItemStack stack) {
        if(player.isCreative()) return 0;
        return AE2Compat.take(player.level(), player, wand, stack, stack.getCount());
    }
}
