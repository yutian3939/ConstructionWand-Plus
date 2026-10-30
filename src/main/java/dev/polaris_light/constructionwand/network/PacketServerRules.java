package dev.polaris_light.constructionwand.network;

import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.ServerRuleCache;
import dev.polaris_light.constructionwand.basics.ServerRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the server side wand rules to a client.
 * <p>
 * The values are the ones the wand screen, the preview and the placement matching read, so the
 * client can behave exactly like the server it is connected to. Every variable length field is
 * bounded on both sides so a broken packet can only be rejected, never allocate or read too much.
 * <p>
 * A payload decoder must never return {@code null}, so a packet that fails to decode becomes an
 * instance with {@code null} rules instead of no instance at all. {@code Handler} ignores such a
 * packet, which leaves the client on its local configuration - the same outcome the 1.20.1 version
 * had with its "empty" packet.
 */
public record PacketServerRules(@Nullable ServerRules rules) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, PacketServerRules> CODEC = CustomPacketPayload.codec(
            PacketServerRules::encode,
            PacketServerRules::new);
    public static final Type<PacketServerRules> ID = new Type<>(ConstructionWand.loc("server_rules"));

    public PacketServerRules(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        if(rules == null) throw new IllegalStateException("Cannot encode empty server rules");

        buffer.writeLong(rules.getRevision());

        Map<ResourceLocation, ServerRules.WandRule> wands = rules.getWands();
        buffer.writeByte(wands.size());
        for(Map.Entry<ResourceLocation, ServerRules.WandRule> entry : wands.entrySet()) {
            ServerRules.WandRule rule = entry.getValue();
            buffer.writeUtf(entry.getKey().toString(), ServerRules.MAX_STRING_CHARS);
            buffer.writeInt(rule.durability());
            buffer.writeInt(rule.limit());
            buffer.writeInt(rule.angel());
            buffer.writeInt(rule.destruction());
            buffer.writeBoolean(rule.upgradeable());
        }

        buffer.writeBoolean(rules.isTeWhitelist());
        writeList(buffer, rules.getTeList());
        writeList(buffer, rules.getPlacementWhitelist());
        writeList(buffer, rules.getPlacementBlacklist());
        writeList(buffer, rules.getPropertyCopyWhitelist());
        writeList(buffer, rules.getSimilarBlocks());

        buffer.writeInt(rules.getSlayStone());
        buffer.writeInt(rules.getSlayIron());
        buffer.writeInt(rules.getSlayDiamond());
        buffer.writeInt(rules.getSlayInfinity());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * @return the decoded rules, or {@code null} when the packet is malformed. Never throws, because
     * the codec has to produce an instance either way.
     */
    @Nullable
    private static ServerRules decode(FriendlyByteBuf buffer) {
        try {
            long revision = buffer.readLong();

            int wandCount = buffer.readUnsignedByte();
            Map<ResourceLocation, ServerRules.WandRule> wands = new LinkedHashMap<>();
            for(int index = 0; index < wandCount; index++) {
                ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(ServerRules.MAX_STRING_CHARS));
                ServerRules.WandRule rule = new ServerRules.WandRule(
                        buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
                if(id != null) wands.put(id, rule);
            }

            boolean teWhitelist = buffer.readBoolean();
            List<String> teList = readList(buffer);
            List<String> placementWhitelist = readList(buffer);
            List<String> placementBlacklist = readList(buffer);
            List<String> propertyCopyWhitelist = readList(buffer);
            List<String> similarBlocks = readList(buffer);

            int slayStone = buffer.readInt();
            int slayIron = buffer.readInt();
            int slayDiamond = buffer.readInt();
            int slayInfinity = buffer.readInt();

            if(buffer.isReadable()) return null;

            return new ServerRules(revision, wands, teWhitelist, teList, placementWhitelist,
                    placementBlacklist, propertyCopyWhitelist, similarBlocks, slayStone, slayIron, slayDiamond, slayInfinity);
        }
        catch(RuntimeException exception) {
            // A malformed packet is simply dropped; the client keeps using its local configuration.
            return null;
        }
    }

    private static void writeList(FriendlyByteBuf buffer, List<String> values) {
        List<String> safe = ServerRules.sanitize(values);
        buffer.writeShort(safe.size());
        for(String value : safe) buffer.writeUtf(value, ServerRules.MAX_STRING_CHARS);
    }

    private static List<String> readList(FriendlyByteBuf buffer) {
        int count = buffer.readUnsignedShort();
        if(count > ServerRules.MAX_LIST_ENTRIES) throw new IllegalArgumentException("Rule count is out of bounds");
        ArrayList<String> result = new ArrayList<>(count);
        for(int index = 0; index < count; index++) result.add(buffer.readUtf(ServerRules.MAX_STRING_CHARS));
        return result;
    }

    public static class Handler
    {
        public static void handle(final PacketServerRules msg, final IPayloadContext ctx) {
            // Without rules the packet failed to decode; the client then keeps its local config.
            if(msg.rules() == null) return;

            ctx.enqueueWork(() -> ServerRuleCache.apply(msg.rules()))
                    .exceptionally(e -> {
                        ctx.disconnect(Component.translatable("constructionwand.networking.server_rules.failed", e.getMessage()));
                        return null;
                    });
        }
    }
}
