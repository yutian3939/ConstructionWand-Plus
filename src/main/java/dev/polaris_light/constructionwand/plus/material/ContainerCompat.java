package dev.polaris_light.constructionwand.plus.material;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.containers.ContainerManager;
import dev.polaris_light.constructionwand.containers.ContainerTrace;

/**
 * Item access through the container handlers of one compatibility module.
 * <p>
 * This is the same call the fork already makes for a wireless terminal or a portable cell in the
 * player's inventory, only narrowed to a single module: a wand core that names a module reads and
 * spends from that module alone, never from the shulker boxes or backpacks the player carries. The
 * plain inventory is left out on purpose here, because the core's supplier adds it as the fallback.
 */
public final class ContainerCompat
{
    private ContainerCompat() {
    }

    /**
     * @return how many items equivalent to the template that module holds
     */
    public static int count(Player player, String source, ItemStack template) {
        if(!(player instanceof ServerPlayer serverPlayer) || template == null || template.isEmpty()) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return 0;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int total = 0;
        for(ItemStack stack : WandUtil.getFullInv(serverPlayer)) {
            // A stack of the item itself is the player's own, not the module's storage.
            if(stack == null || stack.isEmpty() || WandUtil.stackEquals(stack, template.getItem())) continue;

            int amount = manager.countItems(serverPlayer, trace, template, stack, source);
            if(amount == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            total += amount;
        }
        return total;
    }

    /**
     * @return the amount that module could not hand out
     */
    public static int take(Player player, String source, ItemStack template, int amount) {
        if(!(player instanceof ServerPlayer serverPlayer) || template == null || template.isEmpty()) return Math.max(0, amount);
        if(amount <= 0) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return amount;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int remaining = amount;
        for(ItemStack stack : WandUtil.getFullInv(serverPlayer)) {
            if(remaining <= 0) break;
            if(stack == null || stack.isEmpty() || WandUtil.stackEquals(stack, template.getItem())) continue;

            remaining = manager.useItems(serverPlayer, trace, template, stack, remaining, source);
        }
        return Math.max(0, remaining);
    }

    /**
     * Hands a harvested stack to that module: into the ME network behind a terminal the player carries,
     * or sold to their EMC. The stack is only counted as stored once the module reports it gone, so a
     * module that can only take part of it leaves the rest to the caller's own routing.
     *
     * @return the part of the stack no container of that module took
     */
    public static ItemStack deposit(Player player, String source, ItemStack stack) {
        if(!(player instanceof ServerPlayer serverPlayer) || stack == null || stack.isEmpty()) return stack;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return stack;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        ItemStack remaining = stack;
        for(ItemStack inventoryStack : WandUtil.getFullInv(serverPlayer)) {
            if(remaining.isEmpty()) break;
            if(inventoryStack == null || inventoryStack.isEmpty()) continue;

            remaining = manager.insertItems(serverPlayer, trace, inventoryStack, remaining, source);
        }
        return remaining;
    }

    /**
     * @return how much of that fluid that module holds, in mB
     */
    public static int countFluid(Player player, String source, FluidStack fluid) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid == null || fluid.isEmpty()) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return 0;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int total = 0;
        for(ItemStack inventoryStack : WandUtil.getFullInv(serverPlayer)) {
            if(inventoryStack == null || inventoryStack.isEmpty()) continue;
            total += manager.countFluid(serverPlayer, trace, fluid, inventoryStack, source);
        }
        return total;
    }

    /**
     * @return how much more of that fluid that module can take, in mB
     */
    public static int fluidCapacity(Player player, String source, FluidStack fluid) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid == null || fluid.isEmpty()) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return 0;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int total = 0;
        for(ItemStack inventoryStack : WandUtil.getFullInv(serverPlayer)) {
            if(inventoryStack == null || inventoryStack.isEmpty()) continue;
            total += manager.fluidCapacity(serverPlayer, trace, fluid, inventoryStack, source);
        }
        return total;
    }

    /**
     * Hands fluid to that module, the way {@link #deposit} hands items over.
     *
     * @return how much of it really went in, in mB
     */
    public static int storeFluid(Player player, String source, FluidStack fluid) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid == null || fluid.isEmpty()) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return 0;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int stored = 0;
        for(ItemStack inventoryStack : WandUtil.getFullInv(serverPlayer)) {
            if(stored >= fluid.getAmount()) break;
            if(inventoryStack == null || inventoryStack.isEmpty()) continue;

            FluidStack request = new FluidStack(fluid.getFluid(), fluid.getAmount() - stored);
            stored += manager.insertFluid(serverPlayer, trace, request, inventoryStack, source);
        }
        return Math.min(stored, fluid.getAmount());
    }

    /**
     * @return the amount of that fluid that could not be taken out of that module, in mB
     */
    public static int takeFluid(Player player, String source, FluidStack fluid, int amount) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid == null || fluid.isEmpty()) return Math.max(0, amount);
        if(amount <= 0) return 0;
        ContainerManager manager = ConstructionWand.containerManager;
        if(manager == null) return amount;

        ContainerTrace trace = new ContainerTrace(serverPlayer);
        int remaining = amount;
        for(ItemStack inventoryStack : WandUtil.getFullInv(serverPlayer)) {
            if(remaining <= 0) break;
            if(inventoryStack == null || inventoryStack.isEmpty()) continue;

            FluidStack request = new FluidStack(fluid.getFluid(), remaining);
            remaining = manager.useFluid(serverPlayer, trace, request, inventoryStack, remaining, source);
        }
        return Math.max(0, remaining);
    }
}
