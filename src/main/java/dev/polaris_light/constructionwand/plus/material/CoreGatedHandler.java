package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import dev.polaris_light.constructionwand.api.IContainerHandler;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.containers.ContainerTrace;
import dev.polaris_light.constructionwand.plus.basics.WandData;

import javax.annotation.Nullable;

/**
 * A container handler that only answers while the wand the player holds carries the core it belongs
 * to.
 * <p>
 * The compatibility modules of this fork (the ME network behind a wireless terminal or a portable
 * cell, and the player's EMC) are reached on the ordinary container path, so they would work for any
 * wand. With this wrapper in front of them they are only reachable once the matching wand core is
 * installed: the core is what unlocks the module, and the module itself is what hands the items out
 * and takes them back. Nothing about how an item is consumed or reclaimed changes, only who is
 * allowed to ask.
 */
public final class CoreGatedHandler implements IContainerHandler
{
    private final DeferredHolder<Item, Item> core;
    private final IContainerHandler delegate;

    private CoreGatedHandler(DeferredHolder<Item, Item> core, IContainerHandler delegate) {
        this.core = core;
        this.delegate = delegate;
    }

    /**
     * @param core the core that unlocks the delegate, or {@code null} when the mod it belongs to is
     *             not installed, in which case the delegate is left ungated (it is never registered)
     */
    public static IContainerHandler of(@Nullable DeferredHolder<Item, Item> core, IContainerHandler delegate) {
        return core == null ? delegate : new CoreGatedHandler(core, delegate);
    }

    @Override
    public boolean matches(Player player, ItemStack itemStack, ItemStack inventoryStack) {
        return carriesCore(player) && delegate.matches(player, itemStack, inventoryStack);
    }

    @Override
    public int getSignature(Player player, ItemStack inventoryStack) {
        return delegate.getSignature(player, inventoryStack);
    }

    @Override
    public int countItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack) {
        return delegate.countItems(player, trace, itemStack, inventoryStack);
    }

    @Override
    public int useItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack, int count) {
        return delegate.useItems(player, trace, itemStack, inventoryStack, count);
    }

    @Override
    public ItemStack insertItems(Player player, ContainerTrace trace, ItemStack inventoryStack, ItemStack stack) {
        return delegate.insertItems(player, trace, inventoryStack, stack);
    }

    @Override
    public int countFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return delegate.countFluid(player, trace, fluid, inventoryStack);
    }

    @Override
    public int fluidCapacity(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return delegate.fluidCapacity(player, trace, fluid, inventoryStack);
    }

    @Override
    public int insertFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack) {
        return delegate.insertFluid(player, trace, fluid, inventoryStack);
    }

    @Override
    public int useFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack, int amount) {
        return delegate.useFluid(player, trace, fluid, inventoryStack, amount);
    }

    /**
     * @return whether the held wand has this core installed, read straight off its option tag
     */
    private boolean carriesCore(Player player) {
        if(player == null) return false;
        ItemStack wand = WandUtil.holdingWand(player);
        return wand != null && WandData.hasInstalled(wand, "material_cores", core.get());
    }
}
