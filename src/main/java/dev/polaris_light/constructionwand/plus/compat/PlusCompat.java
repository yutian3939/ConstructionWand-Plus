package dev.polaris_light.constructionwand.plus.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.material.SupplierStorage;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;

/**
 * Entry point for the optional compatibility modules. Everything here degrades to the plain wand
 * behaviour when the other mod is not installed.
 * <p>
 * AE2 and ProjectE need no module of their own: this fork's {@code ContainerManager} already reaches
 * both (the ME network behind a wireless terminal or portable cell, and the player's EMC), so a wand
 * draws from them through the ordinary container path. Trinket slots need no module either, since
 * {@code SupplierInventory} already reads the armour and Curios slots. All that is left here is to put
 * the wand's bound containers in front of the inventory.
 */
public final class PlusCompat
{
    private PlusCompat() {
    }

    /**
     * @return the supplier for a wand without a material core: bound containers first, then the player
     * inventory
     */
    public static IWandSupplier inventorySupplier(Player player, WandOptions options, ItemStack wand, boolean includeInventory) {
        return new SupplierStorage(player, options, wand, includeInventory);
    }

    /**
     * @return whether the wand should use the extended inventory supplier
     */
    public static boolean usesExtendedInventory(ItemStack wand) {
        return ContainerBinding.count(wand) > 0;
    }
}
