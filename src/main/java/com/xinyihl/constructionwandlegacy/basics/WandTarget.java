package com.xinyihl.constructionwandlegacy.basics;

import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nullable;

/**
 * A held-wand location that can be revalidated when a delayed packet is handled.
 */
public final class WandTarget {
    public static final int OFFHAND_SLOT = 40;

    private final EnumHand hand;
    private final int slot;

    public WandTarget(EnumHand hand, int slot) {
        if (hand == null) {
            throw new IllegalArgumentException("hand must not be null");
        }
        if (hand == EnumHand.MAIN_HAND && (slot < 0 || slot >= 9)) {
            throw new IllegalArgumentException("main-hand slot is out of bounds");
        }
        if (hand == EnumHand.OFF_HAND && slot != OFFHAND_SLOT) {
            throw new IllegalArgumentException("off-hand slot is invalid");
        }
        this.hand = hand;
        this.slot = slot;
    }

    @Nullable
    public static WandTarget locate(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getHeldItemMainhand();
        if (isWand(main)) {
            return new WandTarget(EnumHand.MAIN_HAND, player.inventory.currentItem);
        }
        ItemStack off = player.getHeldItemOffhand();
        return isWand(off) ? new WandTarget(EnumHand.OFF_HAND, OFFHAND_SLOT) : null;
    }

    @Nullable
    public static WandTarget forHand(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null || !isWand(player.getHeldItem(hand))) {
            return null;
        }
        return hand == EnumHand.MAIN_HAND ? new WandTarget(hand, player.inventory.currentItem) : new WandTarget(hand, OFFHAND_SLOT);
    }

    private static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    public ItemStack resolve(EntityPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (hand == EnumHand.MAIN_HAND) {
            if (player.inventory.currentItem != slot) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = player.inventory.getStackInSlot(slot);
            return isWand(stack) && stack == player.getHeldItemMainhand() ? stack : ItemStack.EMPTY;
        }
        ItemStack stack = player.getHeldItemOffhand();
        return isWand(stack) ? stack : ItemStack.EMPTY;
    }

    public EnumHand getHand() {
        return hand;
    }

    public int getSlot() {
        return slot;
    }
}
