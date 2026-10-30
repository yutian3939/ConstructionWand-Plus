package dev.polaris_light.constructionwand.containers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.api.IContainerHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class ContainerManager
{
    /**
     * A handler together with the compatibility module it belongs to, or {@code null} for the general
     * ones. The source is what lets a material core address only the handlers of its own mod, see the
     * overloads below and {@code plus.material.SupplierCompat}.
     */
    private record Entry(@Nullable String source, IContainerHandler handler)
    {
    }

    private final ArrayList<Entry> handlers;

    public ContainerManager() {
        handlers = new ArrayList<>();
    }

    public boolean register(IContainerHandler handler) {
        return register(null, handler);
    }

    public boolean register(@Nullable String source, IContainerHandler handler) {
        return handlers.add(new Entry(source, handler));
    }

    public int countItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack) {
        return countItems(player, trace, itemStack, inventoryStack, null);
    }

    /**
     * @param source when non-null, only the handlers registered for that source are consulted
     */
    public int countItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack,
                          @Nullable String source) {
        for(Entry entry : handlers) {
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(handler.matches(player, itemStack, inventoryStack)) {
                int sig = handler.getSignature(player, inventoryStack);
                if (trace.push(sig)) {
                    return handler.countItems(player, trace, itemStack, inventoryStack);
                } else {
                    return 0;
                }
            }
        }
        return 0;
    }

    public int useItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack, int count) {
        return useItems(player, trace, itemStack, inventoryStack, count, null);
    }

    /**
     * @param source when non-null, only the handlers registered for that source are consulted
     */
    public int useItems(Player player, ContainerTrace trace, ItemStack itemStack, ItemStack inventoryStack, int count,
                        @Nullable String source) {
        for(Entry entry : handlers) {
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(handler.matches(player, itemStack, inventoryStack)) {
                int sig = handler.getSignature(player, inventoryStack);
                if (trace.push(sig)) {
                    return handler.useItems(player, trace, itemStack, inventoryStack, count);
                } else {
                    return count;
                }
            }
        }
        return count;
    }

    /**
     * @return whether the handler belongs to the requested source. A {@code null} source asks for every
     * handler, and a handler without a source is only reached by that request.
     */
    private static boolean matchesSource(Entry entry, @Nullable String source) {
        return source == null || source.equals(entry.source());
    }

    /**
     * Hands an item to the containers that can store it, in registration order, and stops as soon as
     * the stack is gone.
     *
     * @return the part of the stack no container took
     */
    public ItemStack insertItems(Player player, ContainerTrace trace, ItemStack inventoryStack, ItemStack stack) {
        return insertItems(player, trace, inventoryStack, stack, null);
    }

    /**
     * @param source when non-null, only the handlers registered for that source are consulted
     */
    public ItemStack insertItems(Player player, ContainerTrace trace, ItemStack inventoryStack, ItemStack stack,
                                 @Nullable String source) {
        ItemStack remaining = stack;
        for(Entry entry : handlers) {
            if(remaining.isEmpty()) break;
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(!handler.matches(player, remaining, inventoryStack)) continue;

            int sig = handler.getSignature(player, inventoryStack);
            if (!trace.push(sig)) continue;

            ItemStack leftover = handler.insertItems(player, trace, inventoryStack, remaining);
            if(leftover != null) remaining = leftover;
        }
        return remaining;
    }

    /**
     * @return how much of that fluid the containers hold, in mB
     */
    public int countFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack,
                          @Nullable String source) {
        int total = 0;
        for(Entry entry : handlers) {
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(!handler.matches(player, ItemStack.EMPTY, inventoryStack)) continue;

            int sig = handler.getSignature(player, inventoryStack);
            if (!trace.push(sig)) continue;

            total += Math.max(0, handler.countFluid(player, trace, fluid, inventoryStack));
        }
        return total;
    }

    /**
     * @return how much of that fluid the containers can still take, in mB
     */
    public int fluidCapacity(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack,
                             @Nullable String source) {
        int total = 0;
        for(Entry entry : handlers) {
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(!handler.matches(player, ItemStack.EMPTY, inventoryStack)) continue;

            int sig = handler.getSignature(player, inventoryStack);
            if (!trace.push(sig)) continue;

            total += Math.max(0, handler.fluidCapacity(player, trace, fluid, inventoryStack));
        }
        return total;
    }

    /**
     * Hands fluid to the containers that can store it, in registration order, and stops as soon as all
     * of it is gone.
     *
     * @return how much of it really went in, in mB
     */
    public int insertFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack,
                           @Nullable String source) {
        int stored = 0;
        for(Entry entry : handlers) {
            if(stored >= fluid.getAmount()) break;
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(!handler.matches(player, ItemStack.EMPTY, inventoryStack)) continue;

            int sig = handler.getSignature(player, inventoryStack);
            if (!trace.push(sig)) continue;

            int left = fluid.getAmount() - stored;
            FluidStack rest = new FluidStack(fluid.getFluid(), left);
            stored += Math.max(0, Math.min(left, handler.insertFluid(player, trace, rest, inventoryStack)));
        }
        return stored;
    }

    /**
     * Takes fluid out of the containers, in registration order.
     *
     * @return the amount that could not be taken, in mB
     */
    public int useFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack inventoryStack, int amount,
                        @Nullable String source) {
        int remaining = amount;
        for(Entry entry : handlers) {
            if(remaining <= 0) break;
            if(!matchesSource(entry, source)) continue;

            IContainerHandler handler = entry.handler();
            if(!handler.matches(player, ItemStack.EMPTY, inventoryStack)) continue;

            int sig = handler.getSignature(player, inventoryStack);
            if (!trace.push(sig)) continue;

            remaining = Math.max(0, Math.min(remaining,
                    handler.useFluid(player, trace, fluid, inventoryStack, remaining)));
        }
        return remaining;
    }
}
