package com.xinyihl.constructionwandlegacy.material;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared long-valued budget for virtual sources such as ProjectE EMC.
 */
public final class SharedMaterialBudget {
    private final long total;
    private final Map<MaterialKey, Long> unitCosts = new HashMap<>();
    private long reserved;
    private long committed;

    public SharedMaterialBudget(long total) {
        this.total = Math.max(0L, total);
    }

    public synchronized void register(MaterialKey key, long unitCost) {
        if (unitCost > 0L) {
            unitCosts.put(key, unitCost);
        }
    }

    public synchronized int available(MaterialKey key, int cachedAvailable) {
        Long unitCost = unitCosts.get(key);
        if (unitCost == null || unitCost <= 0L || cachedAvailable <= 0) {
            return 0;
        }
        long remaining = Math.max(0L, total - committed - reserved);
        return Math.min(cachedAvailable, SaturatedAmounts.fromLong(remaining / unitCost));
    }

    public synchronized int reserve(MaterialKey key, int requested, int cachedAvailable) {
        int accepted = Math.min(requested, available(key, cachedAvailable));
        if (accepted <= 0) {
            return 0;
        }
        long cost = unitCosts.get(key) * accepted;
        reserved += cost;
        return accepted;
    }

    public synchronized void finish(MaterialKey key, int count, boolean wasCommitted) {
        Long unitCost = unitCosts.get(key);
        if (unitCost == null || count <= 0) {
            return;
        }
        long cost = unitCost * count;
        reserved = Math.max(0L, reserved - cost);
        if (wasCommitted) {
            committed = Math.min(total, committed + cost);
        }
    }

    public synchronized long getRemainingBudget() {
        return Math.max(0L, total - committed - reserved);
    }
}
