package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.config.RuleSnapshot;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientState {
    private volatile PreviewSnapshot preview = PreviewSnapshot.empty();
    private volatile Set<BlockPos> undoBlocks = Collections.emptySet();
    @Nullable
    private volatile ConfigRuntime.Snapshot serverRules;
    private volatile long serverRulesRevision = Long.MIN_VALUE;

    public PreviewSnapshot getPreview() {
        return preview;
    }

    public void setPreview(PreviewSnapshot preview) {
        this.preview = preview == null ? PreviewSnapshot.empty() : preview;
    }

    public Set<BlockPos> getUndoBlocks() {
        return undoBlocks;
    }

    public void replaceUndoBlocks(Set<BlockPos> blocks) {
        undoBlocks = blocks == null || blocks.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
    }

    public synchronized boolean applyServerRules(RuleSnapshot rules) {
        if (rules == null || rules.getRevision() <= serverRulesRevision) {
            return false;
        }
        ConfigRuntime.Snapshot compiled = ConfigRuntime.compile(rules);
        serverRules = compiled;
        serverRulesRevision = rules.getRevision();
        return true;
    }

    @Nullable
    public ConfigRuntime.Snapshot getServerRules() {
        return serverRules;
    }

    public long getServerRulesRevision() {
        return serverRulesRevision;
    }

    public synchronized void resetConnection() {
        preview = PreviewSnapshot.empty();
        undoBlocks = Collections.emptySet();
        serverRules = null;
        serverRulesRevision = Long.MIN_VALUE;
    }

    /**
     * Clears world-local positions while retaining the rules for a dimension change.
     */
    public synchronized void resetWorld() {
        preview = PreviewSnapshot.empty();
        undoBlocks = Collections.emptySet();
    }
}
