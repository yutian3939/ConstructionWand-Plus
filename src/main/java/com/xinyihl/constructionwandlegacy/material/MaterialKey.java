package com.xinyihl.constructionwandlegacy.material;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable item identity used by material transactions. Stack size is deliberately excluded.
 */
public final class MaterialKey {
    private final Item item;
    private final int metadata;
    @Nullable
    private final NBTTagCompound tag;
    private final int hashCode;

    private MaterialKey(Item item, int metadata, @Nullable NBTTagCompound tag) {
        this.item = Objects.requireNonNull(item, "item");
        this.metadata = metadata;
        this.tag = tag == null ? null : tag.copy();
        this.hashCode = calculateHashCode();
    }

    public static MaterialKey of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Material stack must not be empty");
        }
        return new MaterialKey(stack.getItem(), stack.getMetadata(), stack.getTagCompound());
    }

    public Item getItem() {
        return item;
    }

    public int getMetadata() {
        return metadata;
    }

    @Nullable
    public NBTTagCompound getTag() {
        return tag == null ? null : tag.copy();
    }

    public ItemStack createStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count, metadata);
        if (tag != null) {
            stack.setTagCompound(tag.copy());
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == item && stack.getMetadata() == metadata && Objects.equals(tag, stack.getTagCompound());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialKey)) {
            return false;
        }
        MaterialKey that = (MaterialKey) other;
        return item == that.item && metadata == that.metadata && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int calculateHashCode() {
        int result = System.identityHashCode(item);
        result = 31 * result + metadata;
        result = 31 * result + (tag == null ? 0 : tag.hashCode());
        return result;
    }
}
