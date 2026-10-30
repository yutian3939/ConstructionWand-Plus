package thetadev.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.ConstructionWand;

import javax.annotation.Nullable;

/**
 * How many blocks one action of a single wand may affect.
 * <p>
 * The configured maximum of the wand's tier stays the cap: the settings screen can lower the limit of
 * a wand, never raise it beyond what the server allows. A wand without an explicit limit simply
 * follows the configuration.
 */
public final class WandLimit
{
    private static final String KEY = "block_limit";

    private WandLimit() {
    }

    /**
     * @return the limit stored on the wand, or {@code 0} when it follows the configuration
     */
    public static int getStored(ItemStack wand) {
        CompoundTag data = data(wand);
        if(data == null || !data.contains(KEY, Tag.TAG_INT)) return 0;
        return Math.max(0, data.getInt(KEY));
    }

    /**
     * @return the limit the wand really uses, never above {@code configuredMax}
     */
    public static int resolve(ItemStack wand, int configuredMax) {
        int max = Math.max(1, configuredMax);
        int stored = getStored(wand);
        return stored <= 0 ? max : Math.min(stored, max);
    }

    public static boolean set(ItemStack wand, int limit) {
        if(wand == null || wand.isEmpty() || limit <= 0) return false;
        CompoundTag root = wand.getOrCreateTag();
        CompoundTag data = root.contains(ExtraWandOption.TAG_ROOT, Tag.TAG_COMPOUND) ? root.getCompound(ExtraWandOption.TAG_ROOT) : new CompoundTag();
        data.putInt(KEY, limit);
        root.put(ExtraWandOption.TAG_ROOT, data);
        return true;
    }

    public static String getKeyTranslation() {
        return ConstructionWand.MODID + ".option.block_limit";
    }

    public static String getValueTranslation() {
        return ConstructionWand.MODID + ".option.block_limit.value";
    }

    public static String getDescriptionTranslation() {
        return ConstructionWand.MODID + ".option.block_limit.desc";
    }

    @Nullable
    private static CompoundTag data(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = wand.getTag();
        if(root == null || !root.contains(ExtraWandOption.TAG_ROOT, Tag.TAG_COMPOUND)) return null;
        return root.getCompound(ExtraWandOption.TAG_ROOT);
    }
}
