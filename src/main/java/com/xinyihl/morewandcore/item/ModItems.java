package com.xinyihl.morewandcore.item;

import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModItems {
    public static final String CORE_DIGGING = "core_digging";
    public static final String CORE_STORAGE = "core_storage";
    public static final String UPGRADE_FORTUNE = "upgrade_fortune";
    public static final String UPGRADE_AUTO_SMELT = "upgrade_auto_smelt";
    public static final String UPGRADE_FLIGHT = "upgrade_flight";

    public static Item ITEM_CORE_DIGGING;
    public static Item ITEM_CORE_STORAGE;
    public static Item ITEM_UPGRADE_FORTUNE;
    public static Item ITEM_UPGRADE_AUTO_SMELT;
    public static Item ITEM_UPGRADE_FLIGHT;

    private ModItems() {
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        ITEM_CORE_DIGGING = register(event, CORE_DIGGING, new ItemCoreDigging(), CreativeTabs.MISC);
        ITEM_CORE_STORAGE = register(event, CORE_STORAGE, new ItemCoreStorage(), CreativeTabs.MISC);
        ITEM_UPGRADE_FORTUNE = register(event, UPGRADE_FORTUNE, new ItemWandUpgrade(Tags.MOD_ID + ".upgrade." + UPGRADE_FORTUNE + ".desc"), CreativeTabs.MISC);
        ITEM_UPGRADE_AUTO_SMELT = register(event, UPGRADE_AUTO_SMELT, new ItemWandUpgrade(Tags.MOD_ID + ".upgrade." + UPGRADE_AUTO_SMELT + ".desc"), CreativeTabs.MISC);
        ITEM_UPGRADE_FLIGHT = register(event, UPGRADE_FLIGHT, new ItemWandUpgrade(Tags.MOD_ID + ".upgrade." + UPGRADE_FLIGHT + ".desc"), CreativeTabs.MISC);
    }

    private static Item register(RegistryEvent.Register<Item> event, String name, Item item, CreativeTabs tab) {
        item.setRegistryName(new ResourceLocation(Tags.MOD_ID, name));
        item.setTranslationKey(Tags.MOD_ID + "." + name);
        item.setCreativeTab(tab);
        event.getRegistry().register(item);
        return item;
    }
}
