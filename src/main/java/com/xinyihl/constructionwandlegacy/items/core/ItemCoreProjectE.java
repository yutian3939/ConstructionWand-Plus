package com.xinyihl.constructionwandlegacy.items.core;

import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.wand.action.ActionConstruction;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Material core backed by ProjectE EMC.
 * <p>
 * It supplies material for placement out of EMC. EMC has no notion of storing items, so a harvested
 * stack is not stored: anything with an EMC value is converted into EMC (and learned when the player
 * did not know it yet), only items without a value stay items and go to the player inventory.
 */
public class ItemCoreProjectE extends ItemCore {
    @Override
    public int getColor() {
        return 0xFF3CF4;
    }

    @Override
    public WandAction getWandAction() {
        return ActionConstruction.INSTANCE;
    }

    @Override
    public MaterialSourceFactory getMaterialSourceFactory() {
        return CompatRegistrar.getProjectEMaterialSourceFactory();
    }

    @Override
    public boolean isMaterialCore() {
        return true;
    }

    @Override
    public ItemStack deposit(EntityPlayer player, ItemStack wand, ItemStack stack) {
        return CompatRegistrar.depositProjectE(player, stack);
    }

    @Override
    public int countStored(EntityPlayer player, ItemStack wand, ItemStack template) {
        return CompatRegistrar.countProjectEEmc(player, template);
    }

    @Override
    public int withdraw(EntityPlayer player, ItemStack wand, ItemStack template, int amount) {
        return CompatRegistrar.takeProjectEEmc(player, template, amount);
    }
}
