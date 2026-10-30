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
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;
import dev.polaris_light.constructionwand.plus.material.ContainerCompat;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.plus.material.SupplierAe;
import dev.polaris_light.constructionwand.wand.action.ActionConstruction;

/**
 * Material core that stands for the Applied Energistics 2 module of this fork.
 * <p>
 * Two ways into the network, both reached through the same container handlers the fork already used:
 * a wireless terminal or portable cell the player carries, and a controller the wand is bound to (sneak
 * and right click it, repeat to unbind). The binding is the more powerful of the two — only a position
 * and a dimension are stored on the wand, so the network is reached over any distance and from any
 * dimension — which is why this core exists again even though the terminal alone would work.
 * <p>
 * Fluids are part of that surface too: a network keeps them in the same storage as items, so a fluid
 * storage cell or component makes the wand's {@code fluid_storage} setting work with this core.
 */
public class ItemCoreAE extends ItemCore implements IMaterialCore
{
    /**
     * Module this core unlocks, see {@code containers.ContainerRegistrar}.
     */
    public static final String SOURCE = "ae2";

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
        return new SupplierAe(player, options, wand, SOURCE);
    }

    /**
     * Stores a harvested stack, in the bound network first and then in the network of a wireless
     * terminal or portable cell the player carries. Whatever neither takes comes back untouched and the
     * caller routes it to the inventory or the ground instead.
     */
    @Override
    public ItemStack deposit(Player player, ItemStack wand, ItemStack stack) {
        if(player == null || stack == null || stack.isEmpty()) return stack;

        ItemStack remaining = AeGridAccess.insert(player, wand, stack);
        if(remaining.isEmpty()) return ItemStack.EMPTY;
        return ContainerCompat.deposit(player, SOURCE, remaining);
    }

    @Override
    public int countStored(Player player, ItemStack wand, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        int bound = AeGridAccess.count(player, wand, template);
        int carried = ContainerCompat.count(player, SOURCE, template);
        long total = (long) bound + (long) carried;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @Override
    public int withdraw(Player player, ItemStack wand, ItemStack template, int amount) {
        if(player == null || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);

        int remaining = AeGridAccess.take(player, wand, template, amount);
        if(remaining <= 0) return 0;
        return ContainerCompat.take(player, SOURCE, template, remaining);
    }

    /**
     * How much more of that fluid the wand can put away. Both the bound network and the network of a
     * terminal the player carries hold fluids in the same storage as items, so a fluid storage cell or
     * component is enough for this to be non-zero.
     */
    @Override
    public int fluidCapacity(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        long total = (long) AeGridAccess.fluidCapacity(player, wand, fluid)
                + (long) ContainerCompat.fluidCapacity(player, SOURCE, fluid);
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @Override
    public int depositFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;

        int stored = AeGridAccess.depositFluid(player, wand, fluid);
        if(stored >= fluid.getAmount()) return stored;

        FluidStack rest = new FluidStack(fluid.getFluid(), fluid.getAmount() - stored);
        return stored + ContainerCompat.storeFluid(player, SOURCE, rest);
    }

    @Override
    public int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount) {
        if(player == null || amount <= 0) return Math.max(0, amount);
        if(fluid == null || fluid.isEmpty()) return Math.max(0, amount);

        int remaining = AeGridAccess.withdrawFluid(player, wand, fluid, amount);
        if(remaining <= 0) return 0;
        return ContainerCompat.takeFluid(player, SOURCE, fluid, remaining);
    }

    @Override
    public int countStoredFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(player == null || fluid == null || fluid.isEmpty()) return 0;
        long total = (long) AeGridAccess.countFluid(player, wand, fluid)
                + (long) ContainerCompat.countFluid(player, SOURCE, fluid);
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
