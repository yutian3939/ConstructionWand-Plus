package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.morewandcore.basics.WandLimit;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server update of the block limit of one wand, sent from the wand configuration screen.
 * <p>
 * The server clamps the requested limit to the configured maximum of the wand's tier, so the setting
 * can only narrow down what the configuration allows.
 */
public class PacketWandLimit implements IMessage {
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_LIMIT = 4096;

    private EnumHand hand;
    private int slot;
    private int limit;
    private boolean valid;

    public PacketWandLimit() {
    }

    public PacketWandLimit(WandTarget target, int limit) {
        if (target == null || target.getHand() == null) {
            throw new IllegalArgumentException("Invalid wand limit packet");
        }
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.limit = limit;
        this.valid = limit > 0 && limit <= MAX_LIMIT;
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
            if (buf.readableBytes() < 5 || buf.readUnsignedByte() != PROTOCOL_VERSION) {
                return;
            }
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            limit = buf.readUnsignedShort();
            valid = hand != null && isSlotValid(hand, slot) && limit > 0 && limit <= MAX_LIMIT && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid wand limit packet");
        }
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        buf.writeShort(limit);
    }

    public static class Handler implements IMessageHandler<PacketWandLimit, IMessage> {
        private static void apply(PacketWandLimit message, EntityPlayerMP player) {
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

            int configuredMax = ConfigRuntime.getSnapshot().getPlacementLimit(((ItemWand) wand.getItem()).getTier());
            int limit = Math.min(message.limit, Math.max(1, configuredMax));
            if (!WandLimit.set(wand, limit)) {
                return;
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketWandLimit message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
