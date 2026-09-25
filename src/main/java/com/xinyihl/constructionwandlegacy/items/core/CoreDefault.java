package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.wand.action.ActionConstruction;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.util.ResourceLocation;

public class CoreDefault implements IWandCore {
    @Override
    public int getColor() {
        return -1;
    }

    @Override
    public WandAction getWandAction() {
        return ActionConstruction.INSTANCE;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWandLegacy.loc("default");
    }
}
