package com.xinyihl.constructionwandlegacy.client;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PreviewSnapshot {
    private static final PreviewSnapshot EMPTY = new PreviewSnapshot(null, Collections.emptySet(), PreviewColor.BLACK);

    @Nullable
    private final PreviewKey key;
    private final Set<BlockPos> blocks;
    private final PreviewColor color;

    private PreviewSnapshot(@Nullable PreviewKey key, Set<BlockPos> blocks, PreviewColor color) {
        this.key = key;
        this.blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
        this.color = color;
    }

    public static PreviewSnapshot empty() {
        return EMPTY;
    }

    public static PreviewSnapshot create(PreviewKey key, Set<BlockPos> blocks, PreviewColor color) {
        if (key == null || blocks == null || color == null) {
            throw new IllegalArgumentException("Preview snapshot values must not be null");
        }
        return new PreviewSnapshot(key, blocks, color);
    }

    @Nullable
    public PreviewKey getKey() {
        return key;
    }

    public PreviewKey.Mode getMode() {
        return key == null ? null : key.getMode();
    }

    public Set<BlockPos> getBlocks() {
        return blocks;
    }

    public PreviewColor getColor() {
        return color;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public static final class PreviewColor {
        public static final PreviewColor BLACK = new PreviewColor(0.0F, 0.0F, 0.0F);
        public static final PreviewColor GREEN = new PreviewColor(0.0F, 1.0F, 0.0F);

        private final float red;
        private final float green;
        private final float blue;

        private PreviewColor(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public static PreviewColor fromPacked(int color) {
            if (color < 0) {
                return BLACK;
            }
            return new PreviewColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
        }

        public float getRed() {
            return red;
        }

        public float getGreen() {
            return green;
        }

        public float getBlue() {
            return blue;
        }
    }
}
