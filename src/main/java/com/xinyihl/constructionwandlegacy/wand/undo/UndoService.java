package com.xinyihl.constructionwandlegacy.wand.undo;

import com.xinyihl.constructionwandlegacy.material.MaterialReceipt;
import com.xinyihl.constructionwandlegacy.network.ModMessages;
import com.xinyihl.constructionwandlegacy.network.PacketUndoBlocks;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.WandTransaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public final class UndoService {
    private static final int HISTORY_SIZE = 10;

    private final Map<UUID, PlayerEntry> history = new HashMap<>();
    private final Logger logger;

    public UndoService(Logger logger) {
        this.logger = logger;
    }

    public void record(EntityPlayer player, WandTransaction transaction) {
        record(player.getUniqueID(), transaction);
        refreshClientIfActive(player);
    }

    void record(UUID playerId, WandTransaction transaction) {
        if (transaction == null || transaction.isEmpty()) {
            return;
        }
        Deque<WandTransaction> entries = getEntry(playerId).entries;
        entries.addLast(transaction);
        while (entries.size() > HISTORY_SIZE) {
            entries.removeFirst();
        }
    }

    /**
     * Stores a recovery that could not safely refund because world restoration failed.
     */
    public void recordPending(EntityPlayer player, WandOperation.AppliedChange change, @Nullable MaterialReceipt receipt, boolean worldRestored) {
        getEntry(player.getUniqueID()).pending.addLast(new PendingEntry(player.world.provider.getDimension(), change, receipt == null ? MaterialReceipt.empty() : receipt, worldRestored));
    }

    /**
     * Retries pending world recovery/refunds against the player's current world.
     */
    public boolean retryPending(EntityPlayer player) {
        PlayerEntry entry = getEntry(player.getUniqueID());
        boolean changed = false;
        Iterator<PendingEntry> iterator = entry.pending.iterator();
        while (iterator.hasNext()) {
            PendingEntry pending = iterator.next();
            if (pending.dimension != player.world.provider.getDimension()) {
                continue;
            }
            if (!pending.worldRestored) {
                WandOperation.RollbackResult result;
                try {
                    result = pending.change.rollback(player.world);
                } catch (RuntimeException exception) {
                    result = WandOperation.RollbackResult.failed("pending world recovery threw", exception);
                }
                if (!result.isRestored()) {
                    logRecoveryFailure(pending.change.getPos(), result);
                    continue;
                }
                pending.worldRestored = true;
                changed = true;
            }
            if (!pending.materialRefunded) {
                try {
                    pending.receipt.refund();
                    pending.materialRefunded = pending.receipt.getRemainingCount() == 0;
                } catch (RuntimeException exception) {
                    logger.error("Pending material refund failed at {}", pending.change.getPos(), exception);
                }
            }
            if (pending.worldRestored && pending.materialRefunded) {
                iterator.remove();
            }
        }
        return changed;
    }

    public boolean undo(EntityPlayer player) {
        PlayerEntry entry = getEntry(player.getUniqueID());
        if (!entry.undoActive) return false;
        boolean changed = undo(player.getUniqueID(), player.world.provider.getDimension(), player.world, player);
        refreshClientIfActive(player);
        return changed;
    }

    boolean undo(UUID playerId, int dimension, @Nullable World world, @Nullable EntityPlayer player) {
        Deque<WandTransaction> entries = getEntry(playerId).entries;
        WandTransaction transaction = entries.peekLast();
        if (transaction == null || transaction.getDimension() != dimension) {
            return false;
        }

        WandTransaction.RecoveryResult result = transaction.recover(world, player);
        if (!result.isComplete() && result.getFailure() != null) {
            logger.error("Wand transaction recovery was not completed: {}", result.getFailure().getMessage(), result.getFailure().getCause());
        }
        if (result.isComplete() && transaction.isComplete()) {
            entries.removeLast();
        }
        return result.didRestoreWorld();
    }

    public Set<BlockPos> peekLastPositions(EntityPlayer player) {
        UUID playerId = player.getUniqueID();
        int dimension = player.world.provider.getDimension();
        WandTransaction transaction = getEntry(playerId).entries.peekLast();
        if (transaction == null || transaction.getDimension() != dimension) {
            return Collections.emptySet();
        }
        return transaction.getPositions();
    }

    public void updateClient(EntityPlayerMP player, boolean undoPressed) {
        PlayerEntry entry = getEntry(player.getUniqueID());
        entry.undoActive = undoPressed;
        ModMessages.sendToPlayer(new PacketUndoBlocks(peekLastPositions(player)), player);
    }

    public void clearHistory(UUID playerId) {
        history.remove(playerId);
    }

    private void logRecoveryFailure(BlockPos pos, WandOperation.RollbackResult result) {
        if (result.getCause() == null) {
            logger.error("Wand recovery at {} was not completed: {}", pos, result.getMessage());
        } else {
            logger.error("Wand recovery at {} failed: {}", pos, result.getMessage(), result.getCause());
        }
    }

    private PlayerEntry getEntry(UUID playerId) {
        return history.computeIfAbsent(playerId, ignored -> new PlayerEntry());
    }

    private void refreshClientIfActive(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        PlayerEntry entry = getEntry(player.getUniqueID());
        if (entry.undoActive) {
            ModMessages.sendToPlayer(new PacketUndoBlocks(peekLastPositions(player)), (EntityPlayerMP) player);
        }
    }

    private static final class PlayerEntry {
        private final Deque<WandTransaction> entries = new ArrayDeque<>();
        private final Deque<PendingEntry> pending = new ArrayDeque<>();
        private boolean undoActive;
    }

    private static final class PendingEntry {
        private final int dimension;
        private final WandOperation.AppliedChange change;
        private final MaterialReceipt receipt;
        private boolean worldRestored;
        private boolean materialRefunded;

        private PendingEntry(int dimension, WandOperation.AppliedChange change, MaterialReceipt receipt, boolean worldRestored) {
            this.dimension = dimension;
            this.change = change;
            this.receipt = receipt;
            this.worldRestored = worldRestored;
            this.materialRefunded = receipt.getRemainingCount() == 0;
        }
    }
}
