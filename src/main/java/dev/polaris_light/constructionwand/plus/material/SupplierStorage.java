package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.wand.supplier.SupplierInventory;

import javax.annotation.Nonnull;

/**
 * Supplier backed by the containers the wand is bound to.
 * <p>
 * The bound containers are always consulted first, in binding order; the player inventory is only
 * used for whatever they cannot provide. {@code includeInventory} switches that fallback off, which
 * restricts a wand to its remote storage.
 */
public class SupplierStorage extends SupplierInventory
{
    private final ItemStack wand;
    private final boolean includeInventory;

    public SupplierStorage(Player player, WandOptions options, ItemStack wand, boolean includeInventory) {
        super(player, options);
        this.wand = wand;
        this.includeInventory = includeInventory;
    }

    @Override
    protected void addBlockItem(BlockItem item) {
        int count = 0;
        if(includeInventory) count += WandUtil.countItem(player, item);
        count = saturatedAdd(count, countStored(item));
        // Additional sources (trinket slots) add to what the wand can reach.
        count = saturatedAdd(count, extras(item));
        if(count > 0) {
            itemCounts.put(item, count);
            itemPool.add(item);
        }
    }

    /**
     * @return how many more items equivalent to the template the extra sources hold
     */
    protected int extras(BlockItem item) {
        return 0;
    }

    /**
     * @return the amount the extra sources could not take
     */
    protected int takeExtras(ItemStack template, int amount) {
        return amount;
    }

    private int countStored(BlockItem item) {
        try {
            return Math.max(0, ContainerBinding.countStored(player.level(), wand, new ItemStack(item)));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    @Override
    public int takeItemStack(@Nonnull ItemStack stack) {
        int count = stack.getCount();
        if(player.isCreative()) return 0;

        // Bound containers first, the player inventory only for the remainder.
        count = withdraw(stack, count);
        if(count > 0) count = takeExtras(stack, count);
        if(count <= 0 || !includeInventory) return Math.max(0, count);

        ItemStack remainder = stack.copy();
        remainder.setCount(count);
        return super.takeItemStack(remainder);
    }

    private int withdraw(ItemStack template, int amount) {
        if(amount <= 0) return amount;
        try {
            int remaining = ContainerBinding.take(player.level(), wand, template, amount);
            return remaining < 0 || remaining > amount ? amount : remaining;
        } catch(RuntimeException exception) {
            return amount;
        }
    }

    private static int saturatedAdd(int left, int right) {
        if(left < 0) left = 0;
        if(right < 0) right = 0;
        long sum = (long) left + (long) right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
