package com.xinyihl.constructionwandlegacy.material;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A no-side-effect planning reservation that can be committed exactly once.
 */
public final class MaterialReservation {
    private final MaterialSession session;
    private final MaterialKey key;
    private final int count;
    private final List<Allocation> allocations;
    private boolean finished;
    @Nullable
    private MaterialReceipt receipt;
    @Nullable
    private MaterialReceipt rollbackReceipt;

    MaterialReservation(MaterialSession session, MaterialKey key, int count, List<Allocation> allocations) {
        this.session = session;
        this.key = key;
        this.count = count;
        this.allocations = Collections.unmodifiableList(new ArrayList<>(allocations));
    }

    private static MaterialReceipt rollback(List<MaterialReceipt> extracted) {
        MaterialReceipt rollback = MaterialReceipt.combine(extracted);
        rollback.refund();
        return rollback;
    }

    public MaterialKey getKey() {
        return key;
    }

    public int getCount() {
        return count;
    }

    @Nullable
    public synchronized MaterialReceipt commit() {
        if (finished) {
            if (receipt == null && rollbackReceipt != null) {
                rollbackReceipt.refund();
            }
            return receipt;
        }

        if (session.isFreeExtraction()) {
            receipt = MaterialReceipt.empty();
            session.finish(this, true);
            finished = true;
            return receipt;
        }

        List<MaterialReceipt> extracted = new ArrayList<>();
        int total = 0;
        boolean committed = false;
        try {
            for (Allocation allocation : allocations) {
                MaterialReceipt part;
                try {
                    part = allocation.source.extract(key, allocation.count);
                } catch (RuntimeException exception) {
                    part = MaterialReceipt.empty();
                }
                if (part != null && part.getCount() > 0) {
                    extracted.add(part);
                    total = SaturatedAmounts.add(total, part.getCount());
                }
                if (part == null || part.getCount() != allocation.count) {
                    rollbackReceipt = rollback(extracted);
                    return null;
                }
            }

            if (total != count) {
                rollbackReceipt = rollback(extracted);
                return null;
            }

            receipt = MaterialReceipt.combine(extracted);
            committed = true;
            return receipt;
        } finally {
            session.finish(this, committed);
            finished = true;
        }
    }

    public synchronized void cancel() {
        if (finished) {
            return;
        }
        session.finish(this, false);
        finished = true;
    }

    /**
     * Returns a receipt for any rollback entries that still need a retry.
     */
    @Nullable
    public synchronized MaterialReceipt getRollbackReceipt() {
        return rollbackReceipt;
    }

    public synchronized boolean retryRollback() {
        return rollbackReceipt != null && rollbackReceipt.refund();
    }

    List<Allocation> getAllocations() {
        return allocations;
    }

    static final class Allocation {
        final MaterialSource source;
        final int count;

        Allocation(MaterialSource source, int count) {
            this.source = source;
            this.count = count;
        }
    }
}
