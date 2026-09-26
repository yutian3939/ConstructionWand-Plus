package com.xinyihl.morewandcore.item;

import com.xinyihl.constructionwandlegacy.items.core.ItemCore;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.morewandcore.wand.ActionSlay;

/**
 * Behaviour core that kills living entities instantly. A left click on a creature kills that one,
 * a sneaking right click kills every living entity within the wand's configured slay radius.
 */
public class ItemCoreSlay extends ItemCore {
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x8E24AA;

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public WandAction getWandAction() {
        return ActionSlay.INSTANCE;
    }
}
