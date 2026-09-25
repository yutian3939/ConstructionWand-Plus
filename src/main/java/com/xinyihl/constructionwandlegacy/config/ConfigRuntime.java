package com.xinyihl.constructionwandlegacy.config;

import com.xinyihl.constructionwandlegacy.registry.BlockEquivalenceIndex;
import com.xinyihl.constructionwandlegacy.wand.WandTier;

import java.util.Collections;

public final class ConfigRuntime {
    private static long revision;
    private static volatile Snapshot current = compile(RuleSnapshot.create(0L, 64, 128, 512, 1024, false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

    private ConfigRuntime() {
    }

    public static synchronized void reload() {
        current = compile(RuleSnapshot.fromConfig(++revision));
    }

    public static PlacementRules getPlacementRules() {
        return current.placementRules;
    }

    public static BlockEquivalenceIndex getBlockEquivalenceIndex() {
        return current.blockEquivalenceIndex;
    }

    public static Snapshot getSnapshot() {
        return current;
    }

    public static Snapshot compile(RuleSnapshot rules) {
        String[] whitelist = rules.getPlacementWhitelist().toArray(new String[0]);
        String[] blacklist = rules.getPlacementBlacklist().toArray(new String[0]);
        String[] properties = rules.getPropertyCopyWhitelist().toArray(new String[0]);
        String[] similarBlocks = rules.getSimilarBlocks().toArray(new String[0]);
        PlacementRules placementRules = PlacementRules.compile(whitelist, blacklist, properties, rules.isTileEntityPlacementAllowed());
        BlockEquivalenceIndex equivalenceIndex = BlockEquivalenceIndex.compile(similarBlocks);
        return new Snapshot(rules, placementRules, equivalenceIndex);
    }

    public static final class Snapshot {
        private final RuleSnapshot wireRules;
        private final PlacementRules placementRules;
        private final BlockEquivalenceIndex blockEquivalenceIndex;

        private Snapshot(RuleSnapshot wireRules, PlacementRules placementRules, BlockEquivalenceIndex blockEquivalenceIndex) {
            this.wireRules = wireRules;
            this.placementRules = placementRules;
            this.blockEquivalenceIndex = blockEquivalenceIndex;
        }

        public long getRevision() {
            return wireRules.getRevision();
        }

        public int getPlacementLimit(WandTier tier) {
            return wireRules.getPlacementLimit(tier);
        }

        public RuleSnapshot getWireRules() {
            return wireRules;
        }

        public PlacementRules getPlacementRules() {
            return placementRules;
        }

        public BlockEquivalenceIndex getBlockEquivalenceIndex() {
            return blockEquivalenceIndex;
        }
    }
}
