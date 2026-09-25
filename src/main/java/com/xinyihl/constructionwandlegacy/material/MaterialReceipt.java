package com.xinyihl.constructionwandlegacy.material;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Records exact extraction endpoints. Combined and component receipts share entry state, so an
 * extraction can never be refunded twice through different receipt views.
 */
public final class MaterialReceipt {
    private static final MaterialReceipt EMPTY = new MaterialReceipt(Collections.emptyList());

    private final List<Entry> entries;
    private final int count;

    private MaterialReceipt(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        int total = 0;
        for (Entry entry : entries) {
            total = SaturatedAmounts.add(total, entry.count);
        }
        this.count = total;
    }

    public static MaterialReceipt empty() {
        return EMPTY;
    }

    public static MaterialReceipt of(String sourceId, MaterialKey key, int count, RefundTarget refundTarget) {
        if (count <= 0) {
            return empty();
        }
        return new MaterialReceipt(Collections.singletonList(new Entry(sourceId, key, count, refundTarget)));
    }

    public static MaterialReceipt combine(List<MaterialReceipt> receipts) {
        List<Entry> combined = new ArrayList<>();
        Map<Entry, Boolean> seen = new IdentityHashMap<>();
        for (MaterialReceipt receipt : receipts) {
            if (receipt == null || receipt == EMPTY) {
                continue;
            }
            for (Entry entry : receipt.entries) {
                if (seen.put(entry, Boolean.TRUE) == null) {
                    combined.add(entry);
                }
            }
        }
        return combined.isEmpty() ? empty() : new MaterialReceipt(combined);
    }

    public int getCount() {
        return count;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Attempts every pending entry in reverse extraction order. Failures remain retryable and do
     * not prevent later entries from running. Returns true only when this call did work and all
     * entries are now fully refunded.
     */
    public boolean refund() {
        boolean attempted = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            attempted |= entries.get(index).attemptRefund();
        }
        if (!attempted) {
            return false;
        }
        for (Entry entry : entries) {
            if (!entry.isRefunded()) {
                return false;
            }
        }
        return true;
    }

    public boolean isRefunded() {
        for (Entry entry : entries) {
            if (!entry.isRefunded()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the amount that could not yet be delivered by any refund target.
     */
    public int getRemainingCount() {
        int remaining = 0;
        for (Entry entry : entries) {
            remaining = SaturatedAmounts.add(remaining, entry.getRemainingCount());
        }
        return remaining;
    }

    @FunctionalInterface
    public interface RefundTarget {
        /**
         * Tries to deliver {@code count} items and returns the undelivered amount. The return value
         * must be between zero and {@code count}; a non-zero value remains retryable.
         */
        int refund(MaterialKey key, int count);
    }

    public static final class Entry {
        private final String sourceId;
        private final MaterialKey key;
        private final int count;
        private final RefundTarget refundTarget;
        private int remaining;
        @Nullable
        private RuntimeException lastFailure;

        private Entry(String sourceId, MaterialKey key, int count, RefundTarget refundTarget) {
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
            this.key = Objects.requireNonNull(key, "key");
            this.count = count;
            this.remaining = count;
            this.refundTarget = Objects.requireNonNull(refundTarget, "refundTarget");
        }

        public String getSourceId() {
            return sourceId;
        }

        public MaterialKey getKey() {
            return key;
        }

        public int getCount() {
            return count;
        }

        public synchronized int getRemainingCount() {
            return remaining;
        }

        public synchronized boolean isRefunded() {
            return remaining == 0;
        }

        @Nullable
        public synchronized RuntimeException getLastFailure() {
            return lastFailure;
        }

        private synchronized boolean attemptRefund() {
            if (remaining == 0) {
                return false;
            }
            int requested = remaining;
            try {
                int next = refundTarget.refund(key, requested);
                if (next < 0 || next > requested) {
                    throw new IllegalStateException("Refund target returned invalid remainder " + next + " for request " + requested);
                }
                remaining = next;
                lastFailure = null;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
            return true;
        }
    }
}
