package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.material.MaterialKey;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes back the drops handed out by one digging operation.
 * <p>
 * Only stores the player already has are used: the player inventory and, when the wand has a
 * material core, whatever that core holds. Items lying on the ground are left alone. The removal is
 * all or nothing, so when the items are not available the undo can be refused and retried later.
 * Destruction operations deliver nothing, so their undo never takes anything back.
 */
public final class DropReclaim {
    private DropReclaim() {
    }

    /**
     * Removes the whole delivered set from the player inventory and the material core.
     *
     * @return {@code null} when everything was taken back, otherwise the first stack that is not
     * available in a sufficient amount, carrying the missing count.
     */
    @Nullable
    public static ItemStack reclaim(@Nullable EntityPlayer player, @Nullable IWandCore materialCore, @Nullable ItemStack wand, DropDestination priority, List<ItemStack> delivered) {
        List<ItemStack> required = nonEmptyCopies(delivered);
        if (required.isEmpty()) {
            return null;
        }
        boolean hasMaterialStore = materialCore != null && wand != null;
        if (player == null && !hasMaterialStore) {
            // Nowhere to take the items from, report them as missing instead of silently succeeding.
            return required.get(0);
        }

        Map<MaterialKey, Integer> needed = new LinkedHashMap<>();
        Map<MaterialKey, ItemStack> samples = new HashMap<>();
        for (ItemStack stack : required) {
            MaterialKey key = MaterialKey.of(stack);
            Integer current = needed.get(key);
            needed.put(key, current == null ? stack.getCount() : current + stack.getCount());
            if (!samples.containsKey(key)) {
                samples.put(key, stack);
            }
        }

        Map<MaterialKey, Integer> available = new HashMap<>();
        if (player != null) {
            countInventory(available, player);
        }
        if (hasMaterialStore) {
            for (MaterialKey key : needed.keySet()) {
                int stored = Math.max(0, countStored(materialCore, player, wand, samples.get(key)));
                if (stored > 0) {
                    Integer current = available.get(key);
                    available.put(key, current == null ? stored : current + stored);
                }
            }
        }

        for (Map.Entry<MaterialKey, Integer> entry : needed.entrySet()) {
            Integer counted = available.get(entry.getKey());
            int amount = counted == null ? 0 : counted;
            if (amount < entry.getValue()) {
                return entry.getKey().createStack(entry.getValue() - amount);
            }
        }

        boolean materialFirst = priority == DropDestination.MATERIAL;
        for (Map.Entry<MaterialKey, Integer> entry : needed.entrySet()) {
            ItemStack sample = samples.get(entry.getKey());
            int remaining = entry.getValue();
            if (materialFirst) {
                remaining = withdraw(materialCore, player, wand, sample, remaining);
                if (remaining > 0 && player != null) {
                    removeFromInventory(player, entry.getKey(), remaining);
                }
            } else {
                if (player != null) {
                    remaining = removeFromInventory(player, entry.getKey(), remaining);
                }
                withdraw(materialCore, player, wand, sample, remaining);
            }
        }

        if (player != null) {
            markInventoryDirty(player);
        }
        return null;
    }

    /**
     * The first stack of the delivered set that is not currently available in the player inventory and
     * the material core, or {@code null} when everything is there. Does not take anything, so it can be
     * used to pre-check an undo and to report what a refused undo is missing.
     */
    @Nullable
    public static ItemStack missingReclaim(@Nullable EntityPlayer player, @Nullable IWandCore materialCore, @Nullable ItemStack wand, List<ItemStack> delivered) {
        List<ItemStack> required = nonEmptyCopies(delivered);
        if (required.isEmpty()) {
            return null;
        }
        boolean hasMaterialStore = materialCore != null && wand != null;
        if (player == null && !hasMaterialStore) {
            return required.get(0);
        }

        Map<MaterialKey, Integer> needed = new LinkedHashMap<>();
        Map<MaterialKey, ItemStack> samples = new HashMap<>();
        for (ItemStack stack : required) {
            MaterialKey key = MaterialKey.of(stack);
            Integer current = needed.get(key);
            needed.put(key, current == null ? stack.getCount() : current + stack.getCount());
            if (!samples.containsKey(key)) {
                samples.put(key, stack);
            }
        }

        Map<MaterialKey, Integer> available = new HashMap<>();
        if (player != null) {
            countInventory(available, player);
        }
        if (hasMaterialStore) {
            for (MaterialKey key : needed.keySet()) {
                int stored = Math.max(0, countStored(materialCore, player, wand, samples.get(key)));
                if (stored > 0) {
                    Integer current = available.get(key);
                    available.put(key, current == null ? stored : current + stored);
                }
            }
        }

        for (Map.Entry<MaterialKey, Integer> entry : needed.entrySet()) {
            Integer counted = available.get(entry.getKey());
            int amount = counted == null ? 0 : counted;
            if (amount < entry.getValue()) {
                return entry.getKey().createStack(entry.getValue() - amount);
            }
        }
        return null;
    }

    /**
     * Counts how many items equivalent to {@code template} the player currently carries.
     */
    public static int countInInventory(@Nullable EntityPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) {
            return 0;
        }
        MaterialKey key = MaterialKey.of(template);
        return countIn(player.inventory.mainInventory, key) + countIn(player.inventory.offHandInventory, key);
    }

    private static int countStored(@Nullable IWandCore materialCore, @Nullable EntityPlayer player, ItemStack wand, ItemStack sample) {
        if (materialCore == null || sample == null) {
            return 0;
        }
        try {
            return materialCore.countStored(player, wand, sample);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static int withdraw(@Nullable IWandCore materialCore, @Nullable EntityPlayer player, @Nullable ItemStack wand, ItemStack sample, int amount) {
        if (amount <= 0 || materialCore == null || wand == null || sample == null) {
            return amount;
        }
        try {
            int remaining = materialCore.withdraw(player, wand, sample, amount);
            return remaining < 0 || remaining > amount ? amount : remaining;
        } catch (RuntimeException exception) {
            return amount;
        }
    }

    private static void countInventory(Map<MaterialKey, Integer> counts, EntityPlayer player) {
        countInto(counts, player.inventory.mainInventory);
        countInto(counts, player.inventory.offHandInventory);
    }

    private static void countInto(Map<MaterialKey, Integer> counts, NonNullList<ItemStack> slots) {
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                continue;
            }
            MaterialKey key = MaterialKey.of(stack);
            Integer current = counts.get(key);
            counts.put(key, current == null ? stack.getCount() : current + stack.getCount());
        }
    }

    private static int countIn(NonNullList<ItemStack> slots, MaterialKey key) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && key.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int removeFromInventory(EntityPlayer player, MaterialKey key, int amount) {
        int remaining = removeFrom(player.inventory.mainInventory, key, amount);
        return removeFrom(player.inventory.offHandInventory, key, remaining);
    }

    private static int removeFrom(NonNullList<ItemStack> slots, MaterialKey key, int amount) {
        int remaining = amount;
        for (int index = 0; index < slots.size() && remaining > 0; index++) {
            ItemStack slot = slots.get(index);
            if (slot.isEmpty() || !key.matches(slot)) {
                continue;
            }
            int removed = Math.min(remaining, slot.getCount());
            slot.shrink(removed);
            remaining -= removed;
            if (slot.isEmpty()) {
                slots.set(index, ItemStack.EMPTY);
            }
        }
        return remaining;
    }

    private static void markInventoryDirty(EntityPlayer player) {
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        }
    }

    private static List<ItemStack> nonEmptyCopies(@Nullable List<ItemStack> delivered) {
        List<ItemStack> required = new ArrayList<>();
        if (delivered == null) {
            return required;
        }
        for (ItemStack stack : delivered) {
            if (stack != null && !stack.isEmpty()) {
                required.add(stack.copy());
            }
        }
        return required;
    }
}
