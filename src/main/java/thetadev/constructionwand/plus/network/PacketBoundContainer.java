package thetadev.constructionwand.plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.plus.material.ContainerBinding;

import java.util.function.Supplier;

/**
 * Changes the bound container list from the wand screen: raise or lower a binding in the priority
 * order, drop it on the position it was dragged to, or remove it. The binding is addressed by its
 * position, so a stale screen can only move entries that really exist.
 */
public class PacketBoundContainer
{
    public static final int ACTION_UP = 0;
    public static final int ACTION_DOWN = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_MOVE = 3;

    public final int action;
    public final BlockPos pos;
    /**
     * The index {@link #ACTION_MOVE} moves the binding to, unused by the other actions.
     */
    public final int index;

    public PacketBoundContainer(int action, BlockPos pos) {
        this(action, pos, 0);
    }

    public PacketBoundContainer(int action, BlockPos pos, int index) {
        this.action = action;
        this.pos = pos;
        this.index = index;
    }

    public static void encode(PacketBoundContainer msg, FriendlyByteBuf buffer) {
        buffer.writeByte(msg.action);
        buffer.writeBlockPos(msg.pos);
        buffer.writeVarInt(msg.index);
    }

    public static PacketBoundContainer decode(FriendlyByteBuf buffer) {
        return new PacketBoundContainer(buffer.readUnsignedByte(), buffer.readBlockPos(), buffer.readVarInt());
    }

    public static class Handler
    {
        public static void handle(final PacketBoundContainer msg, final Supplier<NetworkEvent.Context> ctx) {
            if(!ctx.get().getDirection().getReceptionSide().isServer()) return;

            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;

            ItemStack wand = WandUtil.holdingWand(player);
            if(wand == null) return;

            boolean changed;
            switch(msg.action) {
                case ACTION_UP -> changed = ContainerBinding.moveBinding(wand, msg.pos, player.level(), -1);
                case ACTION_DOWN -> changed = ContainerBinding.moveBinding(wand, msg.pos, player.level(), 1);
                case ACTION_REMOVE -> changed = ContainerBinding.removeBinding(wand, msg.pos, player.level());
                case ACTION_MOVE -> changed = ContainerBinding.moveBindingTo(wand, msg.pos, player.level(), msg.index);
                default -> changed = false;
            }
            if(!changed) return;

            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }
}
