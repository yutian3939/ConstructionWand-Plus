package com.xinyihl.constructionwandlegacy.crafting;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class RecipeWandUpgrade extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        ItemStack wand = ItemStack.EMPTY;
        IWandUpgrade upgrade = null;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (wand.isEmpty() && stack.getItem() instanceof ItemWand) {
                wand = stack;
                continue;
            }

            if (upgrade == null && stack.getItem() instanceof IWandUpgrade) {
                upgrade = (IWandUpgrade) stack.getItem();
                continue;
            }

            return false;
        }

        if (wand.isEmpty() || upgrade == null) {
            return false;
        }

        return !WandDataCodec.read(wand).hasUpgrade(upgrade);
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack wand = ItemStack.EMPTY;
        IWandUpgrade upgrade = null;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (wand.isEmpty() && stack.getItem() instanceof ItemWand) {
                wand = stack;
            } else if (upgrade == null && stack.getItem() instanceof IWandUpgrade) {
                upgrade = (IWandUpgrade) stack.getItem();
            }
        }

        if (wand.isEmpty() || upgrade == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = wand.copy();
        result.setCount(1);
        return WandDataCodec.addUpgrade(result, upgrade) ? result : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
