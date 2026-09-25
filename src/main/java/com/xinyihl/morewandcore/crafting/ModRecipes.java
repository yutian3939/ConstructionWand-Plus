package com.xinyihl.morewandcore.crafting;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.item.ModItems;
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
        // A destruction core with the four diamond tools around it: pickaxe up, shovel left,
        // hoe right, axe down.
        register(event, ModItems.CORE_DIGGING, new ItemStack(ModItems.ITEM_CORE_DIGGING),
                " P ",
                "SCH",
                " A ",
                'P', Items.DIAMOND_PICKAXE,
                'S', Items.DIAMOND_SHOVEL,
                'H', Items.DIAMOND_HOE,
                'A', Items.DIAMOND_AXE,
                // The addon and the wand mod both have a ModItems, so the destruction core is named
                // in full here.
                'C', com.xinyihl.constructionwandlegacy.items.ModItems.CORE_DESTRUCTION);

        register(event, ModItems.CORE_STORAGE, new ItemStack(ModItems.ITEM_CORE_STORAGE),
                " PG",
                "PCP",
                "GP ",
                'P', "paneGlass",
                'G', Items.ENDER_PEARL,
                'C', "chest");

        register(event, ModItems.UPGRADE_FORTUNE, new ItemStack(ModItems.ITEM_UPGRADE_FORTUNE),
                " L ",
                "LDL",
                " L ",
                'L', new ItemStack(Items.DYE, 1, 4),
                'D', Items.DIAMOND);

        register(event, ModItems.UPGRADE_AUTO_SMELT, new ItemStack(ModItems.ITEM_UPGRADE_AUTO_SMELT),
                " B ",
                "BFB",
                " B ",
                'B', Items.BLAZE_POWDER,
                'F', Blocks.FURNACE);

        register(event, ModItems.UPGRADE_FLIGHT, new ItemStack(ModItems.ITEM_UPGRADE_FLIGHT),
                " F ",
                "FDF",
                " F ",
                'F', Items.FEATHER,
                'D', Items.DIAMOND);
    }

    private static void register(RegistryEvent.Register<IRecipe> event, String name, ItemStack output, Object... recipe) {
        ShapedOreRecipe shapedRecipe = new ShapedOreRecipe(new ResourceLocation(Tags.MOD_ID, "crafting"), output, recipe);
        shapedRecipe.setRegistryName(new ResourceLocation(Tags.MOD_ID, name));
        event.getRegistry().register(shapedRecipe);
    }
}
