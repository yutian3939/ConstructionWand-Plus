package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.items.ModItems;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ColorHandlerEvent;
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
        registerModel(ModItems.WAND_STONE);
        registerModel(ModItems.WAND_IRON);
        registerModel(ModItems.WAND_DIAMOND);
        registerModel(ModItems.WAND_INFINITY);
        registerModel(ModItems.CORE_ANGEL);
        registerModel(ModItems.CORE_DESTRUCTION);

        if (ModItems.isProjectECoreEnabled()) {
            registerModel(ModItems.CORE_PROJECTE);
        }
        if (ModItems.isAE2CoreEnabled()) {
            registerModel(ModItems.CORE_AE);
        }
    }

    @SubscribeEvent
    public static void onItemColor(ColorHandlerEvent.Item event) {
        event.getItemColors().registerItemColorHandler((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof ItemWand) || tintIndex != 1) {
                return -1;
            }
            return WandDataCodec.read(stack).getSelectedCore().getColor();
        }, ModItems.WAND_STONE, ModItems.WAND_IRON, ModItems.WAND_DIAMOND, ModItems.WAND_INFINITY);
    }

    private static void registerModel(Item item) {
        if (item == null || item.getRegistryName() == null) {
            return;
        }
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
