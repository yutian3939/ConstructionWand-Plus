package com.xinyihl.constructionwandlegacy.material.source;

import com.xinyihl.constructionwandlegacy.material.MaterialKey;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public final class InventoryRefunds {
    private InventoryRefunds() {
    }

    /**
     * Delivers as much as possible to the live player's inventory, then to a server-side dropped
     * item. The returned count is never silently discarded and can be retried by a receipt.
     */
    public static int refund(EntityPlayer player, MaterialKey key, int count) {
        if (!isUsable(player) || count <= 0) {
            return Math.max(0, count);
        }

        int remaining = count;
        while (remaining > 0) {
            int maxStackSize = Math.max(1, key.createStack(1).getMaxStackSize());
            int amount = Math.min(remaining, maxStackSize);
            ItemStack part = key.createStack(amount);
            try {
                player.inventory.addItemStackToInventory(part);
            } catch (RuntimeException ignored) {
                // Preserve the current stack count and try the controlled drop below.
            }
            int notInserted = part.isEmpty() ? 0 : Math.max(0, part.getCount());
            if (notInserted > 0) {
                try {
                    EntityItem dropped = player.dropItem(part, false);
                    if (dropped != null) {
                        notInserted = 0;
                    }
                } catch (RuntimeException ignored) {
                    // Keep notInserted retryable.
                }
            }
            int delivered = amount - notInserted;
            remaining -= Math.max(0, delivered);
            if (delivered <= 0) {
                break;
            }
        }
        try {
            player.inventory.markDirty();
        } catch (RuntimeException ignored) {
            // A detached inventory remains represented by the returned remainder.
        }
        return remaining;
    }

    public static boolean isUsable(EntityPlayer player) {
        return player != null && !player.isDead && player.world != null && !player.world.isRemote;
    }
}
