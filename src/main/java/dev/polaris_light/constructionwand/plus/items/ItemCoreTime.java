package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.items.core.ItemCore;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreLevels;
import dev.polaris_light.constructionwand.plus.wand.ActionTime;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Behaviour core that accelerates the block it is pointed at, like a time torch or a bottle of time:
 * a right click on a block runs it faster, at the speed and for the time the wand is set to on its screen.
 * <p>
 * How far a wand may go is the wand's tier: stone up to {@code 16x}, iron up to {@code 128x}, diamond up
 * to {@code 512x}, and only the infinity wand reaches the {@code 1024x} top of the ladder.
 */
public class ItemCoreTime extends ItemCore
{
    /**
     * Preview/tint colour of the core, a cyan of its own so it is not mistaken for the digging or the
     * AE core.
     */
    public static final int CORE_COLOR = 0x00E5FF;

    public ItemCoreTime(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionTime();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("core_time");
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack itemstack, Item.TooltipContext context, @Nonnull List<Component> lines,
                                @Nonnull TooltipFlag extraInfo) {
        super.appendHoverText(itemstack, context, lines, extraInfo);

        lines.add(Component.translatable(ConstructionWand.MODID + ".tooltip.time_how").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(ConstructionWand.MODID + ".tooltip.time_levels",
                TimeCoreLevels.ladderText()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(ConstructionWand.MODID + ".tooltip.time_tiers").withStyle(ChatFormatting.GRAY));
    }
}
