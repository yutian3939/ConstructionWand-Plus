package com.xinyihl.morewandcore.item;

import com.xinyihl.constructionwandlegacy.material.MaterialKey;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.wand.action.ActionConstruction;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.basics.ContainerBinding;
import com.xinyihl.morewandcore.material.StorageMaterialSourceFactory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Material core backed by the containers bound to the wand.
 * <p>
 * On its own it does nothing: the behaviour comes from whichever behaviour core is selected. Sneak
 * and right click a container with this core installed to bind it, repeat that to bind more of them;
 * the wand then builds from them in the order the configuration screen shows and stores what it
 * harvests back into them the same way.
 */
public class ItemCoreStorage extends Item implements IWandCore {

    /**
     * Preview/tint colour of the core, kept distinct from the existing cores.
     */
    public static final int CORE_COLOR = 0xC1741F;

    public ItemCoreStorage() {
        setMaxStackSize(1);
    }

    @Override
    public int getColor() {
        return CORE_COLOR;
    }

    @Override
    public WandAction getWandAction() {
        return ActionConstruction.INSTANCE;
    }

    @Override
    public MaterialSourceFactory getMaterialSourceFactory() {
        return StorageMaterialSourceFactory.INSTANCE;
    }

    @Override
    public boolean isMaterialCore() {
        return true;
    }

    @Override
    public ItemStack deposit(EntityPlayer player, ItemStack wand, ItemStack stack) {
        return ContainerBinding.insert(wand, stack);
    }

    @Override
    public int countStored(EntityPlayer player, ItemStack wand, ItemStack template) {
        return template == null || template.isEmpty() ? 0 : ContainerBinding.countStored(wand, MaterialKey.of(template));
    }

    @Override
    public int withdraw(EntityPlayer player, ItemStack wand, ItemStack template, int amount) {
        return amount <= 0 || template == null || template.isEmpty()
                ? amount
                : ContainerBinding.take(wand, MaterialKey.of(template), amount);
    }

    @Override
    public int fluidCapacity(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return fluid == null ? 0 : ContainerBinding.fluidCapacity(wand, fluid);
    }

    @Override
    public int depositFluid(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return ContainerBinding.fill(wand, fluid);
    }

    @Override
    public int withdrawFluid(EntityPlayer player, ItemStack wand, FluidStack fluid, int amount) {
        return ContainerBinding.drain(wand, fluid, amount);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".option.cores." + getRegistryName() + ".desc"));
        tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.core_tip"));
    }
}
