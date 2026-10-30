package thetadev.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.items.core.ItemCore;
import thetadev.constructionwand.plus.wand.ActionDigging;

/**
 * Wand core that breaks blocks and keeps their drops.
 */
public class ItemCoreDigging extends ItemCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x1FBF8F;

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
