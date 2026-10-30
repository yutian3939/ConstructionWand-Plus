package thetadev.constructionwand.plus.compat.ae2;

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
 * Material core backed by an ME network. Sneak and right click a controller with this core selected
 * to bind the wand to that network; the wand then places blocks out of it and can also store what it
 * harvests (or the fluid it clears) back into it.
 */
public class ItemCoreAE extends ItemCore implements IMaterialCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x3CA4FF;

    public ItemCoreAE(Properties properties) {
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
        return ConstructionWand.loc("core_ae");
    }

    @Override
    public IWandSupplier createSupplier(Player player, WandOptions options, ItemStack wand) {
        return new SupplierAE(player, options, wand);
    }

    @Override
    public ItemStack deposit(Player player, ItemStack wand, ItemStack stack) {
        if(player == null || stack == null || stack.isEmpty()) return stack;
        return AE2Compat.insert(player.level(), player, wand, stack);
    }

    @Override
    public int countStored(Player player, ItemStack wand, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        return AE2Compat.count(player.level(), player, wand, template);
    }

    @Override
    public int withdraw(Player player, ItemStack wand, ItemStack template, int amount) {
        if(player == null || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);
        return AE2Compat.take(player.level(), player, wand, template, amount);
    }

    @Override
    public int fluidCapacity(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return AE2Compat.fluidCapacity(player.level(), player, wand, fluid);
    }

    @Override
    public int depositFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return AE2Compat.depositFluid(player.level(), player, wand, fluid);
    }

    @Override
    public int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount) {
        if(player == null || amount <= 0) return Math.max(0, amount);
        if(fluid == null || fluid.isEmpty()) return Math.max(0, amount);
        return AE2Compat.withdrawFluid(player.level(), player, wand, fluid, amount);
    }

    @Override
    public int countStoredFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return AE2Compat.countFluid(player.level(), player, wand, fluid);
    }
}
