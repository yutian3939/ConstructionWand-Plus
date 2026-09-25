package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.morewandcore.basics.ExtraWandOption;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server update of one addon wand setting.
 */
public class PacketExtraOption implements IMessage {
    /**
     * Bumped when a setting was appended to {@link ExtraWandOption}: the packet sends the ordinal, so
     * a screen of another build would otherwise toggle the wrong setting.
     */
    private static final int PROTOCOL_VERSION = 4;

    private int optionId = -1;
    private EnumHand hand;
    private int slot;
    private int value = -1;
    private boolean valid;

    public PacketExtraOption() {
    }

    public PacketExtraOption(ExtraWandOption option, WandTarget target, int value) {
        if (option == null || target == null || target.getHand() == null) {
            throw new IllegalArgumentException("Invalid digging option packet");
        }
        this.optionId = option.ordinal();
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.value = value;
        this.valid = value >= 0 && value < option.getValueCount();
    }

    private static boolean isSlotValid(EnumHand hand, int slot) {
        return hand == EnumHand.MAIN_HAND ? slot >= 0 && slot < 9 : slot == WandTarget.OFFHAND_SLOT;
    }

    private static ExtraWandOption optionOf(int optionId) {
        ExtraWandOption[] options = ExtraWandOption.values();
        return optionId >= 0 && optionId < options.length ? options[optionId] : null;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        try {
            if (buf.readableBytes() < 5 || buf.readUnsignedByte() != PROTOCOL_VERSION) {
                return;
            }
            optionId = buf.readUnsignedByte();
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            value = buf.readUnsignedByte();
            ExtraWandOption option = optionOf(optionId);
            valid = option != null && hand != null && value < option.getValueCount() && isSlotValid(hand, slot) && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid digging option packet");
        }
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(optionId);
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        buf.writeByte(value);
    }

    public static class Handler implements IMessageHandler<PacketExtraOption, IMessage> {
        private static void apply(PacketExtraOption message, EntityPlayerMP player) {
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
            ExtraWandOption option = optionOf(message.optionId);
            if (option == null || !ExtraWandOption.setIndex(wand, option, message.value)) {
                return;
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketExtraOption message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
