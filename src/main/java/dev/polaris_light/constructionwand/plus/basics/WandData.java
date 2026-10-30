package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Access to the extra data the plus features keep on a wand.
 * <p>
 * 1.21 stores item NBT in the {@code minecraft:custom_data} component instead of a free tag, so every
 * read and write goes through here: the root tag of the stack is read back, the plus subtree is
 * changed and the whole root is written again. Reading the root back on every write matters because
 * the stock {@code wand_options} tag lives in the same component and the two must not overwrite each
 * other.
 */
public final class WandData
{
    /**
     * Root tag the stock wand settings live in; the plus options have their own, see
     * {@link ExtraWandOption#TAG_ROOT}.
     */
    private static final String WandOptionsTag = "wand_options";

    private WandData() {
    }

    /**
     * @return the plus subtree of the wand, or {@code null} when the wand carries none
     */
    @Nullable
    public static CompoundTag read(ItemStack wand) {
        return readRoot(wand, ExtraWandOption.TAG_ROOT);
    }

    /**
     * @return the named subtree of the wand, or {@code null} when it is absent
     */
    @Nullable
    public static CompoundTag readRoot(ItemStack wand, String rootKey) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = rootTag(wand);
        if(!root.contains(rootKey, Tag.TAG_COMPOUND)) return null;
        return root.getCompound(rootKey);
    }

    /**
     * @return the raw named tag of the wand, which may be of any type, or {@code null} when it is
     * absent. Used by the features that store a list rather than a compound.
     */
    @Nullable
    public static Tag readTag(ItemStack wand, String key) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = rootTag(wand);
        return root.contains(key) ? root.get(key) : null;
    }

    /**
     * @return the plus subtree of the wand, creating it in memory when it does not exist yet
     */
    public static CompoundTag readOrEmpty(ItemStack wand) {
        CompoundTag data = read(wand);
        return data == null ? new CompoundTag() : data;
    }

    /**
     * Writes the plus subtree back into the wand, leaving every other root tag of the stack alone.
     */
    public static void write(ItemStack wand, CompoundTag data) {
        writeRoot(wand, ExtraWandOption.TAG_ROOT, data);
    }

    /**
     * Writes the named subtree back into the wand, leaving every other root tag of the stack alone.
     */
    public static void writeRoot(ItemStack wand, String rootKey, Tag data) {
        if(!isWand(wand) || data == null) return;
        CompoundTag root = rootTag(wand);
        root.put(rootKey, data);
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /**
     * Removes the named subtree from the wand, leaving every other root tag of the stack alone.
     */
    public static void removeRoot(ItemStack wand, String rootKey) {
        if(!isWand(wand)) return;
        CompoundTag root = rootTag(wand);
        if(!root.contains(rootKey)) return;
        root.remove(rootKey);
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /**
     * Changes the plus subtree of the wand in place.
     *
     * @return whether the wand was written
     */
    public static boolean update(ItemStack wand, Consumer<CompoundTag> updater) {
        if(!isWand(wand) || updater == null) return false;
        CompoundTag data = readOrEmpty(wand);
        updater.accept(data);
        write(wand, data);
        return true;
    }

    /**
     * @return whether the wand carries that item in the named option list, read straight off the live
     * tag. This is the hot path the container gate runs on every inventory slot, so it neither copies
     * the tag nor builds a {@code WandOptions} (whose constructor writes the tag back).
     */
    public static boolean hasInstalled(ItemStack wand, String optionKey, @Nullable Item item) {
        if(item == null || wand == null || wand.isEmpty()) return false;

        CompoundTag root = peekRoot(wand);
        if(root == null || !root.contains(WandOptionsTag, Tag.TAG_COMPOUND)) return false;

        CompoundTag options = root.getCompound(WandOptionsTag);
        if(!options.contains(optionKey, Tag.TAG_LIST)) return false;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if(id == null) return false;

        String wanted = id.toString();
        ListTag installed = options.getList(optionKey, Tag.TAG_STRING);
        for(int index = 0; index < installed.size(); index++) {
            if(wanted.equals(installed.getString(index))) return true;
        }
        return false;
    }

    /**
     * @return the live root tag of the stack, without copying it. Never mutate the result.
     */
    @Nullable
    public static CompoundTag peekRoot(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return null;
        CustomData data = wand.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /**
     * @return whether the stack may carry wand data at all. Every write goes through here, so a stack of
     * stone that is only being asked about its options can never be given a tag by accident.
     */
    public static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof dev.polaris_light.constructionwand.items.wand.ItemWand;
    }

    private static CompoundTag rootTag(ItemStack wand) {
        return wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
