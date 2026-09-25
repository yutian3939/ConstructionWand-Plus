package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
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
 * Client to server request to take one passive upgrade component off the wand, sent from the wand
 * configuration screen. Installation works like it does for the cores, by crafting the wand together
 * with the component.
 */
public class PacketRemoveUpgrade implements IMessage {
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_ID_BYTES = 128;

    private EnumHand hand;
    private int slot;
    private String upgradeId;
    private boolean valid;

    public PacketRemoveUpgrade() {
    }

    public PacketRemoveUpgrade(WandTarget target, ResourceLocation upgradeId) {
        if (target == null || target.getHand() == null || upgradeId == null) {
            throw new IllegalArgumentException("Invalid upgrade removal packet");
        }
        this.hand = target.getHand();
        this.slot = target.getSlot();
        this.upgradeId = upgradeId.toString();
        this.valid = true;
    }

    private static boolean isSlotValid(EnumHand hand, int slot) {
        return hand == EnumHand.MAIN_HAND ? slot >= 0 && slot < 9 : slot == WandTarget.OFFHAND_SLOT;
    }

    private static IWandUpgrade upgradeOf(String id) {
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
        return item instanceof IWandUpgrade ? (IWandUpgrade) item : null;
    }

    private static void giveToPlayer(EntityPlayerMP player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        hand = null;
        upgradeId = null;
        try {
            if (buf.readableBytes() < 5 || buf.readUnsignedByte() != PROTOCOL_VERSION) {
                return;
            }
            int handId = buf.readUnsignedByte();
            hand = handId == 0 ? EnumHand.MAIN_HAND : handId == 1 ? EnumHand.OFF_HAND : null;
            slot = buf.readUnsignedByte();
            upgradeId = NetworkProtocol.readBoundedString(buf, MAX_ID_BYTES);
            valid = hand != null && isSlotValid(hand, slot) && upgradeOf(upgradeId) != null && !buf.isReadable();
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid upgrade removal packet");
        }
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeByte(slot);
        NetworkProtocol.writeBoundedString(buf, upgradeId, MAX_ID_BYTES);
    }

    public static class Handler implements IMessageHandler<PacketRemoveUpgrade, IMessage> {
        private static void apply(PacketRemoveUpgrade message, EntityPlayerMP player) {
            IWandUpgrade upgrade = upgradeOf(message.upgradeId);
            if (!(upgrade instanceof Item)) {
                return;
            }
            WandTarget target;
            try {
                target = new WandTarget(message.hand, message.slot);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            ItemStack wand = target.resolve(player);
            if (wand.isEmpty() || !(wand.getItem() instanceof ItemWand) || !WandDataCodec.removeUpgrade(wand, upgrade)) {
                return;
            }

            giveToPlayer(player, new ItemStack((Item) upgrade));
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        @Override
        public IMessage onMessage(PacketRemoveUpgrade message, MessageContext ctx) {
            if (!message.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }
    }
}
