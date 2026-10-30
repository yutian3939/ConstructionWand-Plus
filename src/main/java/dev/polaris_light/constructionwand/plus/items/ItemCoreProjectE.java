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
import dev.polaris_light.constructionwand.plus.material.ContainerCompat;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.plus.material.SupplierCompat;
import dev.polaris_light.constructionwand.wand.action.ActionConstruction;

/**
 * Material core that stands for the ProjectE module of this fork.
 * <p>
 * It binds nothing: with this core on the wand the player's EMC is simply another source, reached
 * through the very same container handler the wand already used for it, and the player inventory stays
 * the fallback.
 * <p>
 * Fluids are not part of ProjectE, so the fluid settings of the wand do nothing while this core is
 * selected.
 */
public class ItemCoreProjectE extends ItemCore implements IMaterialCore
{
    /**
     * Module this core unlocks, see {@code containers.ContainerRegistrar}.
     */
    public static final String SOURCE = "projecte";

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
        return new SupplierCompat(player, options, wand, SOURCE);
    }

    /**
     * Sells a harvested stack to the player's EMC. Without a transmutation table or tablet in the
     * inventory the stack comes back untouched and the caller routes it to the inventory or the ground
     * instead.
     */
    @Override
    public ItemStack deposit(Player player, ItemStack wand, ItemStack stack) {
        if(player == null || stack == null || stack.isEmpty()) return stack;
        return ContainerCompat.deposit(player, SOURCE, stack);
    }

    @Override
    public int countStored(Player player, ItemStack wand, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        return ContainerCompat.count(player, SOURCE, template);
    }

    @Override
    public int withdraw(Player player, ItemStack wand, ItemStack template, int amount) {
        if(player == null || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);
        return ContainerCompat.take(player, SOURCE, template, amount);
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
