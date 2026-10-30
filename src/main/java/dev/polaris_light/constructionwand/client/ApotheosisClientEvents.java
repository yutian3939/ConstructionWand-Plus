package dev.polaris_light.constructionwand.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.compat.enchantinginfuser.InfuserBridge;

/**
 * Keeps the behaviour of Enchanting Infuser in place on the client as well.
 * <p>
 * The mod asks that behaviour on both sides, and it installs its own whenever its config reloads. The check is a
 * single field comparison, so it is simply repeated every tick rather than hooked onto the reload event.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT)
public final class ApotheosisClientEvents
{
    private ApotheosisClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if(ModList.get() == null || !ModList.get().isLoaded(InfuserBridge.MODID)) return;
        InfuserBridge.ensureInstalled();
    }
}