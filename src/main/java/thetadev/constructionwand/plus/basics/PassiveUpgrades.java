package thetadev.constructionwand.plus.basics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import thetadev.constructionwand.api.IWandUpgrade;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.plus.items.PlusItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the passive upgrade components off a wand.
 * <p>
 * The components stack, so the effects they describe are combined: the fortune component replaces the
 * fortune the wand would otherwise have, and the auto-smelting component runs after the drops are
 * settled, which means both work together.
 * <p>
 * Every installed component can additionally be disabled in place from the wand screen: it stays on
 * the wand but its effect is off until it is enabled again.
 */
public final class PassiveUpgrades
{
    private static final String TAG_DISABLED = "disabled_upgrades";

    private PassiveUpgrades() {
    }

    /**
     * @return whether the component is installed on the wand, regardless of its enabled state
     */
    public static boolean has(ItemStack wand, Item upgrade) {
        if(wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade)) return false;
        return new WandOptions(wand).upgrades.hasUpgrade((IWandUpgrade) upgrade);
    }

    /**
     * @return whether the component is installed and currently enabled
     */
    public static boolean isEnabled(ItemStack wand, Item upgrade) {
        return has(wand, upgrade) && !isDisabled(wand, upgrade);
    }

    /**
     * @return whether an installed component is currently disabled
     */
    public static boolean isDisabled(ItemStack wand, Item upgrade) {
        if(wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade)) return false;
        ResourceLocation id = ((IWandUpgrade) upgrade).getRegistryName();
        if(id == null) return false;

        CompoundTag data = data(wand);
        if(data == null) return false;

        ListTag list = data.getList(TAG_DISABLED, Tag.TAG_STRING);
        String key = id.toString();
        for(int index = 0; index < list.size(); index++) {
            if(key.equals(list.getString(index))) return true;
        }
        return false;
    }

    /**
     * Enables or disables an installed component in place. Disabling keeps the component on the wand
     * but switches its effect off.
     *
     * @return whether the wand was written
     */
    public static boolean setDisabled(ItemStack wand, Item upgrade, boolean disabled) {
        if(wand == null || wand.isEmpty() || !(upgrade instanceof IWandUpgrade) || !has(wand, upgrade)) return false;

        ResourceLocation id = ((IWandUpgrade) upgrade).getRegistryName();
        if(id == null) return false;

        String key = id.toString();
        return updateData(wand, data -> {
            ListTag list = data.contains(TAG_DISABLED, Tag.TAG_LIST) ? data.getList(TAG_DISABLED, Tag.TAG_STRING) : new ListTag();
            ListTag result = new ListTag();
            for(int index = 0; index < list.size(); index++) {
                String entry = list.getString(index);
                if(!key.equals(entry)) result.add(StringTag.valueOf(entry));
            }
            if(disabled) result.add(StringTag.valueOf(key));
            data.put(TAG_DISABLED, result);
        });
    }

    public static boolean hasFortune(ItemStack wand) {
        return isEnabled(wand, PlusItems.UPGRADE_FORTUNE.get());
    }

    public static boolean hasAutoSmelt(ItemStack wand) {
        return isEnabled(wand, PlusItems.UPGRADE_AUTO_SMELT.get());
    }

    public static boolean hasFlight(ItemStack wand) {
        return isEnabled(wand, PlusItems.UPGRADE_FLIGHT.get());
    }

    public static boolean hasDefense(ItemStack wand) {
        return isEnabled(wand, PlusItems.UPGRADE_DEFENSE.get());
    }

    /**
     * @return the installed passive upgrades in a stable order, for the wand screen and tooltips
     */
    public static List<Item> installed(ItemStack wand) {
        List<Item> installed = new ArrayList<>();
        for(Item upgrade : new Item[]{PlusItems.UPGRADE_FORTUNE.get(), PlusItems.UPGRADE_AUTO_SMELT.get(),
                PlusItems.UPGRADE_FLIGHT.get(), PlusItems.UPGRADE_DEFENSE.get()}) {
            if(upgrade != null && has(wand, upgrade)) installed.add(upgrade);
        }
        return installed;
    }

    /**
     * @return the fortune level the fortune component grants, from the configuration
     */
    public static int fortuneLevel() {
        return Math.max(1, ConfigServer.FORTUNE_LEVEL.get());
    }

    /**
     * @return the still installed passive upgrade whose registry name matches, or {@code null}
     */
    public static Item byRegistryName(ItemStack wand, String registryName) {
        for(Item upgrade : installed(wand)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(upgrade);
            if(id != null && id.toString().equals(registryName)) return upgrade;
        }
        return null;
    }

    private static CompoundTag data(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = wand.getTag();
        if(root == null || !root.contains(ExtraWandOption.TAG_ROOT, Tag.TAG_COMPOUND)) return null;
        return root.getCompound(ExtraWandOption.TAG_ROOT);
    }

    private static boolean updateData(ItemStack wand, Consumer<CompoundTag> updater) {
        if(wand == null || wand.isEmpty() || updater == null) return false;
        CompoundTag root = wand.getOrCreateTag();
        CompoundTag data = root.contains(ExtraWandOption.TAG_ROOT, Tag.TAG_COMPOUND) ? root.getCompound(ExtraWandOption.TAG_ROOT) : new CompoundTag();
        updater.accept(data);
        root.put(ExtraWandOption.TAG_ROOT, data);
        return true;
    }
}
