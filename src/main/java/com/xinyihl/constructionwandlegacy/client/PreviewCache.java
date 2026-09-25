package com.xinyihl.constructionwandlegacy.client;

import java.util.Objects;
import java.util.function.Supplier;

final class PreviewCache {
    private long lastAttemptedClientTick = Long.MIN_VALUE;
    private PreviewKey key;
    private PreviewSnapshot snapshot = PreviewSnapshot.empty();

    PreviewSnapshot update(long clientTick, PreviewKey nextKey, Supplier<PreviewSnapshot> planner) {
        if (clientTick == lastAttemptedClientTick) {
            return snapshot;
        }
        lastAttemptedClientTick = clientTick;
        if (nextKey.equals(key)) {
            return snapshot;
        }
        PreviewSnapshot planned = Objects.requireNonNull(planner.get(), "planned preview");
        key = nextKey;
        snapshot = planned;
        return snapshot;
    }

    void reset() {
        lastAttemptedClientTick = Long.MIN_VALUE;
        key = null;
        snapshot = PreviewSnapshot.empty();
    }
}
