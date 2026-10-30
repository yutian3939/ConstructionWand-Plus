package dev.polaris_light.constructionwand.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.containers.ContainerTrace;

public interface IContainerHandler
{
    boolean matches(Player player, ItemStack itemStack, ItemStack inventoryStack);

    // Ender Chest is 0, Invaild ids -1
    int getSignature(Player player, ItemStack inventoryStack);

    int countItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack);

    int useItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack, int count);

    /**
     * Puts an item into the container.
     * <p>
     * The interface grew up around taking items out, so the default refuses: a container that cannot
     * store hands the stack straight back and the caller routes it somewhere else. The ME network and
     * the player's EMC do store, and that is what the material cores of the plus content deposit into.
     *
     * @return the part of the stack that did not fit, or the stack itself when it cannot store at all
     */
    default ItemStack insertItems(Player player, ContainerTrace trace, ItemStack inventoryStack, ItemStack stack) {
        return stack;
    }

    /**
     * @return how much of that fluid the container holds, in mB
     */
    default int countFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return 0;
    }

    /**
     * @return how much of that fluid the container can still take, in mB
     */
    default int fluidCapacity(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return 0;
    }

    /**
     * Puts fluid into the container, the way {@link #insertItems} puts items in.
     *
     * @return how much of it really went in, in mB
     */
    default int insertFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return 0;
    }

    /**
     * Takes fluid out of the container.
     *
     * @return the amount that could not be taken, in mB
     */
    default int useFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack, int amount) {
        return amount;
    }
}
