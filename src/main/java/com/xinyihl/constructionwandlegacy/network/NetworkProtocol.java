package com.xinyihl.constructionwandlegacy.network;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public final class NetworkProtocol {
    public static final int VERSION = 2;
    public static final int MAX_UNDO_BLOCKS = 4096;

    private NetworkProtocol() {
    }

    public static void writeHeader(ByteBuf buf) {
        buf.writeByte(VERSION);
    }

    public static boolean readHeader(ByteBuf buf) {
        return buf.isReadable() && buf.readUnsignedByte() == VERSION;
    }

    public static void writeBoundedString(ByteBuf buf, String value, int maxBytes) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBytes) {
            throw new IllegalArgumentException("String exceeds network bound");
        }
        buf.writeShort(encoded.length);
        buf.writeBytes(encoded);
    }

    public static String readBoundedString(ByteBuf buf, int maxBytes) {
        if (buf.readableBytes() < 2) {
            throw new IllegalArgumentException("Missing string length");
        }
        int length = buf.readUnsignedShort();
        if (length > maxBytes || buf.readableBytes() < length) {
            throw new IllegalArgumentException("String length is out of bounds");
        }
        byte[] encoded = new byte[length];
        buf.readBytes(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
