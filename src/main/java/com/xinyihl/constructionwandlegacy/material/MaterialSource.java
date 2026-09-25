package com.xinyihl.constructionwandlegacy.material;

/**
 * One concrete material store captured for the lifetime of a planning/execution session.
 */
public interface MaterialSource {
    String getId();

    /**
     * Enumerates this source once. Implementations may cache exact slots for later extraction.
     */
    void enumerate(MaterialCollector collector);

    /**
     * Performs a real extraction and returns a receipt for the amount actually removed.
     */
    MaterialReceipt extract(MaterialKey key, int count);

    /**
     * Applies source-wide capacity constraints to cached per-key availability.
     */
    default int availableCapacity(MaterialKey key, int cachedAvailable) {
        return cachedAvailable;
    }

    /**
     * Reserves up to {@code requested} units from source-wide capacity. The default implementation
     * preserves independent per-key source behavior.
     */
    default int reserveCapacity(MaterialKey key, int requested, int cachedAvailable) {
        return Math.min(requested, cachedAvailable);
    }

    /**
     * Releases source-wide capacity, recording it as spent only after a successful commit.
     */
    default void finishReservation(MaterialKey key, int count, boolean committed) {
    }
}
