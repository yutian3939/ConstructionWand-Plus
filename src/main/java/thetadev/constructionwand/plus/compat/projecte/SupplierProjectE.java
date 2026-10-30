package thetadev.constructionwand.plus.compat.projecte;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.wand.supplier.SupplierInventory;

import javax.annotation.Nonnull;

/**
 * Supplier backed by EMC: everything the player can pay for is available, and placing it spends the
 * corresponding EMC. The player inventory is not used while this core is selected.
 */
public class SupplierProjectE extends SupplierInventory
{
    private final ItemStack wand;

    public SupplierProjectE(Player player, WandOptions options, ItemStack wand) {
        super(player, options);
        this.wand = wand;
    }

    @Override
    protected void addBlockItem(BlockItem item) {
        int count = ProjectECompat.countFor(player, new ItemStack(item));
        if(count > 0) {
            itemCounts.put(item, count);
            itemPool.add(item);
        }
    }

    @Override
    public int takeItemStack(@Nonnull ItemStack stack) {
        if(player.isCreative()) return 0;
        return ProjectECompat.take(player, stack, stack.getCount());
    }
}
