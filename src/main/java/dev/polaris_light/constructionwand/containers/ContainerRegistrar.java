package dev.polaris_light.constructionwand.containers;

import net.neoforged.fml.ModList;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.containers.handlers.HandlerAdvWirelessTerminal;
import dev.polaris_light.constructionwand.containers.handlers.HandlerBotania;
import dev.polaris_light.constructionwand.containers.handlers.HandlerBundle;
import dev.polaris_light.constructionwand.containers.handlers.HandlerCapability;
import dev.polaris_light.constructionwand.containers.handlers.HandlerDimensionsNet;
import dev.polaris_light.constructionwand.containers.handlers.HandlerLightland;
import dev.polaris_light.constructionwand.containers.handlers.HandlerNetTerminal;
import dev.polaris_light.constructionwand.containers.handlers.HandlerOccultism;
import dev.polaris_light.constructionwand.containers.handlers.HandlerProjectE;
import dev.polaris_light.constructionwand.containers.handlers.HandlerPortableCell;
import dev.polaris_light.constructionwand.containers.handlers.HandlerShulkerbox;
import dev.polaris_light.constructionwand.containers.handlers.HandlerWirelessGrid;
import dev.polaris_light.constructionwand.containers.handlers.HandlerWirelessTerminal;
import dev.polaris_light.constructionwand.plus.items.ItemCoreAE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreProjectE;
import dev.polaris_light.constructionwand.plus.items.PlusItems;
import dev.polaris_light.constructionwand.plus.material.CoreGatedHandler;


public class ContainerRegistrar
{
    public static void register() {
        // Normal backpack will be recognized as Capability
        if(ModList.get().isLoaded("l2backpack")) {
            ConstructionWand.containerManager.register(new HandlerLightland());
            ConstructionWand.LOGGER.info("L2Backpack integration added");
        }

        if(ModList.get().isLoaded("botania")) {
            ConstructionWand.containerManager.register(new HandlerBotania());
            ConstructionWand.LOGGER.info("Botania integration added");
        }

        if(ModList.get().isLoaded("curios")) {
            ConstructionWand.LOGGER.info("Curios integration added");
        }

        if(ModList.get().isLoaded("ae2")) {
            // The ME network is reachable as soon as the wand carries the AE core; the wrapper only lets
            // the handler answer for such a wand, see plus.material.CoreGatedHandler.
            ConstructionWand.containerManager.register(ItemCoreAE.SOURCE,
                    CoreGatedHandler.of(PlusItems.CORE_AE, new HandlerPortableCell()));
            ConstructionWand.containerManager.register(ItemCoreAE.SOURCE,
                    CoreGatedHandler.of(PlusItems.CORE_AE, new HandlerWirelessTerminal()));
            ConstructionWand.LOGGER.info("Applied Energistics 2 integration added");
        }

        if(ModList.get().isLoaded("toms_storage")) {
            ConstructionWand.containerManager.register(new HandlerAdvWirelessTerminal());
            ConstructionWand.LOGGER.info("Tom's Simple Storage integration added");
        }

        if(ModList.get().isLoaded("refinedstorage")) {
            ConstructionWand.containerManager.register(new HandlerWirelessGrid());
            ConstructionWand.LOGGER.info("Refined Storage integration added");
        }

        if(ModList.get().isLoaded("beyonddimensions")) {
            ConstructionWand.containerManager.register(new HandlerDimensionsNet());
            ConstructionWand.containerManager.register(new HandlerNetTerminal());
            ConstructionWand.LOGGER.info("Beyond Dimensions integration added");
        }

        if(ModList.get().isLoaded("occultism")) {
            ConstructionWand.containerManager.register(new HandlerOccultism());
            ConstructionWand.LOGGER.info("Occultism integration added");
        }

        if(ModList.get().isLoaded("projecte")) {
            // The player's EMC is reachable as soon as the wand carries the ProjectE core, see above.
            ConstructionWand.containerManager.register(ItemCoreProjectE.SOURCE,
                    CoreGatedHandler.of(PlusItems.CORE_PROJECTE, new HandlerProjectE()));
            ConstructionWand.LOGGER.info("ProjectE integration added");
        }

        ConstructionWand.containerManager.register(new HandlerShulkerbox());
        ConstructionWand.containerManager.register(new HandlerBundle());
        ConstructionWand.containerManager.register(new HandlerCapability());
    }
}
