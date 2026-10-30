package dev.polaris_light.constructionwand.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.network.PacketBoundContainer;
import dev.polaris_light.constructionwand.plus.network.PacketExtraOption;
import dev.polaris_light.constructionwand.plus.network.PacketRemoveCore;
import dev.polaris_light.constructionwand.plus.network.PacketRemoveUpgrade;
import dev.polaris_light.constructionwand.plus.network.PacketToggleUpgrade;
import dev.polaris_light.constructionwand.plus.network.PacketTimeAcceleration;
import dev.polaris_light.constructionwand.plus.network.PacketTimeAccelerate;
import dev.polaris_light.constructionwand.plus.network.PacketTimeClear;
import dev.polaris_light.constructionwand.plus.network.PacketSuperFortune;
import dev.polaris_light.constructionwand.plus.network.PacketWandLimit;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModMessages {

    private ModMessages() {
    }

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ConstructionWand.MODID);

        registrar.playToClient(PacketUndoBlocks.ID, PacketUndoBlocks.CODEC, PacketUndoBlocks.Handler::handle);
        registrar.playToServer(PacketQueryUndo.ID, PacketQueryUndo.CODEC, PacketQueryUndo.Handler::handle);
        registrar.playToServer(PacketWandOption.ID, PacketWandOption.CODEC, PacketWandOption.Handler::handle);
        registrar.playToServer(PacketRequestPreview.ID, PacketRequestPreview.CODEC, PacketRequestPreview.Handler::handle);
        registrar.playToClient(PacketPreviewResult.ID, PacketPreviewResult.CODEC, PacketPreviewResult.Handler::handle);
        registrar.playToClient(PacketServerRules.ID, PacketServerRules.CODEC, PacketServerRules.Handler::handle);

        registrar.playToServer(PacketExtraOption.ID, PacketExtraOption.CODEC, PacketExtraOption.Handler::handle);
        registrar.playToServer(PacketToggleUpgrade.ID, PacketToggleUpgrade.CODEC, PacketToggleUpgrade.Handler::handle);
        registrar.playToServer(PacketRemoveUpgrade.ID, PacketRemoveUpgrade.CODEC, PacketRemoveUpgrade.Handler::handle);
        registrar.playToServer(PacketRemoveCore.ID, PacketRemoveCore.CODEC, PacketRemoveCore.Handler::handle);
        registrar.playToServer(PacketBoundContainer.ID, PacketBoundContainer.CODEC, PacketBoundContainer.Handler::handle);
        registrar.playToClient(PacketTimeAcceleration.ID, PacketTimeAcceleration.CODEC, PacketTimeAcceleration.Handler::handle);
        registrar.playToServer(PacketTimeClear.ID, PacketTimeClear.CODEC, PacketTimeClear.Handler::handle);
        registrar.playToServer(PacketTimeAccelerate.ID, PacketTimeAccelerate.CODEC,
                PacketTimeAccelerate.Handler::handle);
        registrar.playToServer(PacketWandLimit.ID, PacketWandLimit.CODEC, PacketWandLimit.Handler::handle);
        // The four numbers of the apotheosis core. Without this line the client is told the payload may not be
        // sent to the server, which throws on the first slider release.
        registrar.playToServer(dev.polaris_light.constructionwand.plus.network.PacketApotheosis.ID,
                dev.polaris_light.constructionwand.plus.network.PacketApotheosis.CODEC,
                dev.polaris_light.constructionwand.plus.network.PacketApotheosis.Handler::handle);
        registrar.playToServer(PacketSuperFortune.ID, PacketSuperFortune.CODEC,
                PacketSuperFortune.Handler::handle);
        registrar.playToServer(PacketUndoRequest.ID, PacketUndoRequest.CODEC, PacketUndoRequest.Handler::handle);
    }

    public static void sendToServer(CustomPacketPayload message) {
        PacketDistributor.sendToServer(message);
    }

    public static void sendToPlayer(CustomPacketPayload message, ServerPlayer player) {
        player.connection.send(message);
    }
}