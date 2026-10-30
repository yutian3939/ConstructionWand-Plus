package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Routes harvested items to their configured destination: the player inventory, the wand's material
 * core or the ground, honouring the priority order of {@link DropDestination}. Shared by the digging
 * core and the slay core, which both keep the drops of what they remove.
 */
public final class DropDelivery
{
    private DropDelivery() {
    }

    public static void deliver(Level level, Player player, BlockPos pos, ItemStack stack, DropDestination destination,
                               @Nullable IMaterialCore materialCore, @Nullable ItemStack wand, @Nullable List<ItemStack> delivered) {
        if(stack == null || stack.isEmpty()) return;

        if(destination == DropDestination.GROUND) {
            Block.popResource(level, pos, stack.copy());
            record(delivered, stack, stack.getCount());
            return;
        }

        ItemStack remaining = stack.copy();
        if(destination == DropDestination.MATERIAL) {
            remaining = moveToMaterial(materialCore, wand, player, remaining, delivered);
            remaining = moveToInventory(player, remaining, delivered);
        }
        else {
            remaining = moveToInventory(player, remaining, delivered);
            remaining = moveToMaterial(materialCore, wand, player, remaining, delivered);
        }
        if(!remaining.isEmpty()) {
            Block.popResource(level, pos, remaining.copy());
            record(delivered, remaining, remaining.getCount());
        }
    }

    private static ItemStack moveToInventory(Player player, ItemStack stack, @Nullable List<ItemStack> delivered) {
        if(stack.isEmpty()) return stack;
        int stored = insertIntoInventory(player, stack);
        record(delivered, stack, stored);
        ItemStack leftover = stack.copy();
        leftover.setCount(stack.getCount() - stored);
        return leftover;
    }

    private static ItemStack moveToMaterial(@Nullable IMaterialCore materialCore, @Nullable ItemStack wand, Player player,
                                            ItemStack stack, @Nullable List<ItemStack> delivered) {
        if(stack.isEmpty() || materialCore == null || wand == null) return stack;
        ItemStack leftover;
        try {
            leftover = materialCore.deposit(player, wand, stack);
        } catch(RuntimeException exception) {
            return stack;
        }
        if(leftover == null) leftover = ItemStack.EMPTY;
        record(delivered, stack, stack.getCount() - leftover.getCount());
        return leftover;
    }

    /**
     * @return how much really landed in the inventory. The amount is measured instead of trusting the
     * inventory insert, which can silently drop the stack and still report success.
     */
    private static int insertIntoInventory(Player player, ItemStack stack) {
        if(stack.isEmpty()) return 0;
        int before = DropReclaim.countInInventory(player, stack);
        player.getInventory().add(stack.copy());
        int after = DropReclaim.countInInventory(player, stack);
        return Math.max(0, Math.min(stack.getCount(), after - before));
    }

    private static void record(@Nullable List<ItemStack> delivered, ItemStack stack, int amount) {
        if(delivered == null || amount <= 0) return;
        ItemStack record = stack.copy();
        record.setCount(amount);
        delivered.add(record);
    }
}
