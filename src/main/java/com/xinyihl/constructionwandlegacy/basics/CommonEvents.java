package com.xinyihl.constructionwandlegacy.basics;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.network.ModMessages;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public class CommonEvents {
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ModMessages.sendRulesToPlayer((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (ConstructionWandLegacy.instance != null && event.player != null) {
            ConstructionWandLegacy.instance.getRuntime().getUndoService().clearHistory(event.player.getUniqueID());
        }
    }

    public void onServerStarting(FMLServerStartingEvent event) {
        ConfigRuntime.reload();
    }
}
