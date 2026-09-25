package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ModMessages {
    public static final String CHANNEL_NAME = "xcwl";
    public static final int ID_UNDO_BLOCKS = 0;
    public static final int ID_QUERY_UNDO = 1;
    public static final int ID_WAND_OPTION = 2;
    public static final int ID_SERVER_RULES = 3;
    public static final int ID_EXTRA_OPTION = 4;
    public static final int ID_REMOVE_CORE = 5;
    public static final int ID_WAND_LIMIT = 6;
    public static final int ID_REMOVE_UPGRADE = 7;
    public static final int ID_UNDO_REQUEST = 8;
    public static final int ID_BOUND_CONTAINER = 9;

    private static SimpleNetworkWrapper INSTANCE;

    private ModMessages() {
    }

    public static void register() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        INSTANCE.registerMessage(PacketUndoBlocks.Handler.class, PacketUndoBlocks.class, ID_UNDO_BLOCKS, Side.CLIENT);
        INSTANCE.registerMessage(PacketQueryUndo.Handler.class, PacketQueryUndo.class, ID_QUERY_UNDO, Side.SERVER);
        INSTANCE.registerMessage(PacketWandOption.Handler.class, PacketWandOption.class, ID_WAND_OPTION, Side.SERVER);
        INSTANCE.registerMessage(PacketServerRules.Handler.class, PacketServerRules.class, ID_SERVER_RULES, Side.CLIENT);
        INSTANCE.registerMessage(PacketExtraOption.Handler.class, PacketExtraOption.class, ID_EXTRA_OPTION, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoveCore.Handler.class, PacketRemoveCore.class, ID_REMOVE_CORE, Side.SERVER);
        INSTANCE.registerMessage(PacketWandLimit.Handler.class, PacketWandLimit.class, ID_WAND_LIMIT, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoveUpgrade.Handler.class, PacketRemoveUpgrade.class, ID_REMOVE_UPGRADE, Side.SERVER);
        INSTANCE.registerMessage(PacketUndoRequest.Handler.class, PacketUndoRequest.class, ID_UNDO_REQUEST, Side.SERVER);
        INSTANCE.registerMessage(PacketBoundContainer.Handler.class, PacketBoundContainer.class, ID_BOUND_CONTAINER, Side.SERVER);
    }

    public static <MSG extends IMessage> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG extends IMessage> void sendToPlayer(MSG message, EntityPlayerMP player) {
        INSTANCE.sendTo(message, player);
    }

    public static void sendRulesToPlayer(EntityPlayerMP player) {
        sendToPlayer(new PacketServerRules(ConfigRuntime.getSnapshot().getWireRules()), player);
    }

    public static void sendRulesToAll() {
        INSTANCE.sendToAll(new PacketServerRules(ConfigRuntime.getSnapshot().getWireRules()));
    }
}
