package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.wand.action.ActionConstruction;

/**
 * The "no material core" entry of the material core selection: materials come from the containers the
 * wand is bound to and from the player inventory.
 */
public class CoreNoMaterial implements IWandCore
{
    @Override
    public int getColor() {
        return -1;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionConstruction();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("none");
    }
}
