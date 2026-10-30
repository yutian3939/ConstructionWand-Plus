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
import dev.polaris_light.constructionwand.plus.wand.ActionApotheosis;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Behaviour core that hands the player the four numbers of an enchanting table: the world tier of
 * Apotheosis, and the rank (eterna), the quanta and the arcana of Apothic Enchanting.
 * <p>
 * A right click on an enchanting table writes the values the wand screen shows onto that table, so the next
 * enchantment offers are rolled with them. The table itself is not replaced: the numbers are written onto the
 * open menu and kept there while it is open, which is also why they follow shelves that change later.
 * <p>
 * It exists only while both mods are installed; see {@code PlusItems}.
 */
public class ItemCoreApotheosis extends ItemCore
{
    /**
     * Preview/tint colour of the core, a violet of its own so it is not mistaken for the digging or the slay
     * core.
     */
    public static final int CORE_COLOR = 0xB14DFF;

    public ItemCoreApotheosis(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public IWandAction getWandAction() {
        return new ActionApotheosis();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ConstructionWand.loc("core_apotheosis");
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack itemstack, Item.TooltipContext context,
                                @Nonnull List<Component> lines, @Nonnull TooltipFlag extraInfo) {
        super.appendHoverText(itemstack, context, lines, extraInfo);

        lines.add(Component.translatable(ConstructionWand.MODID + ".tooltip.apotheosis_how")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable(ConstructionWand.MODID + ".tooltip.apotheosis_stats")
                .withStyle(ChatFormatting.GRAY));
    }
}