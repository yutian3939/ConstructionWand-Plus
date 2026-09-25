package com.xinyihl.constructionwandlegacy.items;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreAE;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreAngel;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreDestruction;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreProjectE;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWandBasic;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWandInfinity;
import com.xinyihl.constructionwandlegacy.wand.WandTier;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModItems {
    private static final String PROJECTE_MOD_ID = "projecte";
    private static final String AE2_MOD_ID = "appliedenergistics2";

    public static Item WAND_STONE;
    public static Item WAND_IRON;
    public static Item WAND_DIAMOND;
    public static Item WAND_INFINITY;

    public static Item CORE_ANGEL;
    public static Item CORE_DESTRUCTION;
    public static Item CORE_PROJECTE;
    public static Item CORE_AE;

    private ModItems() {
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        WAND_STONE = register(event, "stone_wand", new ItemWandBasic(WandTier.STONE, new ItemStack(Blocks.COBBLESTONE)), CreativeTabs.TOOLS);
        WAND_IRON = register(event, "iron_wand", new ItemWandBasic(WandTier.IRON, new ItemStack(Items.IRON_INGOT)), CreativeTabs.TOOLS);
        WAND_DIAMOND = register(event, "diamond_wand", new ItemWandBasic(WandTier.DIAMOND, new ItemStack(Items.DIAMOND)), CreativeTabs.TOOLS);
        WAND_INFINITY = register(event, "infinity_wand", new ItemWandInfinity(), CreativeTabs.TOOLS);

        CORE_ANGEL = register(event, "core_angel", new ItemCoreAngel(), CreativeTabs.MISC);
        CORE_DESTRUCTION = register(event, "core_destruction", new ItemCoreDestruction(), CreativeTabs.MISC);

        if (isProjectECoreEnabled()) {
            CORE_PROJECTE = register(event, "core_projecte", new ItemCoreProjectE(), CreativeTabs.MISC);
        }
        if (isAE2CoreEnabled()) {
            CORE_AE = register(event, "core_ae", new ItemCoreAE(), CreativeTabs.MISC);
        }
    }

    public static boolean isProjectECoreEnabled() {
        return Loader.isModLoaded(PROJECTE_MOD_ID);
    }

    public static boolean isAE2CoreEnabled() {
        return Loader.isModLoaded(AE2_MOD_ID);
    }

    private static Item register(RegistryEvent.Register<Item> event, String name, Item item, CreativeTabs tab) {
        item.setRegistryName(ConstructionWandLegacy.loc(name));
        item.setTranslationKey(Tags.MOD_ID + "." + name);
        item.setCreativeTab(tab);
        event.getRegistry().register(item);
        return item;
    }
}
