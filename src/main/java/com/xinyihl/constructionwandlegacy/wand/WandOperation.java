package com.xinyihl.constructionwandlegacy.wand;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * An immutable planned world mutation.
 */
public interface WandOperation {
    BlockPos getPos();

    IBlockState getPreviewState();

    ApplyResult apply(WandContext context, WandPlan.ExecutionToken token);

    interface AppliedChange {
        BlockPos getPos();

        RollbackResult rollback(World world);

        RollbackResult restore(World world, EntityPlayer player);
    }

    final class RollbackResult {
        private final Status status;
        private final boolean refundMaterial;
        @Nullable
        private final String message;
        @Nullable
        private final RuntimeException cause;

        private RollbackResult(Status status, boolean refundMaterial, @Nullable String message, @Nullable RuntimeException cause) {
            this.status = status;
            this.refundMaterial = refundMaterial;
            this.message = message;
            this.cause = cause;
        }

        public static RollbackResult restored() {
            return new RollbackResult(Status.RESTORED, true, null, null);
        }

        public static RollbackResult alreadyRestored() {
            return new RollbackResult(Status.RESTORED, false, null, null);
        }

        public static RollbackResult notRestored(String message) {
            return new RollbackResult(Status.NOT_RESTORED, false, message, null);
        }

        public static RollbackResult failed(String message, RuntimeException cause) {
            return new RollbackResult(Status.FAILED, false, message, cause);
        }

        public Status getStatus() {
            return status;
        }

        public boolean isRestored() {
            return status == Status.RESTORED;
        }

        public boolean shouldRefundMaterial() {
            return refundMaterial;
        }

        @Nullable
        public String getMessage() {
            return message;
        }

        @Nullable
        public RuntimeException getCause() {
            return cause;
        }

        public enum Status {
            RESTORED, NOT_RESTORED, FAILED
        }
    }

    final class ApplyResult {
        private final Status status;
        @Nullable
        private final AppliedChange change;
        @Nullable
        private final RollbackResult rollbackResult;
        @Nullable
        private final String message;
        @Nullable
        private final RuntimeException cause;

        private ApplyResult(Status status, @Nullable AppliedChange change, @Nullable RollbackResult rollbackResult, @Nullable String message, @Nullable RuntimeException cause) {
            this.status = status;
            this.change = change;
            this.rollbackResult = rollbackResult;
            this.message = message;
            this.cause = cause;
        }

        public static ApplyResult applied(AppliedChange change) {
            return new ApplyResult(Status.APPLIED, change, null, null, null);
        }

        public static ApplyResult rejected(String message) {
            return new ApplyResult(Status.REJECTED, null, null, message, null);
        }

        public static ApplyResult failed(String message, RuntimeException cause) {
            return new ApplyResult(Status.FAILED, null, null, message, cause);
        }

        public static ApplyResult failedWithChange(String message, @Nullable RuntimeException cause, AppliedChange change, RollbackResult rollbackResult) {
            return new ApplyResult(Status.FAILED, change, rollbackResult, message, cause);
        }

        public Status getStatus() {
            return status;
        }

        @Nullable
        public AppliedChange getChange() {
            return change;
        }

        @Nullable
        public RollbackResult getRollbackResult() {
            return rollbackResult;
        }

        @Nullable
        public String getMessage() {
            return message;
        }

        @Nullable
        public RuntimeException getCause() {
            return cause;
        }

        public enum Status {
            APPLIED, REJECTED, FAILED
        }
    }
}
