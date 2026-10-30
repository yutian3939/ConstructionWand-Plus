package thetadev.constructionwand.plus.items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandUpgrade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        return ForgeRegistries.ITEMS.getKey(this);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<net.minecraft.network.chat.Component> lines, @Nonnull TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable(descriptionKey).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable(ConstructionWand.MODID + ".upgrade.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
