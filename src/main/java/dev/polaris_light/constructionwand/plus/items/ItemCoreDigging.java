package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.items.core.ItemCore;
import dev.polaris_light.constructionwand.plus.wand.ActionDigging;

/**
 * Wand core that breaks blocks and keeps their drops.
 */
public class ItemCoreDigging extends ItemCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x2ECC40;

    public ItemCoreDigging(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionDigging();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("core_digging");
    }
}
