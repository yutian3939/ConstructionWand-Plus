package com.xinyihl.constructionwandlegacy.material.source;

import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

public final class PortableContainerAccess {
    private static final int SHULKER_SLOTS = 27;

    private PortableContainerAccess() {
    }

    @Nullable
    public static ContainerAccess open(ItemStack owner) {
        if (owner.isEmpty()) {
            return null;
        }
        if (owner.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            IItemHandler handler = owner.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler != null) {
                return new CapabilityAccess(owner, handler);
            }
        }
        if (owner.getCount() == 1 && Block.getBlockFromItem(owner.getItem()) instanceof BlockShulkerBox) {
            return new ShulkerAccess(owner);
        }
        return null;
    }

    public interface ContainerAccess {
        int getSlots();

        ItemStack getStackInSlot(int slot);

        ItemStack extractItem(int slot, int count);

        ItemStack insert(ItemStack stack);

        boolean isValid(ItemStack owner);

        boolean flush();
    }

    private static final class CapabilityAccess implements ContainerAccess {
        private final ItemStack owner;
        private final IItemHandler handler;

        private CapabilityAccess(ItemStack owner, IItemHandler handler) {
            this.owner = owner;
            this.handler = handler;
        }

        @Override
        public int getSlots() {
            try {
                return handler.getSlots();
            } catch (RuntimeException exception) {
                return 0;
            }
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            try {
                return handler.getStackInSlot(slot);
            } catch (RuntimeException exception) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int count) {
            try {
                ItemStack result = handler.extractItem(slot, count, false);
                return result == null ? ItemStack.EMPTY : result;
            } catch (RuntimeException exception) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                try {
                    ItemStack next = handler.insertItem(slot, remaining, false);
                    remaining = next == null ? remaining : next;
                } catch (RuntimeException exception) {
                    break;
                }
            }
            return remaining;
        }

        @Override
        public boolean isValid(ItemStack currentOwner) {
            if (currentOwner != owner || currentOwner.isEmpty() || !currentOwner.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                return false;
            }
            try {
                return currentOwner.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) == handler;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        @Override
        public boolean flush() {
            // Capability handlers persist directly; no separate write is required.
            return true;
        }
    }

    private static final class ShulkerAccess implements ContainerAccess {
        private final ItemStack owner;
        private final NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        private boolean valid = true;

        private ShulkerAccess(ItemStack owner) {
            this.owner = owner;
            NBTTagCompound root = owner.getTagCompound();
            if (root != null && root.hasKey("BlockEntityTag", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound blockEntity = root.getCompoundTag("BlockEntityTag");
                if (blockEntity.hasKey("Items", Constants.NBT.TAG_LIST)) {
                    ItemStackHelper.loadAllItems(blockEntity, items);
                }
            }
        }

        @Override
        public int getSlots() {
            return items.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack extractItem(int slot, int count) {
            ItemStack current = items.get(slot);
            if (current.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            int amount = Math.min(count, current.getCount());
            ItemStack extracted = current.copy();
            extracted.setCount(amount);
            current.shrink(amount);
            return extracted;
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < items.size() && !remaining.isEmpty(); slot++) {
                ItemStack current = items.get(slot);
                if (!current.isEmpty() && ItemStack.areItemsEqual(current, remaining) && ItemStack.areItemStackTagsEqual(current, remaining)) {
                    int accepted = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                    if (accepted > 0) {
                        current.grow(accepted);
                        remaining.shrink(accepted);
                    }
                }
            }
            for (int slot = 0; slot < items.size() && !remaining.isEmpty(); slot++) {
                if (items.get(slot).isEmpty()) {
                    int accepted = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    ItemStack inserted = remaining.copy();
                    inserted.setCount(accepted);
                    items.set(slot, inserted);
                    remaining.shrink(accepted);
                }
            }
            return remaining;
        }

        @Override
        public boolean isValid(ItemStack currentOwner) {
            return valid && currentOwner == owner && !currentOwner.isEmpty() && currentOwner.getCount() == 1 && Block.getBlockFromItem(currentOwner.getItem()) instanceof BlockShulkerBox;
        }

        @Override
        public boolean flush() {
            try {
                NBTTagCompound blockEntity = owner.getOrCreateSubCompound("BlockEntityTag");
                ItemStackHelper.saveAllItems(blockEntity, items);
                return true;
            } catch (RuntimeException ignored) {
                valid = false;
                return false;
            }
        }
    }
}
