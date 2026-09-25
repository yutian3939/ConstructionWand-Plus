package com.xinyihl.morewandcore.basics;

import javax.annotation.Nullable;

/**
 * How the wand deals with fluids.
 * <p>
 * {@link #SMART} is the original behaviour: only the fluid the removed blocks enclose is cleared, so
 * a pocket inside a wall cannot flood the hole while an ocean is left alone. {@link #ON} goes further
 * and treats fluids as ordinary blocks, so they are aimed at and matched like any other block.
 */
public enum FluidRemoval {
    OFF("off"),
    SMART("smart"),
    ON("on");

    private final String id;

    FluidRemoval(String id) {
        this.id = id;
    }

    public static String[] keys() {
        FluidRemoval[] values = values();
        String[] keys = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            keys[index] = values[index].id;
        }
        return keys;
    }

    @Nullable
    public static FluidRemoval byKey(String key) {
        if (key == null) {
            return null;
        }
        for (FluidRemoval mode : values()) {
            if (mode.id.equals(key)) {
                return mode;
            }
        }
        return null;
    }

    public static FluidRemoval byIndex(int index) {
        FluidRemoval[] values = values();
        return index >= 0 && index < values.length ? values[index] : OFF;
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return ordinal();
    }
}
