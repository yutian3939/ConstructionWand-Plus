package com.xinyihl.constructionwandlegacy.wand;

/**
 * Immutable intrinsic properties of a construction wand tier.
 */
public final class WandSpec {
    private final int basePlacementLimit;
    private final int durability;
    private final int angelRange;

    public WandSpec(int basePlacementLimit, int durability, int angelRange) {
        if (basePlacementLimit < 1) {
            throw new IllegalArgumentException("basePlacementLimit must be positive");
        }
        if (durability < 1) {
            throw new IllegalArgumentException("durability must be positive");
        }
        if (angelRange < 0) {
            throw new IllegalArgumentException("angelRange must not be negative");
        }

        this.basePlacementLimit = basePlacementLimit;
        this.durability = durability;
        this.angelRange = angelRange;
    }

    public int getBasePlacementLimit() {
        return basePlacementLimit;
    }

    public int getDurability() {
        return durability;
    }

    public int getAngelRange() {
        return angelRange;
    }
}
