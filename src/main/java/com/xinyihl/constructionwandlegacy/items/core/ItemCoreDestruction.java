package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.wand.action.ActionDestruction;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;

public class ItemCoreDestruction extends ItemCore {
    @Override
    public int getColor() {
        return 0xFF0000;
    }

    @Override
    public WandAction getWandAction() {
        return ActionDestruction.INSTANCE;
    }
}
