package thetadev.constructionwand.plus.material;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.option.WandOptions;

/**
 * A wand core that provides the materials the wand places and harvests from somewhere else than the
 * player inventory: the bound containers, the ME network, EMC.
 * <p>
 * The contract mirrors the 1.12.2 original: every method works with leftovers. An insert returns what
 * did not fit, an extract returns what could not be taken. Fluid amounts are in mB and one source
 * block is worth one bucket.
 * <p>
 * Implementations must never throw: the wand calls them while planning and while rolling an
 * operation back, and a broken store simply counts as empty.
 */
public interface IMaterialCore extends IWandCore
{
    /**
     * @return the supplier the wand builds from when this material core is selected
     */
    IWandSupplier createSupplier(Player player, WandOptions options, ItemStack wand);

    /**
     * Stores one harvested stack.
     *
     * @return the part that did not fit
     */
    ItemStack deposit(Player player, ItemStack wand, ItemStack stack);

    /**
     * @return how many items equivalent to the template are stored
     */
    int countStored(Player player, ItemStack wand, ItemStack template);

    /**
     * Takes items out of the store.
     *
     * @return the amount that could not be taken
     */
    int withdraw(Player player, ItemStack wand, ItemStack template, int amount);

    /**
     * @return how much of that fluid the store can still take
     */
    int fluidCapacity(Player player, ItemStack wand, FluidStack fluid);

    /**
     * @return how much of that fluid really went in
     */
    int depositFluid(Player player, ItemStack wand, FluidStack fluid);

    /**
     * @return the amount of fluid that could not be taken
     */
    int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount);

    /**
     * @return how much of that fluid is stored
     */
    int countStoredFluid(Player player, ItemStack wand, FluidStack fluid);
}
