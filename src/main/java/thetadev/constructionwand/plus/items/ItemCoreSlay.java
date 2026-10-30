package thetadev.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.items.core.ItemCore;
import thetadev.constructionwand.plus.wand.ActionSlay;

/**
 * Behaviour core that kills living entities instantly. A left click on a creature kills that one, a
 * sneaking right click kills every living entity within the wand's configured slay radius.
 */
public class ItemCoreSlay extends ItemCore
{
    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0x8E24AA;

    public ItemCoreSlay(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionSlay();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("core_slay");
    }
}
