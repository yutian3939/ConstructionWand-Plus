package com.xinyihl.constructionwandlegacy.wand;

import com.xinyihl.constructionwandlegacy.config.ModConfig;

public enum WandTier {
    STONE(new WandSpec(64, 131, 16)), IRON(new WandSpec(128, 250, 32)), DIAMOND(new WandSpec(512, 1561, 64)), INFINITY(new WandSpec(1024, Integer.MAX_VALUE, 128));

    private final WandSpec spec;

    WandTier(WandSpec spec) {
        this.spec = spec;
    }

    public WandSpec getSpec() {
        return spec;
    }

    public int getConfiguredPlacementLimit() {
        switch (this) {
            case STONE:
                return ModConfig.wandLimits.stoneWandMaxBlocks;
            case IRON:
                return ModConfig.wandLimits.ironWandMaxBlocks;
            case DIAMOND:
                return ModConfig.wandLimits.diamondWandMaxBlocks;
            case INFINITY:
                return ModConfig.wandLimits.infinityWandMaxBlocks;
            default:
                return spec.getBasePlacementLimit();
        }
    }
}
