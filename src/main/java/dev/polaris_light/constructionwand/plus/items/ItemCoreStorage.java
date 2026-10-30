package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.items.core.ItemCore;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.plus.material.SupplierStorage;
import dev.polaris_light.constructionwand.wand.action.ActionConstruction;

/**
 * Material core backed by the containers bound to the wand.
 * <p>
 * On its own it does nothing: the behaviour comes from whichever behaviour core is selected. Sneak
 * and right click a container with this core selected to bind it, repeat that to unbind it; the wand
 * then builds from them in the order the configuration screen shows and stores what it harvests back
 * into them the same way. The player inventory is only used for whatever the bound containers cannot
 * provide.
 */
public class ItemCoreStorage extends ItemCore implements IMaterialCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0xC1741F;

    public ItemCoreStorage(Properties properties) {
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
        return ConstructionWand.loc("core_storage");
    }

    @Override
    public IWandSupplier createSupplier(Player player, WandOptions options, ItemStack wand) {
        return new SupplierStorage(player, options, wand, true);
    }

    @Override
    public ItemStack deposit(Player player, ItemStack wand, ItemStack stack) {
        if(player == null || stack == null || stack.isEmpty()) return stack;
        return ContainerBinding.insert(player.level(), wand, stack);
    }

    @Override
    public int countStored(Player player, ItemStack wand, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        return ContainerBinding.countStored(player.level(), wand, template);
    }

    @Override
    public int withdraw(Player player, ItemStack wand, ItemStack template, int amount) {
        if(player == null || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);
        return ContainerBinding.take(player.level(), wand, template, amount);
    }

    @Override
    public int fluidCapacity(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return ContainerBinding.fluidCapacity(player.level(), wand, fluid);
    }

    @Override
    public int depositFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return ContainerBinding.fill(player.level(), wand, fluid);
    }

    @Override
    public int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount) {
        if(player == null || amount <= 0) return Math.max(0, amount);
        if(fluid == null || fluid.isEmpty()) return Math.max(0, amount);
        return ContainerBinding.drain(player.level(), wand, fluid, amount);
    }

    @Override
    public int countStoredFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        return ContainerBinding.countStoredFluid(player.level(), wand, fluid);
    }
}
