package com.xinyihl.constructionwandlegacy.compat.ae2;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.tile.networking.TileController;
import appeng.util.item.AEItemStack;
import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.material.*;
import com.xinyihl.constructionwandlegacy.material.source.InventoryRefunds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;

public final class AE2MaterialSourceFactory implements MaterialSourceFactory {
    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private static MaterialSource createSource(EntityPlayer player, World world, BlockPos pos, TileController controller, IGrid grid, IActionSource actionSource) {
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        return storage == null ? null : new AE2MaterialSource(player, world, pos, controller, grid, storage, actionSource);
    }

    @Nullable
    @Override
    @Optional.Method(modid = "appliedenergistics2")
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        AE2Compat.Binding binding = AE2Compat.readBinding(wand);
        if (binding == null) {
            return null;
        }
        BlockPos boundPos = binding.getPosition();
        World boundWorld = DimensionManager.getWorld(binding.getDimension());
        if (boundWorld == null || !boundWorld.isBlockLoaded(boundPos)) {
            return null;
        }
        TileEntity tile = boundWorld.getTileEntity(boundPos);
        if (!(tile instanceof TileController)) {
            return null;
        }
        TileController controller = (TileController) tile;
        try {
            ISecurityGrid security = controller.getProxy().getSecurity();
            IGridNode node = controller.getGridNode(null);
            if (node == null || !security.hasPermission(player, SecurityPermissions.EXTRACT)) {
                return null;
            }
            return createSource(player, boundWorld, boundPos, controller, node.getGrid(), new MachineSource(controller));
        } catch (GridAccessException exception) {
            if (ConstructionWandLegacy.LOGGER != null) {
                ConstructionWandLegacy.LOGGER.debug("Unable to resolve bound AE2 grid", exception);
            }
            return null;
        }
    }

    /**
     * Resolves the bound ME network for the item and fluid hand-in/hand-out of a material core.
     */
    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private static GridRef resolveGrid(ItemStack wand, EntityPlayer player, SecurityPermissions permission) {
        if (player == null || wand == null || wand.isEmpty()) {
            return null;
        }
        AE2Compat.Binding binding = AE2Compat.readBinding(wand);
        if (binding == null) {
            return null;
        }
        BlockPos boundPos = binding.getPosition();
        World boundWorld = DimensionManager.getWorld(binding.getDimension());
        if (boundWorld == null || boundWorld.isRemote || !boundWorld.isBlockLoaded(boundPos)) {
            return null;
        }
        TileEntity tile = boundWorld.getTileEntity(boundPos);
        if (!(tile instanceof TileController)) {
            return null;
        }
        TileController controller = (TileController) tile;
        try {
            ISecurityGrid security = controller.getProxy().getSecurity();
            IGridNode node = controller.getGridNode(null);
            if (node == null || !security.hasPermission(player, permission)) {
                return null;
            }
            return new GridRef(node.getGrid(), new MachineSource(controller));
        } catch (GridAccessException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private static Network resolveNetwork(ItemStack wand, EntityPlayer player, SecurityPermissions permission) {
        GridRef grid = resolveGrid(wand, player, permission);
        if (grid == null) {
            return null;
        }
        IMEMonitor<IAEItemStack> storage = grid.items();
        return storage == null ? null : new Network(storage, grid.actionSource);
    }

    /**
     * @return the part that did not fit into the network
     */
    @Optional.Method(modid = "appliedenergistics2")
    public static ItemStack deposit(ItemStack wand, EntityPlayer player, ItemStack stack) {
        Network network = resolveNetwork(wand, player, SecurityPermissions.INJECT);
        return network == null ? stack : network.inject(stack);
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static int countStored(ItemStack wand, EntityPlayer player, ItemStack template) {
        Network network = resolveNetwork(wand, player, SecurityPermissions.EXTRACT);
        return network == null ? 0 : network.available(template);
    }

    /**
     * @return the amount that could not be taken
     */
    @Optional.Method(modid = "appliedenergistics2")
    public static int withdraw(ItemStack wand, EntityPlayer player, ItemStack template, int amount) {
        Network network = resolveNetwork(wand, player, SecurityPermissions.EXTRACT);
        return network == null ? amount : network.take(template, amount);
    }

    /**
     * How much of that fluid the bound ME network could still take, asked of its fluid storage cells.
     */
    @Optional.Method(modid = "appliedenergistics2")
    public static int fluidCapacity(ItemStack wand, EntityPlayer player, FluidStack fluid) {
        GridRef grid = resolveGrid(wand, player, SecurityPermissions.INJECT);
        if (grid == null || fluid == null || fluid.getFluid() == null) {
            return 0;
        }
        IMEMonitor<IAEFluidStack> storage = grid.fluids();
        if (storage == null) {
            return 0;
        }
        IAEFluidStack request = AEFluidStack.fromFluidStack(new FluidStack(fluid.getFluid(), Integer.MAX_VALUE));
        if (request == null) {
            return 0;
        }
        try {
            IAEFluidStack rejected = storage.injectItems(request, Actionable.SIMULATE, grid.actionSource);
            long rejectedAmount = rejected == null ? 0L : rejected.getStackSize();
            return SaturatedAmounts.fromLong(Math.max(0L, Integer.MAX_VALUE - rejectedAmount));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /**
     * @return the amount that was stored, in mB
     */
    @Optional.Method(modid = "appliedenergistics2")
    public static int depositFluid(ItemStack wand, EntityPlayer player, FluidStack fluid) {
        GridRef grid = resolveGrid(wand, player, SecurityPermissions.INJECT);
        if (grid == null || fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return 0;
        }
        IMEMonitor<IAEFluidStack> storage = grid.fluids();
        if (storage == null) {
            return 0;
        }
        IAEFluidStack request = AEFluidStack.fromFluidStack(new FluidStack(fluid.getFluid(), fluid.amount));
        if (request == null) {
            return 0;
        }
        try {
            IAEFluidStack rejected = storage.injectItems(request, Actionable.MODULATE, grid.actionSource);
            int remaining = rejected == null ? 0 : SaturatedAmounts.fromLong(rejected.getStackSize());
            return Math.max(0, fluid.amount - Math.min(fluid.amount, remaining));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken, in mB
     */
    @Optional.Method(modid = "appliedenergistics2")
    public static int withdrawFluid(ItemStack wand, EntityPlayer player, FluidStack fluid, int amount) {
        if (amount <= 0) {
            return 0;
        }
        GridRef grid = resolveGrid(wand, player, SecurityPermissions.EXTRACT);
        if (grid == null || fluid == null || fluid.getFluid() == null) {
            return amount;
        }
        IMEMonitor<IAEFluidStack> storage = grid.fluids();
        if (storage == null) {
            return amount;
        }
        IAEFluidStack request = AEFluidStack.fromFluidStack(new FluidStack(fluid.getFluid(), amount));
        if (request == null) {
            return amount;
        }
        try {
            IAEFluidStack extracted = storage.extractItems(request, Actionable.MODULATE, grid.actionSource);
            int taken = extracted == null ? 0 : SaturatedAmounts.fromLong(extracted.getStackSize());
            return amount - Math.max(0, Math.min(amount, taken));
        } catch (RuntimeException exception) {
            return amount;
        }
    }

    /**
     * The storage of one bound network, item and fluid cells side by side.
     */
    private static final class GridRef {
        private final IGrid grid;
        private final IActionSource actionSource;

        private GridRef(IGrid grid, IActionSource actionSource) {
            this.grid = grid;
            this.actionSource = actionSource;
        }

        @Nullable
        private IMEMonitor<IAEItemStack> items() {
            try {
                IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
                return storageGrid == null ? null : storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            } catch (RuntimeException exception) {
                return null;
            }
        }

        @Nullable
        private IMEMonitor<IAEFluidStack> fluids() {
            try {
                IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
                return storageGrid == null ? null : storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    private static final class Network {
        private final IMEMonitor<IAEItemStack> storage;
        private final IActionSource actionSource;

        private Network(IMEMonitor<IAEItemStack> storage, IActionSource actionSource) {
            this.storage = storage;
            this.actionSource = actionSource;
        }

        private ItemStack inject(ItemStack stack) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            IAEItemStack request = AEItemStack.fromItemStack(stack.copy());
            if (request == null) {
                return stack;
            }
            request.setStackSize(stack.getCount());
            try {
                IAEItemStack rejected = storage.injectItems(request, Actionable.MODULATE, actionSource);
                int remaining = rejected == null ? 0 : Math.min(stack.getCount(), SaturatedAmounts.fromLong(rejected.getStackSize()));
                if (remaining <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack leftover = stack.copy();
                leftover.setCount(remaining);
                return leftover;
            } catch (RuntimeException exception) {
                return stack;
            }
        }

        private int available(ItemStack template) {
            if (template.isEmpty()) {
                return 0;
            }
            IAEItemStack request = AEItemStack.fromItemStack(template.copy());
            if (request == null) {
                return 0;
            }
            request.setStackSize(Integer.MAX_VALUE);
            try {
                IAEItemStack simulated = storage.extractItems(request, Actionable.SIMULATE, actionSource);
                return simulated == null ? 0 : SaturatedAmounts.fromLong(simulated.getStackSize());
            } catch (RuntimeException exception) {
                return 0;
            }
        }

        private int take(ItemStack template, int amount) {
            if (amount <= 0 || template.isEmpty()) {
                return amount;
            }
            IAEItemStack request = AEItemStack.fromItemStack(template.copy());
            if (request == null) {
                return amount;
            }
            request.setStackSize(amount);
            try {
                IAEItemStack extracted = storage.extractItems(request, Actionable.MODULATE, actionSource);
                int taken = extracted == null ? 0 : Math.min(amount, SaturatedAmounts.fromLong(extracted.getStackSize()));
                return amount - taken;
            } catch (RuntimeException exception) {
                return amount;
            }
        }
    }

    private static final class AE2MaterialSource implements MaterialSource {
        private static final String ID = "ae2";

        private final EntityPlayer player;
        private final World world;
        private final BlockPos pos;
        private final TileController controller;
        private final IGrid grid;
        private final IMEMonitor<IAEItemStack> storage;
        private final IActionSource actionSource;

        private AE2MaterialSource(EntityPlayer player, World world, BlockPos pos, TileController controller, IGrid grid, IMEMonitor<IAEItemStack> storage, IActionSource actionSource) {
            this.player = player;
            this.world = world;
            this.pos = pos;
            this.controller = controller;
            this.grid = grid;
            this.storage = storage;
            this.actionSource = actionSource;
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        @Optional.Method(modid = "appliedenergistics2")
        public void enumerate(MaterialCollector collector) {
            if (!isValidEndpoint(SecurityPermissions.EXTRACT)) {
                return;
            }
            IItemList<IAEItemStack> itemList = storage.getStorageList();
            if (itemList == null) {
                return;
            }
            for (IAEItemStack aeStack : itemList) {
                if (aeStack == null || SaturatedAmounts.fromLong(aeStack.getStackSize()) == 0) {
                    continue;
                }
                ItemStack definition = aeStack.getDefinition();
                if (!definition.isEmpty()) {
                    collector.accept(MaterialKey.of(definition), aeStack.getStackSize());
                }
            }
        }

        @Override
        @Optional.Method(modid = "appliedenergistics2")
        public MaterialReceipt extract(MaterialKey key, int count) {
            if (count <= 0 || !isValidEndpoint(SecurityPermissions.EXTRACT)) {
                return MaterialReceipt.empty();
            }
            IAEItemStack request = AEItemStack.fromItemStack(key.createStack(1));
            if (request == null) {
                return MaterialReceipt.empty();
            }
            request.setStackSize(count);
            IAEItemStack result;
            try {
                result = storage.extractItems(request, Actionable.MODULATE, actionSource);
            } catch (RuntimeException exception) {
                return MaterialReceipt.empty();
            }
            int extracted = result == null ? 0 : Math.min(count, SaturatedAmounts.fromLong(result.getStackSize()));
            return MaterialReceipt.of(ID, key, extracted, this::refund);
        }

        private int refund(MaterialKey key, int count) {
            if (!isValidEndpoint(SecurityPermissions.INJECT)) {
                return InventoryRefunds.refund(player, key, count);
            }
            IAEItemStack request = AEItemStack.fromItemStack(key.createStack(1));
            int remaining = count;
            if (request != null) {
                request.setStackSize(count);
                try {
                    IAEItemStack rejected = storage.injectItems(request, Actionable.MODULATE, actionSource);
                    remaining = rejected == null ? 0 : Math.min(count, SaturatedAmounts.fromLong(rejected.getStackSize()));
                } catch (RuntimeException ignored) {
                    remaining = count;
                }
            }
            return remaining <= 0 ? 0 : InventoryRefunds.refund(player, key, remaining);
        }

        @Optional.Method(modid = "appliedenergistics2")
        private boolean isValidEndpoint(SecurityPermissions permission) {
            if (world == null || world.isRemote || !world.isBlockLoaded(pos) || world.getTileEntity(pos) != controller || controller.isInvalid()) {
                return false;
            }
            try {
                IGridNode node = controller.getGridNode(null);
                if (node == null || node.getGrid() != grid || !controller.getProxy().getSecurity().hasPermission(player, permission)) {
                    return false;
                }
                IStorageGrid currentStorage = grid.getCache(IStorageGrid.class);
                return currentStorage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)) == storage;
            } catch (GridAccessException | RuntimeException exception) {
                return false;
            }
        }
    }
}
