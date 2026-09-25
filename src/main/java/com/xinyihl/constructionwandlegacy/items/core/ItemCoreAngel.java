package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.wand.action.ActionAngel;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;

public class ItemCoreAngel extends ItemCore {
    @Override
    public int getColor() {
        return 0xE9B115;
    }

    @Override
    public WandAction getWandAction() {
        return ActionAngel.INSTANCE;
    }
}
