package dev.polaris_light.constructionwand.basics.option;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.api.IWandUpgrade;
import dev.polaris_light.constructionwand.basics.ReplacementRegistry;
import dev.polaris_light.constructionwand.items.core.CoreDefault;
import dev.polaris_light.constructionwand.plus.items.CoreNoMaterial;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;

import javax.annotation.Nullable;

public class WandOptions
{
    public final CompoundTag tag;

    private static final String TAG_ROOT = "wand_options";

    public enum LOCK
    {
        HORIZONTAL,
        VERTICAL,
        NORTHSOUTH,
        EASTWEST,
        NOLOCK
    }

    public enum DIRECTION
    {
        TARGET,
        PLAYER
    }

    public enum MATCH
    {
        EXACT,
        SIMILAR,
        ANY
    }

    public final WandUpgradesSelectable<IWandCore> cores;
    public final WandUpgradesSelectable<IWandCore> materialCores;
    public final WandUpgrades<IWandUpgrade> upgrades;

    public final OptionEnum<LOCK> lock;
    public final OptionEnum<DIRECTION> direction;
    public final OptionBoolean replace;
    public final OptionEnum<MATCH> match;
    public final OptionBoolean random;

    public final IOption<?>[] allOptions;

    /**
     * @return whether the stack is a wand at all. Reading the options of anything else is allowed, writing
     * them is not.
     */
    public static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof dev.polaris_light.constructionwand.items.wand.ItemWand;
    }

    public WandOptions(ItemStack wandStack) {
        CompoundTag root = wandStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(TAG_ROOT)) {
            root.put(TAG_ROOT, new CompoundTag());
        }
        tag = root.getCompound(TAG_ROOT);
        // The whole root is read back on every write: the plus settings live in a sibling root tag of
        // the same component, so writing the tag this instance read at construction would drop them.
        //
        // Only a wand is written to. The settings of a stack that is not a wand are read from an empty tag
        // and stay in memory: the questions "which core is this?" are asked about whatever the player
        // happens to hold, and answering them must not leave a tag on a stack of stone, which would stop it
        // from stacking with the plain ones.
        Runnable persist = () -> {
            if(!isWand(wandStack)) return;
            CompoundTag live = wandStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            live.put(TAG_ROOT, tag);
            wandStack.set(DataComponents.CUSTOM_DATA, CustomData.of(live));
        };
        persist.run();

        cores = new WandUpgradesSelectable<>(tag, "cores", new CoreDefault(), persist);
        materialCores = new WandUpgradesSelectable<>(tag, "material_cores", new CoreNoMaterial(), persist);
        upgrades = new WandUpgrades<>(tag, "upgrades", null, persist);

        lock = new OptionEnum<>(tag, "lock", LOCK.class, LOCK.NOLOCK, persist);
        direction = new OptionEnum<>(tag, "direction", DIRECTION.class, DIRECTION.TARGET, persist);
        replace = new OptionBoolean(tag, "replace", true, persist);
        match = new OptionEnum<>(tag, "match", MATCH.class, MATCH.SIMILAR, persist);
        random = new OptionBoolean(tag, "random", false, persist);

        allOptions = new IOption[]{cores, materialCores, lock, direction, replace, match, random};
    }

    @Nullable
    public IOption<?> get(String key) {
        for(IOption<?> option : allOptions) {
            if(option.getKey().equals(key)) return option;
        }
        return null;
    }

    public boolean testLock(LOCK l) {
        if(lock.get() == LOCK.NOLOCK) return true;
        return lock.get() == l;
    }

    public boolean matchBlocks(Block b1, Block b2) {
        switch(match.get()) {
            case EXACT:
                return b1 == b2;
            case SIMILAR:
                return ReplacementRegistry.matchBlocks(b1, b2);
            case ANY:
                return b1 != Blocks.AIR && b2 != Blocks.AIR;
        }
        return false;
    }

    public boolean hasUpgrade(IWandUpgrade upgrade) {
        if(upgrade instanceof IMaterialCore) return materialCores.hasUpgrade((IWandCore) upgrade);
        if(upgrade instanceof IWandCore) return cores.hasUpgrade((IWandCore) upgrade);
        return upgrades.hasUpgrade(upgrade);
    }

    public boolean addUpgrade(IWandUpgrade upgrade) {
        if(upgrade instanceof IMaterialCore) return materialCores.addUpgrade((IWandCore) upgrade);
        if(upgrade instanceof IWandCore) return cores.addUpgrade((IWandCore) upgrade);
        return upgrades.addUpgrade(upgrade);
    }

    /**
     * @return the selected material core, or {@code null} when the wand draws from its inventory only
     */
    @Nullable
    public IMaterialCore getMaterialCore() {
        return materialCores.get() instanceof IMaterialCore core ? core : null;
    }
}
