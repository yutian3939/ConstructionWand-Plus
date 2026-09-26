package com.xinyihl.morewandcore.client;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.morewandcore.item.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class ClientItemEvents {
    private ClientItemEvents() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        registerModel(ModItems.ITEM_CORE_DIGGING);
        registerModel(ModItems.ITEM_CORE_STORAGE);
        registerModel(ModItems.ITEM_CORE_SLAY);
        registerModel(ModItems.ITEM_UPGRADE_FORTUNE);
        registerModel(ModItems.ITEM_UPGRADE_AUTO_SMELT);
        registerModel(ModItems.ITEM_UPGRADE_FLIGHT);
    }

    private static void registerModel(Item item) {
        if (item == null || item.getRegistryName() == null) {
            return;
        }
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
