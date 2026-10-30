package dev.polaris_light.constructionwand.basics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.network.ModMessages;
import dev.polaris_light.constructionwand.network.PacketServerRules;

@EventBusSubscriber(modid = ConstructionWand.MODID)
public class CommonEvents
{
    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent e) {
        ReplacementRegistry.init();
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        // Send the server rules so the client's wand screen and preview agree with this server. The
        // 1.20.1 original also rebroadcast them on a config reload (ServerRuleSync); ConfigServer is
        // registered as a STARTUP config here, which is never reloaded at runtime, so that module
        // has no counterpart.
        if(!(e.getEntity() instanceof ServerPlayer player)) return;

        ModMessages.sendToPlayer(new PacketServerRules(ConfigServer.toServerRules()), player);
    }

    @SubscribeEvent
    public static void logOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Player player = e.getEntity();
        if(player.level().isClientSide) return;
        ConstructionWand.undoHistory.removePlayer(player);
    }
}
