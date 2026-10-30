package thetadev.constructionwand.basics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.network.PacketServerRules;

/**
 * Rebroadcasts the server rules when the configuration is reloaded, so a running server does not need
 * a restart before the clients see the new values again.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ServerRuleSync
{
    private ServerRuleSync() {
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if(event.getConfig().getType() != ModConfig.Type.SERVER) return;
        if(event.getConfig().getSpec() != ConfigServer.SPEC) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if(server == null || ConstructionWand.instance == null || ConstructionWand.instance.HANDLER == null) return;

        PacketServerRules packet = new PacketServerRules(ConfigServer.toServerRules());
        for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            ConstructionWand.instance.HANDLER.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if(event.getConfig().getType() != ModConfig.Type.SERVER) return;
        if(event.getConfig().getSpec() != ConfigServer.SPEC) return;
        // The similar block groups are read from the config as well and are rebuilt on load.
        ReplacementRegistry.init();
    }
}
