package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Client to server request to take one core off the wand, sent from the wand configuration screen.
 * <p>
 * The core is named by its registry id and only removed when the wand in the addressed hand really
 * carries it, so a stale screen cannot take anything with it, and the item is handed to the player.
 */
public class PacketRemoveCore implements IMessage {
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_ID_BYTES = 128;

    private EnumHand hand;
    private int slot;
    private String coreId;
    private boolean valid;

    public PacketRemoveCore() {
    }

    public PacketRemoveCore(WandTarget target, ResourceLocation coreId) {
        if (target == null || target.getHand() == null || coreId == null) {
            throw new IllegalArgumentException("Invalid core removal packet");
        }
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.coreId = coreId.toString();
        this.valid = true;
    }

    private static boolean isSlotValid(EnumHand hand, int slot) {
        return hand == EnumHand.MAIN_HAND ? slot >= 0 && slot < 9 : slot == WandTarget.OFFHAND_SLOT;
    }

    private static IWandCore coreOf(String id) {
        if (id == null) {
            return null;
        }
        ResourceLocation key;
        try {
            key = new ResourceLocation(id);
        } catch (RuntimeException exception) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item instanceof IWandCore ? (IWandCore) item : null;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        hand = null;
        coreId = null;
        try {
            if (buf.readableBytes() < 5 || buf.readUnsignedByte() != PROTOCOL_VERSION) {
                return;
            }
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            coreId = NetworkProtocol.readBoundedString(buf, MAX_ID_BYTES);
            valid = hand != null && isSlotValid(hand, slot) && coreOf(coreId) != null && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid core removal packet");
        }
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        NetworkProtocol.writeBoundedString(buf, coreId, MAX_ID_BYTES);
    }

    public static class Handler implements IMessageHandler<PacketRemoveCore, IMessage> {
        private static void apply(PacketRemoveCore message, EntityPlayerMP player) {
            IWandCore core = coreOf(message.coreId);
            if (core == null) {
                return;
            }
            WandTarget target;
            try {
                target = new WandTarget(message.hand, message.slot);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            ItemStack wand = target.resolve(player);
            if (wand.isEmpty() || !(wand.getItem() instanceof ItemWand) || !WandDataCodec.removeUpgrade(wand, core)) {
                return;
            }

            ItemStack removed = new ItemStack((Item) core);
            if (!player.inventory.addItemStackToInventory(removed)) {
                player.dropItem(removed, false);
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketRemoveCore message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
