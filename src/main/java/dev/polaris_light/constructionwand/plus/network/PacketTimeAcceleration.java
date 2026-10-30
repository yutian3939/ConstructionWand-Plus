package dev.polaris_light.constructionwand.plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.client.TimeClientState;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;

import java.util.ArrayList;
import java.util.List;

/**
 * Every block the time core has accelerated, sent to the clients that draw their highlight and read their
 * speed.
 * <p>
 * The whole table is sent whenever it changes, which is on a click and when an effect runs out, so the
 * message stays small enough to be sent to everyone. The remaining time is counted down on the client in
 * between, so nothing has to be sent per tick.
 */
public record PacketTimeAcceleration(List<TimeAcceleration.Entry> entries) implements CustomPacketPayload
{
    private static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<FriendlyByteBuf, PacketTimeAcceleration> CODEC = CustomPacketPayload.codec(
            PacketTimeAcceleration::encode,
            PacketTimeAcceleration::new);
    public static final Type<PacketTimeAcceleration> ID = new Type<>(ConstructionWand.loc("time_acceleration"));

    public PacketTimeAcceleration(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static List<TimeAcceleration.Entry> read(FriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<TimeAcceleration.Entry> entries = new ArrayList<>(size);
        for(int index = 0; index < size; index++) {
            ResourceLocation dimension = buffer.readResourceLocation();
            BlockPos pos = buffer.readBlockPos();
            int rate = buffer.readVarInt();
            int remaining = buffer.readVarInt();
            int total = buffer.readVarInt();
            float heading = buffer.readFloat();
            String blockId = buffer.readUtf();
            entries.add(new TimeAcceleration.Entry(ResourceKey.create(Registries.DIMENSION, dimension), pos, rate,
                    remaining, total, heading, blockId));
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for(TimeAcceleration.Entry entry : entries) {
            buffer.writeResourceLocation(entry.dimension().location());
            buffer.writeBlockPos(entry.pos());
            buffer.writeVarInt(entry.rate());
            buffer.writeVarInt(entry.remaining());
            buffer.writeVarInt(entry.total());
            buffer.writeFloat(entry.heading());
            buffer.writeUtf(entry.blockId() == null ? "" : entry.blockId());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler
    {
        public static void handle(final PacketTimeAcceleration msg, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                        if(ctx.flow().isServerbound()) return;
                        TimeClientState.accept(msg.entries());
                    })
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.time_acceleration.failed",
                                e.getMessage()));
                        return null;
                    });
        }
    }

    /**
     * @return whether the entry belongs to the dimension the player is in
     */
    public static boolean inDimension(TimeAcceleration.Entry entry, Level level) {
        return level != null && entry.dimension().equals(level.dimension());
    }
}
