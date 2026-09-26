package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Behaviour of the slay core. It kills entities instead of touching blocks, so it plans nothing: the
 * actual killing happens from the item interaction handlers ({@code onLeftClickEntity} and the
 * sneaking right click) in {@link ItemWand}.
 */
public final class ActionSlay implements WandAction {
    public static final ActionSlay INSTANCE = new ActionSlay();

    private ActionSlay() {
    }

    @Override
    public int getLimit(ItemStack wand) {
        return ((ItemWand) wand.getItem()).getTier().getConfiguredSlayRadius();
    }

    @Override
    public int getLimit(WandContext context) {
        return context.getSlayRadius();
    }

    @Override
    public List<WandOperation> plan(WandContext context, OperationResolver resolver, int limit) {
        return Collections.emptyList();
    }
}
