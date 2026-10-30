package thetadev.constructionwand.plus.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.plus.material.SupplierStorage;
import thetadev.constructionwand.plus.material.ContainerBinding;

/**
 * Entry point for the optional compatibility modules. Everything here degrades to the plain wand
 * behaviour when the other mod is not installed.
 */
public final class PlusCompat
{
    private PlusCompat() {
    }

    /**
     * @return whether an inventory source outside the wand's own containers exists
     */
    public static boolean hasExtraInventorySource() {
        return CuriosCompat.isLoaded();
    }

    /**
     * @return the supplier for a wand without a material core: bound containers first, then trinkets,
     * then the player inventory
     */
    public static IWandSupplier inventorySupplier(Player player, WandOptions options, ItemStack wand, boolean includeInventory) {
        if(CuriosCompat.isLoaded()) return new SupplierCurios(player, options, wand, includeInventory);
        return new SupplierStorage(player, options, wand, includeInventory);
    }

    /**
     * @return whether the wand should use the extended inventory supplier
     */
    public static boolean usesExtendedInventory(ItemStack wand) {
        return ContainerBinding.count(wand) > 0 || hasExtraInventorySource();
    }

    /**
     * @return whether the mod with that id is loaded
     */
    public static boolean loaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }
}
