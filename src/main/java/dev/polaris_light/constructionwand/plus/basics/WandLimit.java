package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.wand.ActionSlay;

import javax.annotation.Nullable;

/**
 * How far one action of a single wand reaches.
 * <p>
 * The stored number is kept per kind of reach: the block limit of a core that works on an area of blocks
 * and the radius of the slay core are two different settings, so lowering one never moves the other. They
 * used to share a single tag, which is why setting the slay radius also changed the block limit of the
 * construction core.
 * <p>
 * The configured maximum of the wand's tier stays the cap: the settings screen can lower a value, never
 * raise it beyond what the server allows. A wand without an explicit value follows the configuration.
 */
public final class WandLimit
{
    private static final String KEY_BLOCK_LIMIT = "block_limit";
    private static final String KEY_SLAY_RADIUS = "slay_radius";

    private WandLimit() {
    }

    /**
     * @return the tag the selected core stores its reach in
     */
    private static String keyFor(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return KEY_BLOCK_LIMIT;
        return new WandOptions(wand).cores.get().getWandAction() instanceof ActionSlay
                ? KEY_SLAY_RADIUS : KEY_BLOCK_LIMIT;
    }

    /**
     * @return the value stored on the wand, or {@code 0} when it follows the configuration
     */
    public static int getStored(ItemStack wand) {
        String key = keyFor(wand);
        CompoundTag data = data(wand);
        if(data == null || !data.contains(key, Tag.TAG_INT)) return 0;
        return Math.max(0, data.getInt(key));
    }

    /**
     * @return the value the wand really uses, never above {@code configuredMax}
     */
    public static int resolve(ItemStack wand, int configuredMax) {
        int max = Math.max(1, configuredMax);
        int stored = getStored(wand);
        return stored <= 0 ? max : Math.min(stored, max);
    }

    /**
     * @return the highest value the wand's tier allows, which is what the screen offers and what the server
     * accepts. It is deliberately not the value the wand currently uses: lowering a setting and then raising
     * it again has to work, and reading the current value here would make every lowered value a new ceiling.
     */
    public static int configuredMax(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return 1;
        IWandCore core = new WandOptions(wand).cores.get();
        if(core.getWandAction() instanceof ActionSlay) {
            return Math.max(1, ConfigServer.getSlayRadius(wand.getItem()));
        }
        return Math.max(1, core.getWandAction().getLimit(wand));
    }

    public static boolean set(ItemStack wand, int limit) {
        if(wand == null || wand.isEmpty() || limit <= 0) return false;
        String key = keyFor(wand);
        return WandData.update(wand, data -> data.putInt(key, limit));
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
        return WandData.read(wand);
    }
}
