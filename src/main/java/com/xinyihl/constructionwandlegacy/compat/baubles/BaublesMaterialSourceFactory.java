package com.xinyihl.constructionwandlegacy.compat.baubles;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.xinyihl.constructionwandlegacy.material.*;
import com.xinyihl.constructionwandlegacy.material.source.CapturedEndpointIdentity;
import com.xinyihl.constructionwandlegacy.material.source.InventoryRefunds;
import com.xinyihl.constructionwandlegacy.material.source.PortableContainerAccess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BaublesMaterialSourceFactory implements MaterialSourceFactory {
    @Override
    @Optional.Method(modid = "baubles")
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        return handler == null ? null : new BaublesMaterialSource(player, handler);
    }

    private interface Endpoint {
        int extract(MaterialKey key, int count);

        int refund(EntityPlayer player, MaterialKey key, int count);
    }

    private static final class BaublesMaterialSource implements MaterialSource {
        private static final String ID = "baubles";

        private final EntityPlayer player;
        private final IBaublesItemHandler handler;
        private final Map<MaterialKey, List<Endpoint>> endpointsByKey = new LinkedHashMap<>();

        private BaublesMaterialSource(EntityPlayer player, IBaublesItemHandler handler) {
            this.player = player;
            this.handler = handler;
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        @Optional.Method(modid = "baubles")
        public void enumerate(MaterialCollector collector) {
            endpointsByKey.clear();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getCount() > 0) {
                    MaterialKey key = MaterialKey.of(stack);
                    endpointsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new BaubleSlotEndpoint(player, handler, slot));
                    collector.accept(key, stack.getCount());
                }

                PortableContainerAccess.ContainerAccess access = PortableContainerAccess.open(stack);
                if (access == null) {
                    continue;
                }
                for (int innerSlot = 0; innerSlot < access.getSlots(); innerSlot++) {
                    ItemStack innerStack = access.getStackInSlot(innerSlot);
                    if (innerStack.isEmpty() || innerStack.getCount() <= 0) {
                        continue;
                    }
                    MaterialKey key = MaterialKey.of(innerStack);
                    endpointsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new ContainerEndpoint(player, handler, slot, stack, access, innerSlot));
                    collector.accept(key, innerStack.getCount());
                }
            }
        }

        @Override
        @Optional.Method(modid = "baubles")
        public MaterialReceipt extract(MaterialKey key, int count) {
            List<Endpoint> endpoints = endpointsByKey.get(key);
            if (count <= 0 || endpoints == null) {
                return MaterialReceipt.empty();
            }
            int remaining = count;
            List<MaterialReceipt> receipts = new ArrayList<>();
            for (Endpoint endpoint : endpoints) {
                int extracted;
                try {
                    extracted = endpoint.extract(key, remaining);
                } catch (RuntimeException exception) {
                    break;
                }
                if (extracted > 0) {
                    receipts.add(MaterialReceipt.of(ID, key, extracted, (refundKey, refundAmount) -> endpoint.refund(player, refundKey, refundAmount)));
                    remaining -= extracted;
                }
                if (remaining == 0) {
                    break;
                }
            }
            return MaterialReceipt.combine(receipts);
        }
    }

    private static final class BaubleSlotEndpoint implements Endpoint {
        private final IBaublesItemHandler handler;
        private final int slot;
        private final EntityPlayer player;

        private BaubleSlotEndpoint(EntityPlayer player, IBaublesItemHandler handler, int slot) {
            this.player = player;
            this.handler = handler;
            this.slot = slot;
        }

        @Override
        public int extract(MaterialKey key, int count) {
            ItemStack current;
            try {
                current = handler.getStackInSlot(slot);
            } catch (RuntimeException exception) {
                return 0;
            }
            if (!isValid() || !key.matches(current)) {
                return 0;
            }
            ItemStack extracted;
            try {
                extracted = handler.extractItem(slot, count, false);
            } catch (RuntimeException exception) {
                return 0;
            }
            if (!extracted.isEmpty()) {
                try {
                    handler.setChanged(slot, true);
                } catch (RuntimeException ignored) {
                }
            }
            return extracted.getCount();
        }

        @Override
        public int refund(EntityPlayer player, MaterialKey key, int count) {
            if (!isValid()) {
                return InventoryRefunds.refund(player, key, count);
            }
            ItemStack remaining;
            try {
                remaining = handler.insertItem(slot, key.createStack(count), false);
            } catch (RuntimeException exception) {
                return InventoryRefunds.refund(player, key, count);
            }
            if (!remaining.isEmpty()) {
                for (int index = 0; index < handler.getSlots() && !remaining.isEmpty(); index++) {
                    try {
                        ItemStack next = handler.insertItem(index, remaining, false);
                        remaining = next == null ? remaining : next;
                    } catch (RuntimeException ignored) {
                        break;
                    }
                }
            }
            try {
                handler.setChanged(slot, true);
            } catch (RuntimeException ignored) {
            }
            return remaining.isEmpty() ? 0 : InventoryRefunds.refund(player, key, remaining.getCount());
        }

        @Optional.Method(modid = "baubles")
        private boolean isValid() {
            try {
                return BaublesApi.getBaublesHandler(player) == handler && !player.isDead;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    private static final class ContainerEndpoint implements Endpoint {
        private final EntityPlayer player;
        private final PortableContainerAccess.ContainerAccess access;
        private final IBaublesItemHandler parent;
        private final int parentSlot;
        private final ItemStack owner;
        private final int slot;

        private ContainerEndpoint(EntityPlayer player, IBaublesItemHandler parent, int parentSlot, ItemStack owner, PortableContainerAccess.ContainerAccess access, int slot) {
            this.player = player;
            this.parent = parent;
            this.parentSlot = parentSlot;
            this.owner = owner;
            this.access = access;
            this.slot = slot;
        }

        @Override
        public int extract(MaterialKey key, int count) {
            if (!isValid() || !key.matches(access.getStackInSlot(slot))) {
                return 0;
            }
            ItemStack extracted = access.extractItem(slot, count);
            if (!access.flush()) {
                return 0;
            }
            return extracted.getCount();
        }

        @Override
        public int refund(EntityPlayer player, MaterialKey key, int count) {
            if (!isValid()) {
                return InventoryRefunds.refund(player, key, count);
            }
            ItemStack remaining = access.insert(key.createStack(count));
            if (!access.flush()) {
                return InventoryRefunds.refund(player, key, count);
            }
            return remaining.isEmpty() ? 0 : InventoryRefunds.refund(player, key, remaining.getCount());
        }

        @Optional.Method(modid = "baubles")
        private boolean isValid() {
            if (!InventoryRefunds.isUsable(player)) {
                return false;
            }
            try {
                return CapturedEndpointIdentity.matches(parent, BaublesApi.getBaublesHandler(player), owner, parent.getStackInSlot(parentSlot)) && access.isValid(owner);
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }
}
