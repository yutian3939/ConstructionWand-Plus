package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;

/**
 * Supplier for a wand whose material core is the AE core.
 * <p>
 * Two sources, in this order: the ME network the wand is bound to, which is reached in its own
 * dimension and over any distance, and then whatever the inherited {@link SupplierCompat} can find —
 * the network of a terminal or portable cell the player carries, and the player inventory itself. A
 * wand without a binding therefore behaves exactly like the plain compat supplier, and a wand with one
 * keeps working from another dimension.
 */
public class SupplierAe extends SupplierCompat
{
    private final ItemStack wand;

    public SupplierAe(Player player, WandOptions options, ItemStack wand, String source) {
        super(player, options, wand, source);
        this.wand = wand;
    }

    @Override
    protected void addBlockItem(BlockItem item) {
        int count = saturatedAdd(0, AeGridAccess.count(player, wand, new ItemStack(item)));
        count = saturatedAdd(count, WandUtil.countItem(player, item, source));
        if(count > 0) {
            itemCounts.put(item, count);
            itemPool.add(item);
        }
    }

    @Override
    public int takeItemStack(ItemStack stack) {
        int count = stack.getCount();
        if(player.isCreative()) return 0;

        // The bound network first; the inherited path only sees what is left over.
        int remaining = AeGridAccess.take(player, wand, stack, count);
        if(remaining <= 0) return 0;

        ItemStack rest = stack.copy();
        rest.setCount(remaining);
        return super.takeItemStack(rest);
    }

    private static int saturatedAdd(int left, int right) {
        if(left < 0) left = 0;
        if(right < 0) right = 0;
        long sum = (long) left + (long) right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
