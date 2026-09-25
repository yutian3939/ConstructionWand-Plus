package com.xinyihl.constructionwandlegacy.config;

import com.xinyihl.constructionwandlegacy.wand.WandTier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, bounded representation of the server rules sent to clients.
 */
public final class RuleSnapshot {
    public static final int MAX_RULE_ENTRIES = 1024;
    public static final int MAX_RULE_BYTES = 512;
    public static final int MIN_PLACEMENT_LIMIT = 1;
    public static final int MAX_PLACEMENT_LIMIT = 4096;

    private final long revision;
    private final int stoneLimit;
    private final int ironLimit;
    private final int diamondLimit;
    private final int infinityLimit;
    private final boolean allowTileEntityPlacement;
    private final List<String> placementWhitelist;
    private final List<String> placementBlacklist;
    private final List<String> propertyCopyWhitelist;
    private final List<String> similarBlocks;

    private RuleSnapshot(long revision, int stoneLimit, int ironLimit, int diamondLimit, int infinityLimit, boolean allowTileEntityPlacement, List<String> placementWhitelist, List<String> placementBlacklist, List<String> propertyCopyWhitelist, List<String> similarBlocks) {
        this.revision = revision;
        this.stoneLimit = requireLimit(stoneLimit);
        this.ironLimit = requireLimit(ironLimit);
        this.diamondLimit = requireLimit(diamondLimit);
        this.infinityLimit = requireLimit(infinityLimit);
        this.allowTileEntityPlacement = allowTileEntityPlacement;
        this.placementWhitelist = immutableRules(placementWhitelist);
        this.placementBlacklist = immutableRules(placementBlacklist);
        this.propertyCopyWhitelist = immutableRules(propertyCopyWhitelist);
        this.similarBlocks = immutableRules(similarBlocks);
    }

    public static RuleSnapshot create(long revision, int stoneLimit, int ironLimit, int diamondLimit, int infinityLimit, boolean allowTileEntityPlacement, List<String> placementWhitelist, List<String> placementBlacklist, List<String> propertyCopyWhitelist, List<String> similarBlocks) {
        return new RuleSnapshot(revision, stoneLimit, ironLimit, diamondLimit, infinityLimit, allowTileEntityPlacement, placementWhitelist, placementBlacklist, propertyCopyWhitelist, similarBlocks);
    }

    static RuleSnapshot fromConfig(long revision) {
        return create(revision, ModConfig.wandLimits.stoneWandMaxBlocks, ModConfig.wandLimits.ironWandMaxBlocks, ModConfig.wandLimits.diamondWandMaxBlocks, ModConfig.wandLimits.infinityWandMaxBlocks, ModConfig.placement.allowTileEntityPlacement, sanitize(ModConfig.placement.blockWhitelist, "placement whitelist"), sanitize(ModConfig.placement.blockBlacklist, "placement blacklist"), sanitize(ModConfig.placement.propertyCopyWhitelist, "property copy whitelist"), sanitize(ModConfig.matching.similarBlocks, "similar blocks"));
    }

    private static List<String> sanitize(String[] values, String name) {
        if (values == null) {
            return Collections.emptyList();
        }
        ArrayList<String> result = new ArrayList<>(Math.min(values.length, MAX_RULE_ENTRIES));
        for (String value : values) {
            if (result.size() == MAX_RULE_ENTRIES) {
                ConfigWarnings.warn("Too many " + name + " entries; ignoring entries after " + MAX_RULE_ENTRIES);
                break;
            }
            if (!isBounded(value)) {
                ConfigWarnings.warn("Ignoring oversized or null " + name + " entry: " + value);
                continue;
            }
            result.add(value);
        }
        return result;
    }

    private static List<String> immutableRules(List<String> values) {
        if (values == null || values.size() > MAX_RULE_ENTRIES) {
            throw new IllegalArgumentException("Rule entry count is out of bounds");
        }
        ArrayList<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            if (!isBounded(value)) {
                throw new IllegalArgumentException("Rule value is null or too long");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static boolean isBounded(String value) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= MAX_RULE_BYTES;
    }

    private static int requireLimit(int value) {
        if (value < MIN_PLACEMENT_LIMIT || value > MAX_PLACEMENT_LIMIT) {
            throw new IllegalArgumentException("Placement limit is out of bounds: " + value);
        }
        return value;
    }

    public long getRevision() {
        return revision;
    }

    public int getPlacementLimit(WandTier tier) {
        switch (tier) {
            case STONE:
                return stoneLimit;
            case IRON:
                return ironLimit;
            case DIAMOND:
                return diamondLimit;
            case INFINITY:
                return infinityLimit;
            default:
                throw new IllegalArgumentException("Unsupported wand tier: " + tier);
        }
    }

    public int getStoneLimit() {
        return stoneLimit;
    }

    public int getIronLimit() {
        return ironLimit;
    }

    public int getDiamondLimit() {
        return diamondLimit;
    }

    public int getInfinityLimit() {
        return infinityLimit;
    }

    public boolean isTileEntityPlacementAllowed() {
        return allowTileEntityPlacement;
    }

    public List<String> getPlacementWhitelist() {
        return placementWhitelist;
    }

    public List<String> getPlacementBlacklist() {
        return placementBlacklist;
    }

    public List<String> getPropertyCopyWhitelist() {
        return propertyCopyWhitelist;
    }

    public List<String> getSimilarBlocks() {
        return similarBlocks;
    }
}
