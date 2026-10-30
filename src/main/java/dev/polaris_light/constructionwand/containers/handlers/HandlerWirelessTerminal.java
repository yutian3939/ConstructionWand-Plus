package dev.polaris_light.constructionwand.containers.handlers;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.api.IContainerHandler;
import dev.polaris_light.constructionwand.containers.ContainerTrace;

public class HandlerWirelessTerminal implements IContainerHandler {

    @Override
    public boolean matches(Player player, ItemStack itemStack, ItemStack inventoryStack) {
        return inventoryStack != null && inventoryStack.getItem() instanceof WirelessTerminalItem;
    }

    @Override   
    public int getSignature(Player player, ItemStack inventoryStack) {
        if (player instanceof ServerPlayer serverPlayer) {
            MEStorage storage = getStorage(serverPlayer, inventoryStack);
            if (storage != null) {
                return storage.hashCode();
            }
        }
        return -1;
    }

    @Override
    public int countItems(Player player, ContainerTrace trace, ItemStack target, ItemStack terminal) {
        if (player instanceof ServerPlayer serverPlayer) {

            MEStorage storage = getStorage(serverPlayer, terminal);
            if (storage == null) return 0;

            AEItemKey key = AEItemKey.of(target);
            if (key == null) return 0;

            long amount = 0;
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getKey().equals(key)) {
                    amount = entry.getLongValue();
                    break;
                }
            }
            return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        }
        return 0;
    }


    @Override
    public int useItems(Player player, ContainerTrace trace, ItemStack target, ItemStack terminal, int count) {
        if (player instanceof ServerPlayer serverPlayer) {
            WirelessTerminalMenuHost<?> host = getHost(serverPlayer, terminal);
            if (host == null) return count;

            MEStorage storage = host.getInventory();
            if (storage == null) return count;

            AEItemKey key = AEItemKey.of(target);
            if (key == null) return count;

            var source = IActionSource.ofPlayer(serverPlayer);
            long extracted = StorageHelper.poweredExtraction(host, storage, key, count, source, Actionable.MODULATE);
            long remaining = count - extracted;
            return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
        }
        return count;
    }

    private MEStorage getStorage(ServerPlayer player, ItemStack terminal) {
        var host = getHost(player, terminal);
        return host != null ? host.getInventory() : null;
    }

    /**
     * Stores the stack in the network the terminal is linked to. The terminal has to be powered and in
     * range, exactly as it does for taking items out.
     */
    @Override
    public ItemStack insertItems(Player player, ContainerTrace trace, ItemStack terminal, ItemStack stack) {
        if(!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return stack;

        WirelessTerminalMenuHost<?> host = getHost(serverPlayer, terminal);
        if(host == null) return stack;

        MEStorage storage = host.getInventory();
        if(storage == null) return stack;

        AEItemKey key = AEItemKey.of(stack);
        if(key == null) return stack;

        var source = IActionSource.ofPlayer(serverPlayer);
        long inserted = StorageHelper.poweredInsert(host, storage, key, stack.getCount(), source, Actionable.MODULATE);
        if(inserted <= 0) return stack;

        ItemStack leftover = stack.copy();
        leftover.setCount(stack.getCount() - (int) Math.min(inserted, stack.getCount()));
        return leftover;
    }

    @Override
    public int countFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack terminal) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid.isEmpty()) return 0;
        MEStorage storage = getStorage(serverPlayer, terminal);
        return storage == null ? 0 : AeFluidStorage.count(storage, fluid);
    }

    @Override
    public int fluidCapacity(Player player, ContainerTrace trace, FluidStack fluid, ItemStack terminal) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid.isEmpty()) return 0;
        WirelessTerminalMenuHost<?> host = getHost(serverPlayer, terminal);
        MEStorage storage = host == null ? null : host.getInventory();
        return storage == null ? 0 : AeFluidStorage.capacity(host, storage, IActionSource.ofPlayer(serverPlayer), fluid);
    }

    @Override
    public int insertFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack terminal) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid.isEmpty()) return 0;
        WirelessTerminalMenuHost<?> host = getHost(serverPlayer, terminal);
        MEStorage storage = host == null ? null : host.getInventory();
        return storage == null ? 0 : AeFluidStorage.insert(host, storage, IActionSource.ofPlayer(serverPlayer), fluid);
    }

    @Override
    public int useFluid(Player player, ContainerTrace trace, FluidStack fluid, ItemStack terminal, int amount) {
        if(!(player instanceof ServerPlayer serverPlayer) || fluid.isEmpty() || amount <= 0) return Math.max(0, amount);
        WirelessTerminalMenuHost<?> host = getHost(serverPlayer, terminal);
        MEStorage storage = host == null ? null : host.getInventory();
        if(storage == null) return amount;

        int taken = AeFluidStorage.extract(host, storage, IActionSource.ofPlayer(serverPlayer), fluid, amount);
        return Math.max(0, amount - taken);
    }

    private WirelessTerminalMenuHost<?> getHost(ServerPlayer player, ItemStack terminal) {
        if (terminal.getItem() instanceof WirelessTerminalItem wireless) {
            try {
                WirelessTerminalMenuHost<?> host = wireless.getMenuHost(
                    player,
                    MenuLocators.forStack(terminal),
                    null
                );

                // Some AE2-compatible terminals, such as AE2WTLib quantum-linked terminals,
                // populate their actionable node lazily when their inventory is queried.
                host.getInventory();

                if (host.getActionableNode() == null) {
                    player.displayClientMessage(PlayerMessages.OutOfRange.text(), true);
                    return null;
                }

                double power = wireless.getAECurrentPower(terminal);
                if (power <= 0) {
                    player.displayClientMessage(PlayerMessages.DeviceNotPowered.text(), true);
                    return null;
                }

                return host;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
