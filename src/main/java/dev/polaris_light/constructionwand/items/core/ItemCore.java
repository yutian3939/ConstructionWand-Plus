package dev.polaris_light.constructionwand.items.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class ItemCore extends Item implements IWandCore
{
    public ItemCore(Properties properties) {
        super(properties);
    }

    /**
     * The description of the core on the item itself.
     * <p>
     * The signature is the 1.21 one, which passes the tooltip context rather than the level the 1.20.1
     * original took. The old signature is never called by the game, so every core of this fork kept its
     * description out of the tooltip until this was corrected.
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack itemstack, Item.TooltipContext context, @Nonnull List<Component> lines, @Nonnull TooltipFlag extraInfo) {
        lines.add(
                Component.translatable(ConstructionWand.MODID + ".option.cores." + getRegistryName().toString() + ".desc")
                        .withStyle(ChatFormatting.GRAY)
        );
        lines.add(
                Component.translatable(ConstructionWand.MODID + ".tooltip.core_tip").withStyle(ChatFormatting.AQUA)
        );
    }
}
