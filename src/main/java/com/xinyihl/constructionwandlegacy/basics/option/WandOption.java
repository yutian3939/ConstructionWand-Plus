package com.xinyihl.constructionwandlegacy.basics.option;

import com.xinyihl.constructionwandlegacy.Tags;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum WandOption {
    CORES(0, "cores"), LOCK(1, "lock"), DIRECTION(2, "direction"), REPLACE(3, "replace"), MATCH(4, "match"), RANDOM(5, "random"), MATERIAL_CORES(6, "material_cores");

    private static final Map<String, WandOption> BY_ID;

    static {
        Map<String, WandOption> byId = new HashMap<>();
        for (WandOption option : values()) {
            byId.put(option.id, option);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private final String id;
    private final int networkId;

    WandOption(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    @Nullable
    public static WandOption fromId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    @Nullable
    public static WandOption fromNetworkId(int id) {
        for (WandOption option : values()) {
            if (option.networkId == id) {
                return option;
            }
        }
        return null;
    }

    public int getNetworkId() {
        return networkId;
    }

    public String getId() {
        return id;
    }

    public String getKeyTranslation() {
        return Tags.MOD_ID + ".option." + id;
    }

    public String getValueTranslation(WandState state) {
        return getKeyTranslation() + "." + state.getValue(this);
    }

    public String getDescriptionTranslation(WandState state) {
        return getValueTranslation(state) + ".desc";
    }
}
