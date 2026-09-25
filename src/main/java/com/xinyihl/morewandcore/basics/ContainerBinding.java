package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.material.MaterialKey;
import com.xinyihl.constructionwandlegacy.material.SaturatedAmounts;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The containers a wand is bound to, in the order they are used, as storage handles.
 * <p>
 * The binding itself lives on the wand and is shared with the stock construction core, see
 * {@link BoundContainerSourceFactory}. Everything here walks that list the same way the wand places
 * blocks: the first container is filled first and emptied first, the ones behind it only see what
 * does not fit or is not there.
 */
public final class ContainerBinding {
    private ContainerBinding() {
    }

    /**
     * How many containers the wand is bound to, zero when it has none.
     */
    public static int count(ItemStack wand) {
        return BoundContainerSourceFactory.readBindings(wand).size();
    }

    /**
     * Stores a harvested stack in the bound containers, the first one first.
     *
     * @return the part that did not fit anywhere
     */
    public static ItemStack insert(ItemStack wand, ItemStack stack) {
        ItemStack remaining = stack;
        for (Handle handle : open(wand)) {
            remaining = handle.insert(remaining);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    /**
     * How many items equivalent to {@code key} all bound containers hold together.
     */
    public static int countStored(ItemStack wand, MaterialKey key) {
        int total = 0;
        for (Handle handle : open(wand)) {
            total = SaturatedAmounts.add(total, handle.count(key));
        }
        return total;
    }

    /**
     * Takes items out of the bound containers, the first one first.
     *
     * @return the amount that could not be taken
     */
    public static int take(ItemStack wand, MaterialKey key, int amount) {
        if (amount <= 0) {
            return amount;
        }
        int remaining = amount;
        for (Handle handle : open(wand)) {
            remaining = handle.take(key, remaining);
            if (remaining <= 0) {
                return 0;
            }
        }
        return remaining;
    }

    /**
     * How much of that fluid all bound containers can still take together.
     */
    public static int fluidCapacity(ItemStack wand, FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return 0;
        }
        int total = 0;
        for (IFluidHandler handler : fluidHandlers(wand)) {
            total = SaturatedAmounts.add(total, capacityFor(handler, fluid));
        }
        return total;
    }

    /**
     * How much of {@code fluid} one handler can still take.
     * <p>
     * The previous probe ({@code handler.fill(fluid.copy(), false)}) is capped by the amount passed
     * in, which is one bucket for a source block, so a tank with a lot of free space still reported
     * only one bucket and the digging core cleared one water block at a time. Reading the tank
     * properties reports the real free space instead.
     */
    private static int capacityFor(IFluidHandler handler, FluidStack fluid) {
        IFluidTankProperties[] tanks;
        try {
            tanks = handler.getTankProperties();
        } catch (RuntimeException exception) {
            tanks = null;
        }
        if (tanks == null || tanks.length == 0) {
            // No readable tank properties: fall back to a probe with a very large amount.
            try {
                return Math.max(0, handler.fill(new FluidStack(fluid.getFluid(), Integer.MAX_VALUE), false));
            } catch (RuntimeException exception) {
                return 0;
            }
        }
        int total = 0;
        for (IFluidTankProperties tank : tanks) {
            try {
                FluidStack contents = tank.getContents();
                int free;
                if (contents == null || contents.amount <= 0) {
                    free = tank.getCapacity();
                } else if (contents.isFluidEqual(fluid)) {
                    free = Math.max(0, tank.getCapacity() - contents.amount);
                } else {
                    free = 0;
                }
                total = SaturatedAmounts.add(total, free);
            } catch (RuntimeException exception) {
                // A tank that cannot be asked is skipped.
            }
        }
        return total;
    }

    /**
     * How much of {@code fluid} all bound containers currently hold together.
     */
    public static int countStoredFluid(ItemStack wand, FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return 0;
        }
        int total = 0;
        for (IFluidHandler handler : fluidHandlers(wand)) {
            total = SaturatedAmounts.add(total, countFluid(handler, fluid));
        }
        return total;
    }

    private static int countFluid(IFluidHandler handler, FluidStack fluid) {
        try {
            int total = 0;
            for (IFluidTankProperties tank : handler.getTankProperties()) {
                FluidStack contents = tank.getContents();
                if (contents != null && contents.isFluidEqual(fluid)) {
                    total = SaturatedAmounts.add(total, contents.amount);
                }
            }
            return total;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /**
     * Fills fluid into the bound containers, the first one first.
     *
     * @return the amount that was stored, in mB
     */
    public static int fill(ItemStack wand, FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }
        int stored = 0;
        for (IFluidHandler handler : fluidHandlers(wand)) {
            int remaining = fluid.amount - stored;
            if (remaining <= 0) {
                break;
            }
            try {
                stored += Math.max(0, Math.min(remaining, handler.fill(new FluidStack(fluid.getFluid(), remaining), true)));
            } catch (RuntimeException exception) {
                // Whatever the next container takes is still stored.
            }
        }
        return stored;
    }

    /**
     * Takes fluid out of the bound containers, the first one first.
     *
     * @return the amount that could not be taken, in mB
     */
    public static int drain(ItemStack wand, FluidStack fluid, int amount) {
        if (fluid == null || amount <= 0) {
            return Math.max(0, amount);
        }
        int remaining = amount;
        for (IFluidHandler handler : fluidHandlers(wand)) {
            if (remaining <= 0) {
                break;
            }
            try {
                FluidStack drained = handler.drain(new FluidStack(fluid.getFluid(), remaining), true);
                if (drained != null && drained.amount > 0) {
                    remaining -= Math.min(remaining, drained.amount);
                }
            } catch (RuntimeException exception) {
                // Try the next container.
            }
        }
        return remaining;
    }

    /**
     * @return the live handles of the bound containers in the order they are used, skipping the ones
     * that are gone or whose chunk is not loaded
     */
    public static List<Handle> open(ItemStack wand) {
        List<Handle> handles = new ArrayList<>();
        for (BoundContainerSourceFactory.Bound bound : BoundContainerSourceFactory.readBindings(wand)) {
            IItemHandler handler = capabilityAt(bound, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (handler != null) {
                handles.add(new Handle(handler));
            }
        }
        return handles;
    }

    private static List<IFluidHandler> fluidHandlers(ItemStack wand) {
        List<IFluidHandler> handlers = new ArrayList<>();
        for (BoundContainerSourceFactory.Bound bound : BoundContainerSourceFactory.readBindings(wand)) {
            IFluidHandler handler = fluidCapabilityAt(bound);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    @Nullable
    private static <T> T capabilityAt(BoundContainerSourceFactory.Bound bound, Capability<T> capability) {
        TileEntity tile = tileAt(bound);
        if (tile == null) {
            return null;
        }
        try {
            return tile.getCapability(capability, null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static TileEntity tileAt(BoundContainerSourceFactory.Bound bound) {
        try {
            World world = DimensionManager.getWorld(bound.getDimension());
            if (world == null || world.isRemote || !world.isBlockLoaded(bound.getPos())) {
                return null;
            }
            TileEntity tile = world.getTileEntity(bound.getPos());
            return tile == null || tile.isInvalid() ? null : tile;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Returns a fluid handler for one bound container.
     * <p>
     * Mekanism's {@code FluidHandlerWrapper} refuses to fill or drain when the capability was asked
     * for with a {@code null} side, and its {@code DOWN} side blocks draining while the tank is
     * active. A concrete side is therefore preferred over the generic one: {@code UP} works for plain
     * fluid tanks and lets a Mekanism tank both fill and drain, so it is tried first, then the other
     * sides, and only then the {@code null} side for containers that only expose one there.
     */
    @Nullable
    private static IFluidHandler fluidCapabilityAt(BoundContainerSourceFactory.Bound bound) {
        TileEntity tile = tileAt(bound);
        if (tile == null) {
            return null;
        }
        IFluidHandler handler = fluidHandlerAt(tile, EnumFacing.UP);
        if (handler != null) {
            return handler;
        }
        for (EnumFacing facing : EnumFacing.values()) {
            if (facing == EnumFacing.UP) {
                continue;
            }
            handler = fluidHandlerAt(tile, facing);
            if (handler != null) {
                return handler;
            }
        }
        return fluidHandlerAt(tile, null);
    }

    @Nullable
    private static IFluidHandler fluidHandlerAt(TileEntity tile, @Nullable EnumFacing side) {
        try {
            return tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Live view of one bound container.
     */
    public static final class Handle {
        private final IItemHandler handler;

        private Handle(IItemHandler handler) {
            this.handler = handler;
        }

        /**
         * @return the part that did not fit
         */
        public ItemStack insert(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < slots() && !remaining.isEmpty(); slot++) {
                try {
                    ItemStack next = handler.insertItem(slot, remaining, false);
                    remaining = next == null ? remaining : next;
                } catch (RuntimeException exception) {
                    break;
                }
            }
            return remaining;
        }

        public int count(MaterialKey key) {
            int total = 0;
            for (int slot = 0; slot < slots(); slot++) {
                try {
                    ItemStack current = handler.getStackInSlot(slot);
                    if (current != null && key.matches(current)) {
                        total = SaturatedAmounts.add(total, current.getCount());
                    }
                } catch (RuntimeException exception) {
                    break;
                }
            }
            return total;
        }

        /**
         * @return the amount that could not be taken
         */
        public int take(MaterialKey key, int amount) {
            if (amount <= 0) {
                return amount;
            }
            int remaining = amount;
            for (int slot = 0; slot < slots() && remaining > 0; slot++) {
                try {
                    ItemStack current = handler.getStackInSlot(slot);
                    if (current == null || !key.matches(current)) {
                        continue;
                    }
                    ItemStack extracted = handler.extractItem(slot, remaining, false);
                    if (extracted != null && !extracted.isEmpty()) {
                        remaining -= extracted.getCount();
                    }
                } catch (RuntimeException exception) {
                    break;
                }
            }
            return remaining;
        }

        private int slots() {
            try {
                return Math.max(0, handler.getSlots());
            } catch (RuntimeException exception) {
                return 0;
            }
        }
    }
}
