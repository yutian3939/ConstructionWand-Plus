package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandOption;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketWandOption implements IMessage {
    private WandOption option;
    private EnumHand hand;
    private int slot;
    private int value;
    private boolean notify;
    private boolean valid;

    public PacketWandOption() {
    }

    public PacketWandOption(WandOption option, WandTarget target, int value, boolean notify) {
        this.option = option;
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.value = value;
        this.notify = notify;
        this.valid = option != null && value >= 0 && value <= 255;
        if (!valid) {
            throw new IllegalArgumentException("Invalid wand option packet");
        }
    }

    private static boolean isSlotValid(EnumHand hand, int slot) {
        return hand == EnumHand.MAIN_HAND ? slot >= 0 && slot < 9 : slot == WandTarget.OFFHAND_SLOT;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        try {
            if (!NetworkProtocol.readHeader(buf) || buf.readableBytes() < 5) {
                return;
            }
            option = WandOption.fromNetworkId(buf.readUnsignedByte());
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            value = buf.readUnsignedByte();
            int notifyValue = buf.readUnsignedByte();
            if (notifyValue > 1) {
                return;
            }
            notify = notifyValue == 1;
            valid = option != null && hand != null && value <= 255 && isSlotValid(hand, slot) && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid wand option packet");
        }
        NetworkProtocol.writeHeader(buf);
        buf.writeByte(option.getNetworkId());
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        buf.writeByte(value);
        buf.writeBoolean(notify);
    }

    public boolean isValid() {
        return valid;
    }

    public WandOption getOption() {
        return option;
    }

    public EnumHand getHand() {
        return hand;
    }

    public int getSlot() {
        return slot;
    }

    public int getValue() {
        return value;
    }

    public boolean shouldNotify() {
        return notify;
    }

    public static class Handler implements IMessageHandler<PacketWandOption, IMessage> {
        private static void apply(PacketWandOption message, EntityPlayerMP player) {
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
            WandState before = WandDataCodec.read(wand);
            if (!WandDataCodec.isValidNetworkValue(before, message.option, message.value) || !WandDataCodec.updateNetworkValue(wand, message.option, message.value)) {
                return;
            }
            WandState after = WandDataCodec.read(wand);
            if (message.notify) {
                ItemWand.optionMessage(player, message.option, after);
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketWandOption message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
