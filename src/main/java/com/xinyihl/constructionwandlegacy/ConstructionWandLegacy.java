package com.xinyihl.constructionwandlegacy;

import com.xinyihl.constructionwandlegacy.basics.CommonEvents;
import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.network.ModMessages;
import com.xinyihl.constructionwandlegacy.proxy.CommonProxy;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class ConstructionWandLegacy {

    @Mod.Instance(Tags.MOD_ID)
    public static ConstructionWandLegacy instance;

    @SidedProxy(clientSide = "com.xinyihl.constructionwandlegacy.client.ClientProxy", serverSide = "com.xinyihl.constructionwandlegacy.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static Logger LOGGER;

    private ModRuntime runtime;
    private CommonEvents commonEvents;

    public static ResourceLocation loc(String name) {
        return new ResourceLocation(Tags.MOD_ID, name);
    }

    public ModRuntime getRuntime() {
        if (runtime == null) {
            throw new IllegalStateException("Mod runtime has not been initialized");
        }
        return runtime;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        runtime = new ModRuntime(LOGGER);
        commonEvents = new CommonEvents();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(commonEvents);
        proxy.preInit(runtime);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ConfigRuntime.reload();
        CompatRegistrar.register(runtime.getMaterialSourceRegistry());
        ModMessages.register();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        if (commonEvents != null) {
            commonEvents.onServerStarting(event);
        }
    }
}
