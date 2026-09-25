package com.xinyihl.constructionwandlegacy.wand;

import com.xinyihl.constructionwandlegacy.material.MaterialReservation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Read-only preview data plus a package-private, one-shot execution lease.
 */
public final class WandPlan {
    private final List<Entry> entries;
    private final Set<BlockPos> positions;
    private final List<Preview> previews;
    private boolean claimed;
    private boolean discarded;

    public WandPlan(List<? extends WandOperation> operations) {
        this(operations, Collections.emptyMap());
    }

    WandPlan(List<? extends WandOperation> operations, Map<? extends WandOperation, MaterialReservation> reservations) {
        List<Entry> mutableEntries = new ArrayList<>();
        LinkedHashSet<BlockPos> plannedPositions = new LinkedHashSet<>();
        List<Preview> plannedPreviews = new ArrayList<>();
        for (WandOperation operation : operations) {
            MaterialReservation reservation = reservations.get(operation);
            Entry entry = new Entry(operation, reservation);
            mutableEntries.add(entry);
            plannedPositions.add(operation.getPos());
            plannedPreviews.add(new Preview(operation.getPos(), operation.getPreviewState()));
        }
        this.entries = Collections.unmodifiableList(mutableEntries);
        this.positions = Collections.unmodifiableSet(plannedPositions);
        this.previews = Collections.unmodifiableList(plannedPreviews);
    }

    public Set<BlockPos> getBlockPositions() {
        return positions;
    }

    public List<Preview> getPreviews() {
        return previews;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Releases all planning reservations when this plan is only a preview. Idempotent.
     */
    public synchronized void discard() {
        if (claimed || discarded) {
            return;
        }
        discarded = true;
        cancelFrom(0);
    }

    @Nullable
    synchronized ExecutionToken claimExecution() {
        if (claimed || discarded) {
            return null;
        }
        claimed = true;
        return new ExecutionToken();
    }

    List<Entry> executionEntries() {
        return entries;
    }

    void cancelFrom(int startIndex) {
        for (int index = Math.max(0, startIndex); index < entries.size(); index++) {
            MaterialReservation reservation = entries.get(index).reservation;
            if (reservation != null) {
                reservation.cancel();
            }
        }
    }

    static final class Entry {
        private final WandOperation operation;
        @Nullable
        private final MaterialReservation reservation;

        private Entry(WandOperation operation, @Nullable MaterialReservation reservation) {
            this.operation = operation;
            this.reservation = reservation;
        }

        WandOperation operation() {
            return operation;
        }

        @Nullable
        MaterialReservation reservation() {
            return reservation;
        }
    }

    public static final class Preview {
        private final BlockPos pos;
        private final IBlockState state;

        private Preview(BlockPos pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }

        public BlockPos getPos() {
            return pos;
        }

        public IBlockState getState() {
            return state;
        }
    }

    /**
     * Unforgeable lease issued only through the package-private executor path.
     */
    public static final class ExecutionToken {
        private boolean active = true;

        private ExecutionToken() {
        }

        public boolean isActive() {
            return active;
        }

        void close() {
            active = false;
        }
    }
}
