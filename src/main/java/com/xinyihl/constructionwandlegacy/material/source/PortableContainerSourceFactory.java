package com.xinyihl.constructionwandlegacy.material.source;

import com.xinyihl.constructionwandlegacy.material.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PortableContainerSourceFactory implements MaterialSourceFactory {
    @Override
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        return new PortableContainerSource(player);
    }

    private static final class PortableContainerSource implements MaterialSource {
        private static final String ID = "portable_container";

        private final EntityPlayer player;
        private final Map<MaterialKey, List<Slot>> slotsByKey = new LinkedHashMap<>();

        private PortableContainerSource(EntityPlayer player) {
            this.player = player;
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        public void enumerate(MaterialCollector collector) {
            slotsByKey.clear();
            NonNullList<ItemStack> main = player.inventory.mainInventory;
            for (int index = 9; index < main.size(); index++) {
                addContainer(new OwnerSlot(main, index), collector);
            }
            for (int index = 0; index < Math.min(9, main.size()); index++) {
                addContainer(new OwnerSlot(main, index), collector);
            }
            NonNullList<ItemStack> offhand = player.inventory.offHandInventory;
            for (int index = 0; index < offhand.size(); index++) {
                addContainer(new OwnerSlot(offhand, index), collector);
            }
        }

        private void addContainer(OwnerSlot ownerSlot, MaterialCollector collector) {
            ItemStack owner = ownerSlot.get();
            PortableContainerAccess.ContainerAccess access = PortableContainerAccess.open(owner);
            if (access == null) {
                return;
            }
            for (int slotIndex = 0; slotIndex < access.getSlots(); slotIndex++) {
                ItemStack stack = access.getStackInSlot(slotIndex);
                if (stack.isEmpty() || stack.getCount() <= 0) {
                    continue;
                }
                MaterialKey key = MaterialKey.of(stack);
                slotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Slot(ownerSlot, owner, access, slotIndex));
                collector.accept(key, stack.getCount());
            }
        }

        @Override
        public MaterialReceipt extract(MaterialKey key, int count) {
            if (count <= 0) {
                return MaterialReceipt.empty();
            }
            List<Slot> slots = slotsByKey.get(key);
            if (slots == null) {
                return MaterialReceipt.empty();
            }
            int remaining = count;
            List<MaterialReceipt> receipts = new ArrayList<>();
            for (Slot slot : slots) {
                if (!slot.isValid() || !key.matches(slot.access.getStackInSlot(slot.index))) {
                    continue;
                }
                ItemStack extracted = slot.access.extractItem(slot.index, remaining);
                if (!extracted.isEmpty()) {
                    int extractedCount = Math.min(remaining, extracted.getCount());
                    if (!slot.access.flush()) {
                        break;
                    }
                    remaining -= extractedCount;
                    receipts.add(MaterialReceipt.of(ID, key, extractedCount, (refundKey, refundCount) -> refund(slot, refundKey, refundCount)));
                }
                if (remaining == 0) {
                    break;
                }
            }
            try {
                player.inventory.markDirty();
            } catch (RuntimeException ignored) {
            }
            return MaterialReceipt.combine(receipts);
        }

        private int refund(Slot slot, MaterialKey key, int count) {
            if (!slot.isValid()) {
                return InventoryRefunds.refund(player, key, count);
            }
            ItemStack remaining = slot.access.insert(key.createStack(count));
            if (!slot.access.flush()) {
                return InventoryRefunds.refund(player, key, count);
            }
            return remaining.isEmpty() ? 0 : InventoryRefunds.refund(player, key, remaining.getCount());
        }
    }

    private static final class OwnerSlot {
        private final NonNullList<ItemStack> inventory;
        private final int index;

        private OwnerSlot(NonNullList<ItemStack> inventory, int index) {
            this.inventory = inventory;
            this.index = index;
        }

        private ItemStack get() {
            return inventory.get(index);
        }
    }

    private static final class Slot {
        private final OwnerSlot ownerSlot;
        private final ItemStack owner;
        private final PortableContainerAccess.ContainerAccess access;
        private final int index;

        private Slot(OwnerSlot ownerSlot, ItemStack owner, PortableContainerAccess.ContainerAccess access, int index) {
            this.ownerSlot = ownerSlot;
            this.owner = owner;
            this.access = access;
            this.index = index;
        }

        private boolean isValid() {
            return ownerSlot.get() == owner && access.isValid(owner);
        }
    }
}
