package com.xinyihl.constructionwandlegacy.wand;

import com.xinyihl.constructionwandlegacy.material.MaterialReceipt;
import com.xinyihl.constructionwandlegacy.material.MaterialReservation;
import com.xinyihl.constructionwandlegacy.lighting.DeferredLightingBatch;
import com.xinyihl.constructionwandlegacy.wand.undo.UndoService;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public final class WandExecutor {
    private final UndoService undoService;
    private final Logger logger;

    public WandExecutor(UndoService undoService, Logger logger) {
        this.undoService = undoService;
        this.logger = logger;
    }

    @Nullable
    private static MaterialReceipt commit(@Nullable MaterialReservation reservation) {
        return reservation == null ? MaterialReceipt.empty() : reservation.commit();
    }

    @Nullable
    private static MaterialReceipt rollbackReceipt(@Nullable MaterialReservation reservation) {
        return reservation == null ? null : reservation.getRollbackReceipt();
    }

    private static void cancel(@Nullable MaterialReservation reservation) {
        if (reservation != null) {
            reservation.cancel();
        }
    }

    public ExecutionResult execute(WandContext context, WandPlan plan) {
        try (DeferredLightingBatch ignored = DeferredLightingBatch.begin(context.getWorld())) {
            return execute(context, plan, new ContextAccess(context));
        }
    }

    ExecutionResult execute(@Nullable WandContext context, WandPlan plan, ExecutionAccess access) {
        if (access.isRemote()) {
            return ExecutionResult.failure("client worlds cannot execute wand plans");
        }
        WandPlan.ExecutionToken token = plan.claimExecution();
        if (token == null) {
            return ExecutionResult.failure("wand plan was discarded or already executed");
        }

        WandTransaction.Builder transaction = WandTransaction.builder(access.getDimension());
        int succeeded = 0;
        int index = 0;
        try {
            for (; index < plan.executionEntries().size(); index++) {
                WandPlan.Entry planned = plan.executionEntries().get(index);
                WandOperation operation = planned.operation();
                if (!access.canContinue()) {
                    break;
                }

                WandOperation.ApplyResult applied;
                try {
                    applied = operation.apply(context, token);
                } catch (RuntimeException exception) {
                    applied = WandOperation.ApplyResult.failed("unhandled operation exception", exception);
                }

                if (applied.getStatus() != WandOperation.ApplyResult.Status.APPLIED || applied.getChange() == null) {
                    cancel(planned.reservation());
                    if (applied.getStatus() == WandOperation.ApplyResult.Status.FAILED) {
                        logger.warn("Wand operation failed at {}: {}", operation.getPos(), applied.getMessage(), applied.getCause());
                    }
                    if (applied.getChange() != null && (applied.getRollbackResult() == null || !applied.getRollbackResult().isRestored())) {
                        recoverWorldThenRefund(applied.getChange(), null, operation.getPos(), access);
                    }
                    continue;
                }

                WandOperation.AppliedChange change = applied.getChange();
                MaterialReceipt receipt;
                try {
                    receipt = commit(planned.reservation());
                } catch (RuntimeException exception) {
                    logger.error("Material commit failed at {}", operation.getPos(), exception);
                    recoverWorldThenRefund(change, rollbackReceipt(planned.reservation()), operation.getPos(), access);
                    index++;
                    break;
                }
                if (receipt == null) {
                    recoverWorldThenRefund(change, rollbackReceipt(planned.reservation()), operation.getPos(), access);
                    continue;
                }

                try {
                    if (!access.isCreative()) {
                        access.damageWand();
                    }
                } catch (RuntimeException exception) {
                    logger.error("Failed to damage wand after operation at {}", operation.getPos(), exception);
                    recoverWorldThenRefund(change, receipt, operation.getPos(), access);
                    index++;
                    break;
                }

                transaction.add(change, receipt);
                succeeded++;
            }

            WandTransaction completed = transaction.build();
            if (!completed.isEmpty()) {
                access.record(completed);
            }
            return succeeded > 0 ? ExecutionResult.success(succeeded, completed) : ExecutionResult.failure("no wand operation succeeded");
        } finally {
            plan.cancelFrom(index);
            token.close();
        }
    }

    private void recoverWorldThenRefund(WandOperation.AppliedChange change, @Nullable MaterialReceipt receipt, BlockPos pos, ExecutionAccess access) {
        WandOperation.RollbackResult worldResult;
        try {
            worldResult = access.rollback(change);
        } catch (RuntimeException exception) {
            worldResult = WandOperation.RollbackResult.failed("exception during executor rollback", exception);
        }
        if (!worldResult.isRestored()) {
            logRecoveryFailure(pos, worldResult);
            access.recordPending(change, receipt, false);
            return;
        }

        if (receipt == null || receipt.getRemainingCount() == 0) {
            return;
        }
        try {
            receipt.refund();
        } catch (RuntimeException exception) {
            logger.error("Failed to refund material after rolling back {}", pos, exception);
        }
        if (receipt.getRemainingCount() > 0) {
            logger.error("Material rollback at {} still has {} undelivered item(s)", pos, receipt.getRemainingCount());
            access.recordPending(change, receipt, true);
        }
    }

    private void logRecoveryFailure(BlockPos pos, WandOperation.RollbackResult result) {
        if (result.getCause() == null) {
            logger.error("World rollback at {} was not completed: {}", pos, result.getMessage());
        } else {
            logger.error("World rollback at {} failed: {}", pos, result.getMessage(), result.getCause());
        }
    }

    interface ExecutionAccess {
        boolean isRemote();

        boolean canContinue();

        boolean isCreative();

        void damageWand();

        int getDimension();

        WandOperation.RollbackResult rollback(WandOperation.AppliedChange change);

        void record(WandTransaction transaction);

        void recordPending(WandOperation.AppliedChange change, @Nullable MaterialReceipt receipt, boolean worldRestored);
    }

    public static final class ExecutionResult {
        private final boolean success;
        private final int succeededOperations;
        @Nullable
        private final WandTransaction transaction;
        @Nullable
        private final String failureReason;

        private ExecutionResult(boolean success, int succeededOperations, @Nullable WandTransaction transaction, @Nullable String failureReason) {
            this.success = success;
            this.succeededOperations = succeededOperations;
            this.transaction = transaction;
            this.failureReason = failureReason;
        }

        private static ExecutionResult success(int succeededOperations, WandTransaction transaction) {
            return new ExecutionResult(true, succeededOperations, transaction, null);
        }

        private static ExecutionResult failure(String reason) {
            return new ExecutionResult(false, 0, null, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getSucceededOperations() {
            return succeededOperations;
        }

        @Nullable
        public WandTransaction getTransaction() {
            return transaction;
        }

        @Nullable
        public String getFailureReason() {
            return failureReason;
        }
    }

    private final class ContextAccess implements ExecutionAccess {
        private final WandContext context;

        private ContextAccess(WandContext context) {
            this.context = context;
        }

        @Override
        public boolean isRemote() {
            return context.getWorld().isRemote;
        }

        @Override
        public boolean canContinue() {
            return !context.getMutableWand().isEmpty() && context.getWandItem().remainingDurability(context.getMutableWand()) > 0;
        }

        @Override
        public boolean isCreative() {
            return context.getPlayer().isCreative();
        }

        @Override
        public void damageWand() {
            context.getMutableWand().damageItem(1, context.getPlayer());
        }

        @Override
        public int getDimension() {
            return context.getDimension();
        }

        @Override
        public WandOperation.RollbackResult rollback(WandOperation.AppliedChange change) {
            return change.rollback(context.getWorld());
        }

        @Override
        public void record(WandTransaction transaction) {
            undoService.record(context.getPlayer(), transaction);
        }

        @Override
        public void recordPending(WandOperation.AppliedChange change, @Nullable MaterialReceipt receipt, boolean worldRestored) {
            undoService.recordPending(context.getPlayer(), change, receipt, worldRestored);
        }
    }
}
