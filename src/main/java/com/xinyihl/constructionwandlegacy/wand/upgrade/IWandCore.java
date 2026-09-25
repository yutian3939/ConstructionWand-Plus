package com.xinyihl.constructionwandlegacy.wand.upgrade;

import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * A core installed in a wand.
 * <p>
 * Cores come in two independent kinds:
 * <ul>
 *   <li><b>Behaviour cores</b> (construction, angel, destruction, digging) decide what the wand
 *       does through {@link #getWandAction()}.</li>
 *   <li><b>Material cores</b> (AE, ProjectE, storage) decide where the wand takes its items from
 *       and where it stores them. They supply no action of their own.</li>
 * </ul>
 * A wand selects one of each, the two selections never influence each other.
 */
public interface IWandCore extends IWandUpgrade {
    int getColor();

    WandAction getWandAction();

    default MaterialSourceFactory getMaterialSourceFactory() {
        return null;
    }

    /**
     * Material cores decide where items come from and go. Behaviour cores return {@code false}.
     */
    default boolean isMaterialCore() {
        return false;
    }

    /**
     * Stores one harvested stack in this material core.
     *
     * @return the part that did not fit
     */
    default ItemStack deposit(EntityPlayer player, ItemStack wand, ItemStack stack) {
        return stack;
    }

    /**
     * How many items equivalent to {@code template} this material core currently holds. Used to
     * take an undo back.
     */
    default int countStored(EntityPlayer player, ItemStack wand, ItemStack template) {
        return 0;
    }

    /**
     * Takes stored items back, used to take an undo back.
     *
     * @return the amount that could not be taken
     */
    default int withdraw(EntityPlayer player, ItemStack wand, ItemStack template, int amount) {
        return amount;
    }

    /**
     * How much of that fluid this material core could add right now. Used to plan how many fluid
     * sources a dig may clear, see {@code FluidBudget}.
     */
    default int fluidCapacity(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return 0;
    }

    /**
     * Stores fluid a digging core harvested, one bucket per source block.
     *
     * @return the amount that was stored, in mB
     */
    default int depositFluid(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return 0;
    }

    /**
     * Takes stored fluid back, used to take an undo back.
     *
     * @return the amount that could not be taken, in mB
     */
    default int withdrawFluid(EntityPlayer player, ItemStack wand, FluidStack fluid, int amount) {
        return amount;
    }

    /**
     * How much of that fluid this material core currently holds. Used to pre-check an undo before
     * anything is restored.
     */
    default int countStoredFluid(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return 0;
    }
}
