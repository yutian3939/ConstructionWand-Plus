package com.xinyihl.constructionwandlegacy.material;

import net.minecraft.item.ItemStack;

public interface MaterialCollector {
    void accept(MaterialKey key, long count);

    default void accept(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
            accept(MaterialKey.of(stack), stack.getCount());
        }
    }
}
