package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.compat.ae2.AE2Compat;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.wand.action.ActionConstruction;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Material core bound to an ME network: the wand takes its building material from the network and
 * can store what it harvests back into it.
 */
public class ItemCoreAE extends ItemCore {
    @Override
    public int getColor() {
        return 0x3CA4FF;
    }

    @Override
    public WandAction getWandAction() {
        return ActionConstruction.INSTANCE;
    }

    @Override
    public MaterialSourceFactory getMaterialSourceFactory() {
        return CompatRegistrar.getAE2MaterialSourceFactory();
    }

    @Override
    public boolean isMaterialCore() {
        return true;
    }

    @Override
    public ItemStack deposit(EntityPlayer player, ItemStack wand, ItemStack stack) {
        return AE2Compat.deposit(wand, player, stack);
    }

    @Override
    public int countStored(EntityPlayer player, ItemStack wand, ItemStack template) {
        return AE2Compat.countStored(wand, player, template);
    }

    @Override
    public int withdraw(EntityPlayer player, ItemStack wand, ItemStack template, int amount) {
        return AE2Compat.withdraw(wand, player, template, amount);
    }

    @Override
    public int fluidCapacity(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return AE2Compat.fluidCapacity(wand, player, fluid);
    }

    @Override
    public int depositFluid(EntityPlayer player, ItemStack wand, FluidStack fluid) {
        return AE2Compat.depositFluid(wand, player, fluid);
    }

    @Override
    public int withdrawFluid(EntityPlayer player, ItemStack wand, FluidStack fluid, int amount) {
        return AE2Compat.withdrawFluid(wand, player, fluid, amount);
    }
}
