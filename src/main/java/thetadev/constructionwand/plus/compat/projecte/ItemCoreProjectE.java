package thetadev.constructionwand.plus.compat.projecte;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.core.ItemCore;
import thetadev.constructionwand.plus.material.IMaterialCore;
import thetadev.constructionwand.wand.action.ActionConstruction;

/**
 * Material core backed by ProjectE's EMC. Placing a block spends the player's EMC, harvesting one
 * turns it into EMC. Fluids are not part of ProjectE, so the fluid settings of the wand do nothing
 * while this core is selected.
 */
public class ItemCoreProjectE extends ItemCore implements IMaterialCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0xFF3CF4;

    public ItemCoreProjectE(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionConstruction();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("core_projecte");
    }

    @Override
    public IWandSupplier createSupplier(Player player, WandOptions options, ItemStack wand) {
        return new SupplierProjectE(player, options, wand);
    }

    @Override
    public ItemStack deposit(Player player, ItemStack wand, ItemStack stack) {
        if(player == null || stack == null || stack.isEmpty()) return stack;
        return ProjectECompat.convertToEmc(player, stack);
    }

    @Override
    public int countStored(Player player, ItemStack wand, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        return ProjectECompat.countFor(player, template);
    }

    @Override
    public int withdraw(Player player, ItemStack wand, ItemStack template, int amount) {
        if(player == null || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);
        return ProjectECompat.take(player, template, amount);
    }

    @Override
    public int fluidCapacity(Player player, ItemStack wand, FluidStack fluid) {
        return 0;
    }

    @Override
    public int depositFluid(Player player, ItemStack wand, FluidStack fluid) {
        return 0;
    }

    @Override
    public int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount) {
        return Math.max(0, amount);
    }

    @Override
    public int countStoredFluid(Player player, ItemStack wand, FluidStack fluid) {
        return 0;
    }
}
