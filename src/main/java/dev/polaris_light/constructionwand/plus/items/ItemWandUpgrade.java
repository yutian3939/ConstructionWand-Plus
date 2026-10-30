package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandUpgrade;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A passive wand upgrade: it implements {@link IWandUpgrade} without being a core, so it is installed
 * on the wand and always active instead of being selectable. Several of them can be carried at once.
 */
public class ItemWandUpgrade extends Item implements IWandUpgrade
{
    private final String descriptionKey;

    public ItemWandUpgrade(String descriptionKey, Properties properties) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext context, @Nonnull List<net.minecraft.network.chat.Component> lines, @Nonnull TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable(descriptionKey).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable(ConstructionWand.MODID + ".upgrade.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
