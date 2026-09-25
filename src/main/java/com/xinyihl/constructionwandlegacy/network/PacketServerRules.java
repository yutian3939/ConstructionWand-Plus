package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.config.RuleSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class PacketServerRules implements IMessage {
    private RuleSnapshot rules;
    private boolean valid;

    public PacketServerRules() {
    }

    public PacketServerRules(RuleSnapshot rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }
        this.rules = rules;
        this.valid = true;
    }

    private static List<String> readRules(ByteBuf buf) {
        if (buf.readableBytes() < 2) {
            throw new IllegalArgumentException("Missing rule count");
        }
        int count = buf.readUnsignedShort();
        if (count > RuleSnapshot.MAX_RULE_ENTRIES) {
            throw new IllegalArgumentException("Rule count is out of bounds");
        }
        ArrayList<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(NetworkProtocol.readBoundedString(buf, RuleSnapshot.MAX_RULE_BYTES));
        }
        return result;
    }

    private static void writeRules(ByteBuf buf, List<String> values) {
        if (values.size() > RuleSnapshot.MAX_RULE_ENTRIES) {
            throw new IllegalArgumentException("Rule count is out of bounds");
        }
        buf.writeShort(values.size());
        for (String value : values) {
            NetworkProtocol.writeBoundedString(buf, value, RuleSnapshot.MAX_RULE_BYTES);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        rules = null;
        try {
            if (!NetworkProtocol.readHeader(buf) || buf.readableBytes() < 33) {
                return;
            }
            long revision = buf.readLong();
            if (revision < 0L) {
                return;
            }
            int stone = buf.readInt();
            int iron = buf.readInt();
            int diamond = buf.readInt();
            int infinity = buf.readInt();
            int allowTileEntitiesValue = buf.readUnsignedByte();
            if (allowTileEntitiesValue > 1) {
                return;
            }
            boolean allowTileEntities = allowTileEntitiesValue == 1;
            List<String> whitelist = readRules(buf);
            List<String> blacklist = readRules(buf);
            List<String> properties = readRules(buf);
            List<String> similar = readRules(buf);
            if (buf.isReadable()) {
                return;
            }
            rules = RuleSnapshot.create(revision, stone, iron, diamond, infinity, allowTileEntities, whitelist, blacklist, properties, similar);
            valid = true;
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            rules = null;
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid || rules == null) {
            throw new IllegalStateException("Cannot encode invalid server rules");
        }
        NetworkProtocol.writeHeader(buf);
        buf.writeLong(rules.getRevision());
        buf.writeInt(rules.getStoneLimit());
        buf.writeInt(rules.getIronLimit());
        buf.writeInt(rules.getDiamondLimit());
        buf.writeInt(rules.getInfinityLimit());
        buf.writeBoolean(rules.isTileEntityPlacementAllowed());
        writeRules(buf, rules.getPlacementWhitelist());
        writeRules(buf, rules.getPlacementBlacklist());
        writeRules(buf, rules.getPropertyCopyWhitelist());
        writeRules(buf, rules.getSimilarBlocks());
    }

    public boolean isValid() {
        return valid;
    }

    public RuleSnapshot getRules() {
        return rules;
    }

    public static class Handler implements IMessageHandler<PacketServerRules, IMessage> {
        @Override
        public IMessage onMessage(PacketServerRules message, MessageContext ctx) {
            if (message.valid) {
                ConstructionWandLegacy.proxy.handleServerRules(message.rules);
            }
            return null;
        }
    }
}
