package thetadev.constructionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thetadev.constructionwand.basics.ConfigClient;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.ModStats;
import thetadev.constructionwand.client.ClientEvents;
import thetadev.constructionwand.client.RenderBlockPreview;
import thetadev.constructionwand.containers.ContainerManager;
import thetadev.constructionwand.containers.ContainerRegistrar;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.network.PacketQueryUndo;
import thetadev.constructionwand.network.PacketServerRules;
import thetadev.constructionwand.network.PacketUndoBlocks;
import thetadev.constructionwand.network.PacketUndoRequest;
import thetadev.constructionwand.network.PacketWandOption;
import thetadev.constructionwand.wand.undo.UndoHistory;


@Mod(ConstructionWand.MODID)
public class ConstructionWand
{
    public static final String MODID = "constructionwand";
    public static final String MODNAME = "ConstructionWand";

    public static ConstructionWand instance;
    public static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "2";
    public SimpleChannel HANDLER;

    public ContainerManager containerManager;
    public UndoHistory undoHistory;
    public RenderBlockPreview renderBlockPreview;

    public ConstructionWand() {
        instance = this;

        containerManager = new ContainerManager();
        undoHistory = new UndoHistory();

        // Register setup methods for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Register Item DeferredRegister
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        // Plus content (new cores, upgrades and the dedicated creative tab)
        thetadev.constructionwand.plus.items.PlusItems.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Config setup
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ConfigServer.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigClient.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ConstructionWand says hello - may the odds be ever in your favor.");

        // Register packets
        HANDLER = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
        int packetIndex = 0;
        HANDLER.registerMessage(packetIndex++, PacketUndoBlocks.class, PacketUndoBlocks::encode, PacketUndoBlocks::decode, PacketUndoBlocks.Handler::handle);
        HANDLER.registerMessage(packetIndex++, PacketQueryUndo.class, PacketQueryUndo::encode, PacketQueryUndo::decode, PacketQueryUndo.Handler::handle);
        HANDLER.registerMessage(packetIndex++, PacketWandOption.class, PacketWandOption::encode, PacketWandOption::decode, PacketWandOption.Handler::handle);

        // Plus packets: extra wand settings, upgrade/core handling and the bound container list
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketExtraOption.class,
                thetadev.constructionwand.plus.network.PacketExtraOption::encode,
                thetadev.constructionwand.plus.network.PacketExtraOption::decode,
                thetadev.constructionwand.plus.network.PacketExtraOption.Handler::handle);
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketToggleUpgrade.class,
                thetadev.constructionwand.plus.network.PacketToggleUpgrade::encode,
                thetadev.constructionwand.plus.network.PacketToggleUpgrade::decode,
                thetadev.constructionwand.plus.network.PacketToggleUpgrade.Handler::handle);
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketRemoveUpgrade.class,
                thetadev.constructionwand.plus.network.PacketRemoveUpgrade::encode,
                thetadev.constructionwand.plus.network.PacketRemoveUpgrade::decode,
                thetadev.constructionwand.plus.network.PacketRemoveUpgrade.Handler::handle);
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketRemoveCore.class,
                thetadev.constructionwand.plus.network.PacketRemoveCore::encode,
                thetadev.constructionwand.plus.network.PacketRemoveCore::decode,
                thetadev.constructionwand.plus.network.PacketRemoveCore.Handler::handle);
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketBoundContainer.class,
                thetadev.constructionwand.plus.network.PacketBoundContainer::encode,
                thetadev.constructionwand.plus.network.PacketBoundContainer::decode,
                thetadev.constructionwand.plus.network.PacketBoundContainer.Handler::handle);
        HANDLER.registerMessage(packetIndex++, thetadev.constructionwand.plus.network.PacketWandLimit.class,
                thetadev.constructionwand.plus.network.PacketWandLimit::encode,
                thetadev.constructionwand.plus.network.PacketWandLimit::decode,
                thetadev.constructionwand.plus.network.PacketWandLimit.Handler::handle);

        // Server rules (S->C) and the undo key request (C->S).
        HANDLER.registerMessage(packetIndex++, PacketServerRules.class,
                PacketServerRules::encode, PacketServerRules::decode, PacketServerRules.Handler::handle);
        HANDLER.registerMessage(packetIndex, PacketUndoRequest.class,
                PacketUndoRequest::encode, PacketUndoRequest::decode, PacketUndoRequest.Handler::handle);

        // Container registry
        ContainerRegistrar.register();

        // Stats
        ModStats.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        renderBlockPreview = new RenderBlockPreview();
        MinecraftForge.EVENT_BUS.register(renderBlockPreview);
        MinecraftForge.EVENT_BUS.register(new ClientEvents());

        event.enqueueWork(ModItems::registerModelProperties);
    }

    public static ResourceLocation loc(String name) {
        return new ResourceLocation(MODID, name);
    }
}
