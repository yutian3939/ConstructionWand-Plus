package thetadev.constructionwand.plus.basics;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.plus.material.IMaterialCore;
import thetadev.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes back the drops handed out by one digging operation.
 * <p>
 * Only stores the player already has are used: the player inventory and, when the wand has a material
 * core, whatever that core holds. Items lying on the ground are left alone. The removal is all or
 * nothing, so when the items are not available the undo can be refused and retried later.
 * Destruction operations deliver nothing, so their undo never takes anything back.
 */
public final class DropReclaim
{
    private DropReclaim() {
    }

    /**
     * Removes the whole delivered set from the player inventory and the material core.
     *
     * @return {@code null} when everything was taken back, otherwise the first stack that is not
     * available in a sufficient amount, carrying the missing count.
     */
    @Nullable
    public static ItemStack reclaim(@Nullable Player player, @Nullable IMaterialCore materialCore, @Nullable ItemStack wand,
                                    DropDestination priority, List<ItemStack> delivered) {
        Map<ItemStack, Integer> needed = aggregate(delivered);
        if(needed.isEmpty()) return null;

        Map<ItemStack, Integer> available = available(player, materialCore, wand, needed);
        ItemStack missing = firstMissing(needed, available);
        if(missing != null) return missing;

        boolean materialFirst = priority == DropDestination.MATERIAL;
        for(Map.Entry<ItemStack, Integer> entry : needed.entrySet()) {
            ItemStack sample = entry.getKey();
            int remaining = entry.getValue();
            if(materialFirst) {
                remaining = withdraw(materialCore, player, wand, sample, remaining);
                if(remaining > 0 && player != null) removeFromInventory(player, sample, remaining);
            }
            else {
                if(player != null) remaining = removeFromInventory(player, sample, remaining);
                withdraw(materialCore, player, wand, sample, remaining);
            }
        }

        if(player != null) markInventoryDirty(player);
        return null;
    }

    /**
     * The first stack of the delivered set that is not currently available in the player inventory and
     * the material core, or {@code null} when everything is there. Does not take anything, so it can be
     * used to pre-check an undo and to report what a refused undo is missing.
     */
    @Nullable
    public static ItemStack missingReclaim(@Nullable Player player, @Nullable IMaterialCore materialCore, @Nullable ItemStack wand,
                                           List<ItemStack> delivered) {
        Map<ItemStack, Integer> needed = aggregate(delivered);
        if(needed.isEmpty()) return null;
        return firstMissing(needed, available(player, materialCore, wand, needed));
    }

    /**
     * Checks whether an entire operation can take back everything it handed out, and reports what is
     * missing.
     * <p>
     * The snapshots of one operation share the player inventory and the material core, so the amounts
     * are summed per store first: every block on its own can look satisfied while the operation as a
     * whole does not have enough, and the undo would then take material from the first blocks and fail
     * on the rest.
     *
     * @return whether every store can give the whole amount
     */
    public static boolean canReclaim(@Nullable Player player, List<ISnapshot.Reclaim> claims) {
        if(claims == null || claims.isEmpty()) return true;

        Map<IMaterialCore, ISnapshot.Reclaim> aggregated = new LinkedHashMap<>();
        for(ISnapshot.Reclaim claim : claims) {
            ISnapshot.Reclaim existing = aggregated.get(claim.store());
            if(existing == null) {
                aggregated.put(claim.store(), claim);
                continue;
            }
            List<ItemStack> items = new ArrayList<>(existing.items());
            items.addAll(claim.items());
            aggregated.put(claim.store(), new ISnapshot.Reclaim(existing.store(), existing.wand(), items));
        }

        boolean enough = true;
        for(ISnapshot.Reclaim claim : aggregated.values()) {
            ItemStack missing = missingReclaim(player, claim.store(), claim.wand(), claim.items());
            if(missing == null) continue;
            if(player != null) UndoFeedback.report(player, missing);
            enough = false;
        }
        return enough;
    }

    /**
     * Counts how many items equivalent to {@code template} the player currently carries.
     */
    public static int countInInventory(@Nullable Player player, ItemStack template) {
        if(player == null || template == null || template.isEmpty()) return 0;
        return countIn(player.getInventory().items, template) + countIn(player.getInventory().offhand, template);
    }

    private static Map<ItemStack, Integer> aggregate(@Nullable List<ItemStack> delivered) {
        Map<ItemStack, Integer> needed = new LinkedHashMap<>();
        if(delivered == null) return needed;
        for(ItemStack stack : delivered) {
            if(stack == null || stack.isEmpty()) continue;
            boolean merged = false;
            for(Map.Entry<ItemStack, Integer> entry : needed.entrySet()) {
                if(ItemStack.isSameItemSameTags(entry.getKey(), stack)) {
                    entry.setValue(entry.getValue() + stack.getCount());
                    merged = true;
                    break;
                }
            }
            if(!merged) needed.put(stack.copy(), stack.getCount());
        }
        return needed;
    }

    private static Map<ItemStack, Integer> available(@Nullable Player player, @Nullable IMaterialCore materialCore,
                                                     @Nullable ItemStack wand, Map<ItemStack, Integer> needed) {
        Map<ItemStack, Integer> available = new LinkedHashMap<>();
        if(player != null) {
            for(Map.Entry<ItemStack, Integer> entry : needed.entrySet()) {
                int carried = countInInventory(player, entry.getKey());
                if(carried > 0) available.put(entry.getKey(), carried);
            }
        }
        if(materialCore != null && wand != null) {
            for(Map.Entry<ItemStack, Integer> entry : needed.entrySet()) {
                int stored = countStored(materialCore, player, wand, entry.getKey());
                if(stored > 0) available.merge(entry.getKey(), stored, Integer::sum);
            }
        }
        return available;
    }

    @Nullable
    private static ItemStack firstMissing(Map<ItemStack, Integer> needed, Map<ItemStack, Integer> available) {
        for(Map.Entry<ItemStack, Integer> entry : needed.entrySet()) {
            Integer counted = available.get(entry.getKey());
            int amount = counted == null ? 0 : counted;
            if(amount < entry.getValue()) {
                ItemStack missing = entry.getKey().copy();
                missing.setCount(entry.getValue() - amount);
                return missing;
            }
        }
        return null;
    }

    private static int countStored(@Nullable IMaterialCore materialCore, @Nullable Player player, ItemStack wand, ItemStack sample) {
        if(materialCore == null || sample == null) return 0;
        try {
            return Math.max(0, materialCore.countStored(player, wand, sample));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    private static int withdraw(@Nullable IMaterialCore materialCore, @Nullable Player player, @Nullable ItemStack wand,
                                ItemStack sample, int amount) {
        if(amount <= 0 || materialCore == null || wand == null || sample == null) return amount;
        try {
            int remaining = materialCore.withdraw(player, wand, sample, amount);
            return remaining < 0 || remaining > amount ? amount : remaining;
        } catch(RuntimeException exception) {
            return amount;
        }
    }

    private static int countIn(NonNullList<ItemStack> slots, ItemStack template) {
        int total = 0;
        for(ItemStack stack : slots) {
            if(!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) total += stack.getCount();
        }
        return total;
    }

    private static int removeFromInventory(Player player, ItemStack template, int amount) {
        int remaining = removeFrom(player.getInventory().items, template, amount);
        return removeFrom(player.getInventory().offhand, template, remaining);
    }

    private static int removeFrom(NonNullList<ItemStack> slots, ItemStack template, int amount) {
        int remaining = amount;
        for(int index = 0; index < slots.size() && remaining > 0; index++) {
            ItemStack slot = slots.get(index);
            if(slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, template)) continue;
            int removed = Math.min(remaining, slot.getCount());
            slot.shrink(removed);
            remaining -= removed;
            if(slot.isEmpty()) slots.set(index, ItemStack.EMPTY);
        }
        return remaining;
    }

    private static void markInventoryDirty(Player player) {
        player.getInventory().setChanged();
        if(player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Non-empty copies of the delivered set, for callers that only need the payload.
     */
    public static List<ItemStack> copies(@Nullable List<ItemStack> delivered) {
        List<ItemStack> copies = new ArrayList<>();
        if(delivered == null) return copies;
        for(ItemStack stack : delivered) {
            if(stack != null && !stack.isEmpty()) copies.add(stack.copy());
        }
        return copies;
    }
}
