package thetadev.constructionwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import thetadev.constructionwand.basics.ServerRuleCache;
import thetadev.constructionwand.basics.ServerRules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sends the server side wand rules to a client.
 * <p>
 * The values are the ones the wand screen, the preview and the placement matching read, so the
 * client can behave exactly like the server it is connected to. Every variable length field is
 * bounded on both sides so a broken packet can only be rejected, never allocate or read too much.
 */
public class PacketServerRules
{
    public final ServerRules rules;

    public PacketServerRules(ServerRules rules) {
        this.rules = rules;
    }

    private PacketServerRules() {
        this.rules = null;
    }

    public static void encode(PacketServerRules msg, FriendlyByteBuf buffer) {
        if(msg.rules == null) throw new IllegalStateException("Cannot encode empty server rules");

        ServerRules rules = msg.rules;
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

    public static PacketServerRules decode(FriendlyByteBuf buffer) {
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

            if(buffer.isReadable()) return new PacketServerRules();

            ServerRules rules = new ServerRules(revision, wands, teWhitelist, teList, placementWhitelist,
                    placementBlacklist, propertyCopyWhitelist, similarBlocks, slayStone, slayIron, slayDiamond, slayInfinity);
            return new PacketServerRules(rules);
        }
        catch(RuntimeException exception) {
            // A malformed packet is simply dropped; the client keeps using its local configuration.
            return new PacketServerRules();
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
        java.util.ArrayList<String> result = new java.util.ArrayList<>(count);
        for(int index = 0; index < count; index++) result.add(buffer.readUtf(ServerRules.MAX_STRING_CHARS));
        return result;
    }

    public static class Handler
    {
        public static void handle(final PacketServerRules msg, final Supplier<NetworkEvent.Context> ctx) {
            if(msg.rules == null) return;

            ctx.get().enqueueWork(() -> ServerRuleCache.apply(msg.rules));
            ctx.get().setPacketHandled(true);
        }
    }
}
