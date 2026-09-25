package com.xinyihl.constructionwandlegacy.basics.option;

import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The resolved configuration of one wand.
 * <p>
 * Cores are split into two independent selections: a behaviour core that supplies the wand action
 * and a material core that supplies and receives the items. A wand always has a behaviour core, the
 * material selection is optional ({@link #NO_MATERIAL_CORE}).
 * <p>
 * Next to the cores a wand can carry passive upgrades, which are not selectable but always active,
 * for example a fortune or an auto-smelting component.
 */
public final class WandState {
    /**
     * Selected material index meaning "no material core, use the player inventory only".
     */
    public static final int NO_MATERIAL_CORE = 0;

    private final List<IWandCore> behaviorCores;
    private final int selectedCoreIndex;
    private final List<IWandCore> materialCores;
    private final int selectedMaterialIndex;
    private final List<IWandCore> allCores;
    private final List<IWandUpgrade> upgrades;
    private final Lock lock;
    private final Direction direction;
    private final boolean replace;
    private final Match match;
    private final boolean random;

    WandState(List<IWandCore> behaviorCores, int selectedCoreIndex, List<IWandCore> materialCores, int selectedMaterialIndex, List<IWandUpgrade> upgrades, Lock lock, Direction direction, boolean replace, Match match, boolean random) {
        if (behaviorCores.isEmpty()) {
            throw new IllegalArgumentException("A wand state must contain its default core");
        }
        this.behaviorCores = Collections.unmodifiableList(new ArrayList<>(behaviorCores));
        this.selectedCoreIndex = selectedCoreIndex >= 0 && selectedCoreIndex < behaviorCores.size() ? selectedCoreIndex : 0;
        this.materialCores = Collections.unmodifiableList(new ArrayList<>(materialCores));
        this.selectedMaterialIndex = normalizeMaterialIndex(selectedMaterialIndex, materialCores.size());
        List<IWandCore> combined = new ArrayList<>(behaviorCores);
        combined.addAll(materialCores);
        this.allCores = Collections.unmodifiableList(combined);
        this.upgrades = Collections.unmodifiableList(new ArrayList<>(upgrades));
        this.lock = lock;
        this.direction = direction;
        this.replace = replace;
        this.match = match;
        this.random = random;
    }

    private static int normalizeMaterialIndex(int index, int size) {
        if (size == 0) {
            return NO_MATERIAL_CORE;
        }
        return index >= NO_MATERIAL_CORE && index <= size ? index : 1;
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Every installed core, behaviour cores first.
     */
    public List<IWandCore> getCores() {
        return allCores;
    }

    public List<IWandCore> getBehaviorCores() {
        return behaviorCores;
    }

    public List<IWandCore> getMaterialCores() {
        return materialCores;
    }

    /**
     * Everything installed on the wand that is not a core, in installation order. These upgrades are
     * always active, there is nothing to select.
     */
    public List<IWandUpgrade> getUpgrades() {
        return upgrades;
    }

    /**
     * The core that decides what the wand does.
     */
    public IWandCore getSelectedCore() {
        return behaviorCores.get(selectedCoreIndex);
    }

    public int getSelectedCoreIndex() {
        return selectedCoreIndex;
    }

    /**
     * The core that decides where items come from and go, or {@code null} when none is selected.
     */
    @Nullable
    public IWandCore getSelectedMaterialCore() {
        return selectedMaterialIndex == NO_MATERIAL_CORE ? null : materialCores.get(selectedMaterialIndex - 1);
    }

    public int getSelectedMaterialIndex() {
        return selectedMaterialIndex;
    }

    public Lock getLock() {
        return lock;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isReplace() {
        return replace;
    }

    public Match getMatch() {
        return match;
    }

    public boolean isRandom() {
        return random;
    }

    public boolean isEnabled(WandOption option) {
        switch (option) {
            case CORES:
                return behaviorCores.size() > 1;
            case MATERIAL_CORES:
                return !materialCores.isEmpty();
            default:
                return true;
        }
    }

    public String getValue(WandOption option) {
        switch (option) {
            case CORES:
                return getSelectedCore().getRegistryName().toString();
            case MATERIAL_CORES:
                IWandCore material = getSelectedMaterialCore();
                return material == null ? "none" : material.getRegistryName().toString();
            case LOCK:
                return enumValue(lock);
            case DIRECTION:
                return enumValue(direction);
            case REPLACE:
                return replace ? "yes" : "no";
            case MATCH:
                return enumValue(match);
            case RANDOM:
                return random ? "yes" : "no";
            default:
                throw new IllegalArgumentException("Unsupported wand option: " + option);
        }
    }

    public boolean testLock(Lock checkLock) {
        return lock == Lock.NOLOCK || lock == checkLock;
    }

    public boolean matchBlocks(IBlockState first, IBlockState second) {
        if (first == null || second == null) {
            return false;
        }

        int firstMeta = first.getBlock().getMetaFromState(first);
        int secondMeta = second.getBlock().getMetaFromState(second);
        switch (match) {
            case EXACT:
                return first.getBlock() == second.getBlock() && firstMeta == secondMeta;
            case SIMILAR:
                return ConfigRuntime.getBlockEquivalenceIndex().matchBlocks(first.getBlock(), second.getBlock());
            case ANY:
                return first.getBlock() != Blocks.AIR && second.getBlock() != Blocks.AIR;
            default:
                return false;
        }
    }

    public boolean hasUpgrade(IWandUpgrade upgrade) {
        if (upgrade instanceof IWandCore) {
            return allCores.contains(upgrade);
        }
        return upgrades.contains(upgrade);
    }

    public enum Lock {
        HORIZONTAL, VERTICAL, NORTHSOUTH, EASTWEST, NOLOCK
    }

    public enum Direction {
        TARGET, PLAYER
    }

    public enum Match {
        EXACT, SIMILAR, ANY
    }
}
