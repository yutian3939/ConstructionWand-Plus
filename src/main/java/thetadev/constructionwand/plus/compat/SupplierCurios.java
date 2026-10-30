package thetadev.constructionwand.plus.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.plus.material.SupplierStorage;

/**
 * The inventory supplier with trinket slots added: bound containers first, then trinkets, then the
 * player inventory.
 */
public class SupplierCurios extends SupplierStorage
{
    public SupplierCurios(Player player, WandOptions options, ItemStack wand, boolean includeInventory) {
        super(player, options, wand, includeInventory);
    }

    @Override
    protected int extras(BlockItem item) {
        return Math.max(0, CuriosCompat.count(player, new ItemStack(item)));
    }

    @Override
    protected int takeExtras(ItemStack template, int amount) {
        if(amount <= 0) return amount;
        int remaining = CuriosCompat.take(player, template, amount);
        return remaining < 0 || remaining > amount ? amount : remaining;
    }
}
