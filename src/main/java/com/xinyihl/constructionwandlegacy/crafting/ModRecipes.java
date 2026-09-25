package com.xinyihl.constructionwandlegacy.crafting;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.items.ModItems;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModRecipes {
    private ModRecipes() {
    }

    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        registerShapedRecipe(event, "stone_wand", new ItemStack(ModItems.WAND_STONE), "  M", " S ", "S  ", 'M', Blocks.COBBLESTONE, 'S', Items.STICK);

        registerShapedRecipe(event, "iron_wand", new ItemStack(ModItems.WAND_IRON), "  M", " S ", "S  ", 'M', Items.IRON_INGOT, 'S', Items.STICK);

        registerShapedRecipe(event, "diamond_wand", new ItemStack(ModItems.WAND_DIAMOND), "  M", " S ", "S  ", 'M', Items.DIAMOND, 'S', Items.STICK);

        registerShapedRecipe(event, "infinity_wand", new ItemStack(ModItems.WAND_INFINITY), "  M", " S ", "S  ", 'M', Items.NETHER_STAR, 'S', Items.STICK);

        registerShapedRecipe(event, "core_angel", new ItemStack(ModItems.CORE_ANGEL), " PG", "PFP", "GP ", 'P', "paneGlass", 'G', Items.GOLD_INGOT, 'F', Items.FEATHER);

        registerShapedRecipe(event, "core_destruction", new ItemStack(ModItems.CORE_DESTRUCTION), " Pp", "PDP", "pP ", 'P', "paneGlass", 'D', Blocks.DIAMOND_BLOCK, 'p', Items.DIAMOND_PICKAXE);

        if (ModItems.isProjectECoreEnabled()) {
            registerShapedRecipe(event, "core_projecte", new ItemStack(ModItems.CORE_PROJECTE), " PG", "PEP", "GP ", 'P', "paneGlass", 'G', Items.GLOWSTONE_DUST, 'E', Items.EMERALD);
        }

        if (ModItems.isAE2CoreEnabled()) {
            registerShapedRecipe(event, "core_ae", new ItemStack(ModItems.CORE_AE), " PG", "PCP", "GP ", 'P', "paneGlass", 'G', Items.QUARTZ, 'C', "chest");
        }

        event.getRegistry().register(new RecipeWandUpgrade().setRegistryName(ConstructionWandLegacy.loc("wand_upgrade")));
    }

    private static void registerShapedRecipe(RegistryEvent.Register<IRecipe> event, String recipeName, ItemStack output, Object... recipe) {
        ResourceLocation group = ConstructionWandLegacy.loc("crafting");
        ShapedOreRecipe shapedRecipe = new ShapedOreRecipe(group, output, recipe);
        shapedRecipe.setRegistryName(ConstructionWandLegacy.loc(recipeName));
        event.getRegistry().register(shapedRecipe);
    }
}
