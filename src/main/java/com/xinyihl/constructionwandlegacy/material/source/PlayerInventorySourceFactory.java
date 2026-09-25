package com.xinyihl.constructionwandlegacy.material.source;

import com.xinyihl.constructionwandlegacy.material.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerInventorySourceFactory implements MaterialSourceFactory {
    @Override
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        return new PlayerInventorySource(player);
    }

    private static final class PlayerInventorySource implements MaterialSource {
        private static final String ID = "player_inventory";

        private final EntityPlayer player;
        private final Map<MaterialKey, List<Slot>> slotsByKey = new LinkedHashMap<>();

        private PlayerInventorySource(EntityPlayer player) {
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
                add(new Slot(main, index), collector);
            }
            for (int index = 0; index < Math.min(9, main.size()); index++) {
                add(new Slot(main, index), collector);
            }
            NonNullList<ItemStack> offhand = player.inventory.offHandInventory;
            for (int index = 0; index < offhand.size(); index++) {
                add(new Slot(offhand, index), collector);
            }
        }

        private void add(Slot slot, MaterialCollector collector) {
            ItemStack stack = slot.get();
            if (stack.isEmpty() || stack.getCount() <= 0) {
                return;
            }
            MaterialKey key = MaterialKey.of(stack);
            slotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
            collector.accept(key, stack.getCount());
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
            List<Taken> taken = new ArrayList<>();
            for (Slot slot : slots) {
                ItemStack stack = slot.get();
                if (!key.matches(stack)) {
                    continue;
                }
                int amount = Math.min(remaining, stack.getCount());
                if (amount <= 0) {
                    continue;
                }
                stack.shrink(amount);
                taken.add(new Taken(slot, amount));
                remaining -= amount;
                if (remaining == 0) {
                    break;
                }
            }
            try {
                player.inventory.markDirty();
            } catch (RuntimeException ignored) {
            }

            int extracted = count - remaining;
            return MaterialReceipt.of(ID, key, extracted, (refundKey, refundCount) -> refund(refundKey, refundCount, taken));
        }

        private int refund(MaterialKey key, int requested, List<Taken> taken) {
            if (!InventoryRefunds.isUsable(player)) {
                return requested;
            }
            int remaining = requested;
            for (int index = taken.size() - 1; index >= 0; index--) {
                if (remaining <= 0) {
                    break;
                }
                Taken part = taken.get(index);
                ItemStack current = part.slot.get();
                int amount = Math.min(remaining, part.count);
                if (current.isEmpty()) {
                    part.slot.set(key.createStack(amount));
                    remaining -= amount;
                } else if (key.matches(current)) {
                    int accepted = Math.min(amount, current.getMaxStackSize() - current.getCount());
                    if (accepted > 0) {
                        current.grow(accepted);
                    }
                    remaining -= accepted;
                }
            }
            if (remaining > 0) {
                remaining = InventoryRefunds.refund(player, key, remaining);
            } else {
                try {
                    player.inventory.markDirty();
                } catch (RuntimeException ignored) {
                    // The receipt retains the amount if the inventory detached mid-refund.
                }
            }
            return remaining;
        }
    }

    private static final class Slot {
        private final NonNullList<ItemStack> inventory;
        private final int index;

        private Slot(NonNullList<ItemStack> inventory, int index) {
            this.inventory = inventory;
            this.index = index;
        }

        private ItemStack get() {
            return inventory.get(index);
        }

        private void set(ItemStack stack) {
            inventory.set(index, stack);
        }
    }

    private static final class Taken {
        private final Slot slot;
        private final int count;

        private Taken(Slot slot, int count) {
            this.slot = slot;
            this.count = count;
        }
    }
}
