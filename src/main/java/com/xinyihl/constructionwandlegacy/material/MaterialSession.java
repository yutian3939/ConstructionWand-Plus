package com.xinyihl.constructionwandlegacy.material;

import net.minecraft.item.Item;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Per-plan material view. Every source is enumerated once and all simulation uses this cache.
 */
public final class MaterialSession {
    private final List<MaterialSource> sources;
    private final Map<MaterialKey, Stock> stocks = new LinkedHashMap<>();
    private final boolean unlimited;
    private final boolean freeExtraction;

    public MaterialSession(List<MaterialSource> sources) {
        this(sources, false, false);
    }

    private MaterialSession(List<MaterialSource> sources, boolean unlimited, boolean freeExtraction) {
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.unlimited = unlimited;
        this.freeExtraction = freeExtraction;
        if (!sources.isEmpty()) {
            enumerateSources();
        }
    }

    public static MaterialSession unlimited() {
        return creativeUnlimited(Collections.emptyList());
    }

    public static MaterialSession creativeUnlimited(List<MaterialSource> sources) {
        return new MaterialSession(sources, true, true);
    }

    public static MaterialSession creativeCatalog(List<MaterialSource> sources) {
        return new MaterialSession(sources, false, true);
    }

    public int available(MaterialKey key) {
        if (unlimited) {
            return Integer.MAX_VALUE;
        }
        Stock stock = stocks.get(key);
        return stock == null ? 0 : stock.available(key);
    }

    public int simulate(MaterialKey key, int requested) {
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, available(key));
    }

    @Nullable
    public MaterialReservation reserve(MaterialKey key, int requested) {
        Objects.requireNonNull(key, "key");
        if (requested <= 0) {
            return null;
        }
        if (unlimited) {
            return new MaterialReservation(this, key, requested, Collections.emptyList());
        }

        Stock stock = stocks.get(key);
        if (stock == null || stock.available(key) < requested) {
            return null;
        }

        int remaining = requested;
        List<MaterialReservation.Allocation> allocations = new ArrayList<>();
        for (MaterialSource source : sources) {
            int sourceAvailable = stock.available(key, source);
            if (sourceAvailable <= 0) {
                continue;
            }
            int allocated;
            try {
                allocated = source.reserveCapacity(key, Math.min(remaining, sourceAvailable), sourceAvailable);
            } catch (RuntimeException exception) {
                allocated = 0;
            }
            if (allocated <= 0 || allocated > Math.min(remaining, sourceAvailable)) {
                if (allocated > 0) {
                    try {
                        source.finishReservation(key, allocated, false);
                    } catch (RuntimeException ignored) {
                    }
                }
                continue;
            }
            stock.reserve(source, allocated);
            allocations.add(new MaterialReservation.Allocation(source, allocated));
            remaining -= allocated;
            if (remaining == 0) {
                return new MaterialReservation(this, key, requested, allocations);
            }
        }

        for (MaterialReservation.Allocation allocation : allocations) {
            stock.release(key, allocation.source, allocation.count, false);
        }
        return null;
    }

    public List<MaterialKey> keysForItem(Item item) {
        List<MaterialKey> result = new ArrayList<>();
        for (Map.Entry<MaterialKey, Stock> entry : stocks.entrySet()) {
            if (entry.getKey().getItem() == item && entry.getValue().available(entry.getKey()) > 0) {
                result.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(result);
    }

    void finish(MaterialReservation reservation, boolean committed) {
        if (unlimited) {
            return;
        }
        Stock stock = stocks.get(reservation.getKey());
        if (stock == null) {
            return;
        }
        for (MaterialReservation.Allocation allocation : reservation.getAllocations()) {
            stock.release(reservation.getKey(), allocation.source, allocation.count, committed);
        }
    }

    boolean isFreeExtraction() {
        return freeExtraction;
    }

    private void enumerateSources() {
        for (MaterialSource source : sources) {
            if (source == null) {
                continue;
            }
            Map<MaterialKey, Integer> collected = new LinkedHashMap<>();
            try {
                source.enumerate((key, count) -> {
                    int amount = SaturatedAmounts.fromLong(count);
                    if (amount <= 0) {
                        return;
                    }
                    collected.put(key, SaturatedAmounts.add(collected.getOrDefault(key, 0), amount));
                });
            } catch (RuntimeException ignored) {
                // A failed optional source must not prevent the remaining sources from planning.
                continue;
            }
            for (Map.Entry<MaterialKey, Integer> entry : collected.entrySet()) {
                Stock stock = stocks.computeIfAbsent(entry.getKey(), ignored -> new Stock());
                stock.add(source, entry.getValue());
            }
        }
    }

    private static final class Stock {
        private final Map<MaterialSource, Integer> totals = new IdentityHashMap<>();
        private final Map<MaterialSource, Integer> reserved = new IdentityHashMap<>();

        private void add(MaterialSource source, int count) {
            totals.put(source, SaturatedAmounts.add(totals.getOrDefault(source, 0), count));
        }

        private int available(MaterialKey key) {
            int total = 0;
            for (MaterialSource source : totals.keySet()) {
                total = SaturatedAmounts.add(total, available(key, source));
            }
            return total;
        }

        private int available(MaterialKey key, MaterialSource source) {
            int cached = Math.max(0, totals.getOrDefault(source, 0) - reserved.getOrDefault(source, 0));
            try {
                return Math.max(0, Math.min(cached, source.availableCapacity(key, cached)));
            } catch (RuntimeException exception) {
                return 0;
            }
        }

        private void reserve(MaterialSource source, int count) {
            reserved.put(source, SaturatedAmounts.add(reserved.getOrDefault(source, 0), count));
        }

        private void release(MaterialKey key, MaterialSource source, int count, boolean committed) {
            int nextReserved = Math.max(0, reserved.getOrDefault(source, 0) - count);
            if (nextReserved == 0) {
                reserved.remove(source);
            } else {
                reserved.put(source, nextReserved);
            }
            if (committed) {
                int nextTotal = Math.max(0, totals.getOrDefault(source, 0) - count);
                if (nextTotal == 0) {
                    totals.remove(source);
                } else {
                    totals.put(source, nextTotal);
                }
            }
            try {
                source.finishReservation(key, count, committed);
            } catch (RuntimeException ignored) {
                // Source capacity is advisory; the concrete source remains the authority.
            }
        }
    }
}
