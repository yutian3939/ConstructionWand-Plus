package com.xinyihl.morewandcore.basics;

import javax.annotation.Nullable;

/**
 * Where the digging core puts the items it harvests.
 * <p>
 * The two non-ground values are priorities: the first store is filled first, whatever does not fit
 * goes to the second one and only the remainder falls to the ground. {@link #MATERIAL} uses the
 * wand's selected material core (the bound containers, the ME network, ...). A ProjectE core does not
 * store items: it turns everything with an EMC value into EMC.
 */
public enum DropDestination {
    INVENTORY("inventory"),
    MATERIAL("material"),
    GROUND("ground");

    private final String id;

    DropDestination(String id) {
        this.id = id;
    }

    public static String[] keys() {
        DropDestination[] values = values();
        String[] keys = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            keys[index] = values[index].id;
        }
        return keys;
    }

    @Nullable
    public static DropDestination byKey(String key) {
        if (key == null) {
            return null;
        }
        for (DropDestination destination : values()) {
            if (destination.id.equals(key)) {
                return destination;
            }
        }
        return null;
    }

    public static DropDestination byIndex(int index) {
        DropDestination[] values = values();
        return index >= 0 && index < values.length ? values[index] : INVENTORY;
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return ordinal();
    }
}
