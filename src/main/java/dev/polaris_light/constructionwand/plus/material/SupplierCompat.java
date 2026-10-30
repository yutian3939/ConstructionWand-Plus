package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.wand.supplier.SupplierInventory;

import javax.annotation.Nullable;

/**
 * Supplier for a wand whose material core stands for one compatibility module.
 * <p>
 * The module is asked first, through the same container handler the fork already uses for a wireless
 * terminal or a portable cell in the player's inventory; the player's own inventory is the fallback,
 * exactly like the bound containers of the storage core. Everything else about placing is unchanged:
 * the same item counts, the same pool and the same durability path.
 */
public class SupplierCompat extends SupplierInventory
{
    protected final String source;

    public SupplierCompat(Player player, WandOptions options, ItemStack wand, String source) {
        super(player, options);
        this.source = source;
    }

    @Override
    protected void addBlockItem(BlockItem item) {
        int count = WandUtil.countItem(player, item, source);
        if(count > 0) {
            itemCounts.put(item, count);
            itemPool.add(item);
        }
    }

    /**
     * Restricts the container pass of the inherited taking to this module, so a wand building out of
     * the ME network never eats the blocks in the player's backpacks.
     */
    @Override
    protected @Nullable String containerSource() {
        return source;
    }
}
