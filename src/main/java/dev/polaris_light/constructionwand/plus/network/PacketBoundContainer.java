package dev.polaris_light.constructionwand.plus.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Changes the bound container list from the wand screen: raise or lower a binding in the priority
 * order, drop it on the position it was dragged to, or remove it. The binding is addressed by its
 * position, so a stale screen can only move entries that really exist.
 */
public record PacketBoundContainer(int action, BlockPos pos, int index) implements CustomPacketPayload
{
    public static final int ACTION_UP = 0;
    public static final int ACTION_DOWN = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_MOVE = 3;

    public static final StreamCodec<FriendlyByteBuf, PacketBoundContainer> CODEC = CustomPacketPayload.codec(
            PacketBoundContainer::encode,
            PacketBoundContainer::new);
    public static final Type<PacketBoundContainer> ID = new Type<>(ConstructionWand.loc("bound_container"));

    public PacketBoundContainer(int action, BlockPos pos) {
        this(action, pos, 0);
    }

    public PacketBoundContainer(FriendlyByteBuf buffer) {
        this(buffer.readUnsignedByte(), buffer.readBlockPos(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketBoundContainer msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;

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
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.bound_container.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
