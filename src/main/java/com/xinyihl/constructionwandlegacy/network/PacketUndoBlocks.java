package com.xinyihl.constructionwandlegacy.network;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class PacketUndoBlocks implements IMessage {
    private Set<BlockPos> undoBlocks = Collections.emptySet();
    private boolean valid;

    public PacketUndoBlocks() {
    }

    public PacketUndoBlocks(Set<BlockPos> undoBlocks) {
        if (undoBlocks == null || undoBlocks.size() > NetworkProtocol.MAX_UNDO_BLOCKS) {
            throw new IllegalArgumentException("Undo block count is out of bounds");
        }
        this.undoBlocks = immutableCopy(undoBlocks);
        this.valid = true;
    }

    private static Set<BlockPos> immutableCopy(Set<BlockPos> blocks) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        undoBlocks = Collections.emptySet();
        try {
            if (!NetworkProtocol.readHeader(buf) || buf.readableBytes() < 2) {
                return;
            }
            int size = buf.readUnsignedShort();
            if (size > NetworkProtocol.MAX_UNDO_BLOCKS || buf.readableBytes() != size * Long.BYTES) {
                return;
            }
            LinkedHashSet<BlockPos> decoded = new LinkedHashSet<>(size);
            for (int index = 0; index < size; index++) {
                decoded.add(BlockPos.fromLong(buf.readLong()));
            }
            undoBlocks = immutableCopy(decoded);
            valid = true;
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            undoBlocks = Collections.emptySet();
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid undo packet");
        }
        NetworkProtocol.writeHeader(buf);
        buf.writeShort(undoBlocks.size());
        for (BlockPos pos : undoBlocks) {
            buf.writeLong(pos.toLong());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public Set<BlockPos> getUndoBlocks() {
        return undoBlocks;
    }

    public static class Handler implements IMessageHandler<PacketUndoBlocks, IMessage> {
        @Override
        public IMessage onMessage(PacketUndoBlocks message, MessageContext ctx) {
            if (message.valid) {
                ConstructionWandLegacy.proxy.handleUndoBlocks(message.undoBlocks);
            }
            return null;
        }
    }
}
