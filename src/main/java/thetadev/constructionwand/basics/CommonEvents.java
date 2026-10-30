package thetadev.constructionwand.basics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.network.PacketServerRules;

@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public class CommonEvents
{
    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent e) {
        ReplacementRegistry.init();
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        // Send the server rules so the client's wand screen and preview agree with this server.
        if(!(e.getEntity() instanceof ServerPlayer player)) return;
        if(ConstructionWand.instance == null || ConstructionWand.instance.HANDLER == null) return;

        ConstructionWand.instance.HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketServerRules(ConfigServer.toServerRules()));
    }

    @SubscribeEvent
    public static void logOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Player player = e.getEntity();
        if(player.level().isClientSide) return;
        ConstructionWand.instance.undoHistory.removePlayer(player);
    }
}
