package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server update of one bound container of a wand, sent from the wand configuration screen.
 * <p>
 * The container is named by its position and dimension instead of its place in the list, so a screen
 * working on an older copy of the wand can only reorder the entries the wand really carries.
 */
public class PacketBoundContainer implements IMessage {
    private static final int PROTOCOL_VERSION = 1;
    private static final int ACTION_MOVE_UP = 0;
    private static final int ACTION_MOVE_DOWN = 1;
    private static final int ACTION_REMOVE = 2;
    /**
     * Coordinates outside of this range cannot hold a bound block, see the world border.
     */
    private static final int MAX_COORDINATE = 30_000_000;

    private EnumHand hand;
    private int slot;
    private int dimension;
    private int x;
    private int y;
    private int z;
    private int action = -1;
    private boolean valid;

    public PacketBoundContainer() {
    }

    private PacketBoundContainer(WandTarget target, BlockPos pos, int dimension, int action) {
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.dimension = dimension;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.action = action;
        this.valid = true;
    }

    public static PacketBoundContainer moveUp(WandTarget target, BlockPos pos, int dimension) {
        return new PacketBoundContainer(target, pos, dimension, ACTION_MOVE_UP);
    }

    public static PacketBoundContainer moveDown(WandTarget target, BlockPos pos, int dimension) {
        return new PacketBoundContainer(target, pos, dimension, ACTION_MOVE_DOWN);
    }

    public static PacketBoundContainer remove(WandTarget target, BlockPos pos, int dimension) {
        return new PacketBoundContainer(target, pos, dimension, ACTION_REMOVE);
    }

    private static boolean isSlotValid(EnumHand hand, int slot) {
        return hand == EnumHand.MAIN_HAND ? slot >= 0 && slot < 9 : slot == WandTarget.OFFHAND_SLOT;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        hand = null;
        try {
            // Version, hand, slot, action and the four integers of the container position.
            if (buf.readableBytes() < 20 || buf.readUnsignedByte() != PROTOCOL_VERSION) {
                return;
            }
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            action = buf.readUnsignedByte();
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            valid = hand != null && isSlotValid(hand, slot)
                    && action >= ACTION_MOVE_UP && action <= ACTION_REMOVE
                    && Math.abs(x) <= MAX_COORDINATE && Math.abs(y) <= MAX_COORDINATE && Math.abs(z) <= MAX_COORDINATE
                    && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid bound container packet");
        }
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        buf.writeByte(action);
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketBoundContainer, IMessage> {
        private static void apply(PacketBoundContainer message, EntityPlayerMP player) {
            WandTarget target;
            try {
                target = new WandTarget(message.hand, message.slot);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            ItemStack wand = target.resolve(player);
            if (wand.isEmpty() || !(wand.getItem() instanceof ItemWand)) {
                return;
            }
            BlockPos pos = new BlockPos(message.x, message.y, message.z);
            boolean changed;
            switch (message.action) {
                case ACTION_MOVE_UP:
                    changed = BoundContainerSourceFactory.moveBinding(wand, pos, message.dimension, -1);
                    break;
                case ACTION_MOVE_DOWN:
                    changed = BoundContainerSourceFactory.moveBinding(wand, pos, message.dimension, 1);
                    break;
                default:
                    changed = BoundContainerSourceFactory.removeBinding(wand, pos, message.dimension);
                    break;
            }
            if (!changed) {
                return;
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketBoundContainer message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
