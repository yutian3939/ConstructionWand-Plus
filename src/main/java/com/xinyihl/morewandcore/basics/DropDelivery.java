package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Routes harvested items to their configured destination: the player inventory, the wand's material
 * core or the ground, honouring the priority order of {@link DropDestination}. Shared by the digging
 * core and the slay core, which both keep the drops of what they remove.
 */
public final class DropDelivery {
    private DropDelivery() {
    }

    public static void deliver(World world, EntityPlayer player, BlockPos pos, ItemStack stack, DropDestination destination, @Nullable IWandCore materialCore, @Nullable ItemStack wand, @Nullable List<ItemStack> delivered) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (destination == DropDestination.GROUND) {
            Block.spawnAsEntity(world, pos, stack.copy());
            if (delivered != null) {
                delivered.add(stack.copy());
            }
            return;
        }

        ItemStack remaining = stack.copy();
        if (destination == DropDestination.MATERIAL) {
            remaining = moveToMaterial(materialCore, wand, player, remaining, delivered);
            remaining = moveToInventory(player, remaining, delivered);
        } else {
            remaining = moveToInventory(player, remaining, delivered);
            remaining = moveToMaterial(materialCore, wand, player, remaining, delivered);
        }
        if (!remaining.isEmpty()) {
            Block.spawnAsEntity(world, pos, remaining);
            if (delivered != null) {
                delivered.add(remaining.copy());
            }
        }
    }

    private static ItemStack moveToInventory(EntityPlayer player, ItemStack stack, @Nullable List<ItemStack> delivered) {
        if (stack.isEmpty()) {
            return stack;
        }
        int stored = insertIntoInventory(player, stack);
        record(delivered, stack, stored);
        ItemStack leftover = stack.copy();
        leftover.setCount(stack.getCount() - stored);
        return leftover;
    }

    private static ItemStack moveToMaterial(@Nullable IWandCore materialCore, @Nullable ItemStack wand, EntityPlayer player, ItemStack stack, @Nullable List<ItemStack> delivered) {
        if (stack.isEmpty() || materialCore == null || wand == null) {
            return stack;
        }
        ItemStack leftover;
        try {
            leftover = materialCore.deposit(player, wand, stack);
        } catch (RuntimeException exception) {
            return stack;
        }
        if (leftover == null) {
            leftover = ItemStack.EMPTY;
        }
        record(delivered, stack, stack.getCount() - leftover.getCount());
        return leftover;
    }

    /**
     * @return how much really landed in the inventory. The amount is measured instead of trusting
     * {@link net.minecraft.inventory.InventoryPlayer#addItemStackToInventory}, which silently
     * destroys the stack and still reports success when a creative player has no room for it.
     */
    private static int insertIntoInventory(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int before = DropReclaim.countInInventory(player, stack);
        player.inventory.addItemStackToInventory(stack.copy());
        int after = DropReclaim.countInInventory(player, stack);
        return Math.max(0, Math.min(stack.getCount(), after - before));
    }

    private static void record(@Nullable List<ItemStack> delivered, ItemStack stack, int amount) {
        if (delivered == null || amount <= 0) {
            return;
        }
        ItemStack record = stack.copy();
        record.setCount(amount);
        delivered.add(record);
    }
}
