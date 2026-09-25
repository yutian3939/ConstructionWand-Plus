package com.xinyihl.constructionwandlegacy.basics.option;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.items.core.CoreDefault;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class WandDataCodec {
    public static final String TAG_ROOT = "wand_options";

    private static final String TAG_CORE_SELECTOR = "cores_sel";
    private static final String TAG_MATERIAL_SELECTOR = "cores_mat_sel";
    private static final String NO_MATERIAL_VALUE = "none";
    private static final IWandCore DEFAULT_CORE = new CoreDefault();
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");

    private WandDataCodec() {
    }

    public static WandState read(ItemStack wandStack) {
        NBTTagCompound data = findData(wandStack);
        CoreLists cores = readCores(data);
        int selectedCore = data == null ? 0 : data.getByte(TAG_CORE_SELECTOR) & 0xFF;
        int selectedMaterial;
        if (data == null || !data.hasKey(TAG_MATERIAL_SELECTOR)) {
            // Wands from before the behaviour/material split kept their material core as the single
            // selected core; selecting the first material core keeps such wands working as before.
            selectedMaterial = cores.material.isEmpty() ? WandState.NO_MATERIAL_CORE : 1;
        } else {
            selectedMaterial = data.getByte(TAG_MATERIAL_SELECTOR) & 0xFF;
        }

        WandState.Lock lock = readEnum(data, WandOption.LOCK, WandState.Lock.class, WandState.Lock.NOLOCK);
        WandState.Direction direction = readEnum(data, WandOption.DIRECTION, WandState.Direction.class, WandState.Direction.TARGET);
        boolean replace = readBoolean(data, WandOption.REPLACE, true);
        WandState.Match match = readEnum(data, WandOption.MATCH, WandState.Match.class, WandState.Match.SIMILAR);
        boolean random = readBoolean(data, WandOption.RANDOM, false);

        return new WandState(cores.behavior, selectedCore, cores.material, selectedMaterial, cores.upgrades, lock, direction, replace, match, random);
    }

    public static boolean update(ItemStack wandStack, WandOption option, String value) {
        if (option == null || value == null || !isWritableStack(wandStack)) {
            return false;
        }

        WandState state = read(wandStack);
        switch (option) {
            case CORES:
                int selector = findCore(state.getBehaviorCores(), value);
                if (selector < 0 || selector > Byte.MAX_VALUE) {
                    return false;
                }
                return update(wandStack, data -> data.setByte(TAG_CORE_SELECTOR, (byte) selector));
            case MATERIAL_CORES:
                int materialSelector;
                if (NO_MATERIAL_VALUE.equals(value)) {
                    materialSelector = WandState.NO_MATERIAL_CORE;
                } else {
                    int found = findCore(state.getMaterialCores(), value);
                    if (found < 0) {
                        return false;
                    }
                    materialSelector = found + 1;
                }
                return update(wandStack, data -> data.setByte(TAG_MATERIAL_SELECTOR, (byte) materialSelector));
            case LOCK:
                WandState.Lock lock = parseEnum(WandState.Lock.class, value);
                return lock != null && writeString(wandStack, option, enumValue(lock));
            case DIRECTION:
                WandState.Direction direction = parseEnum(WandState.Direction.class, value);
                return direction != null && writeString(wandStack, option, enumValue(direction));
            case REPLACE:
            case RANDOM:
                Boolean booleanValue = parseBoolean(value);
                return booleanValue != null && update(wandStack, data -> data.setBoolean(option.getId(), booleanValue));
            case MATCH:
                WandState.Match match = parseEnum(WandState.Match.class, value);
                return match != null && writeString(wandStack, option, enumValue(match));
            default:
                return false;
        }
    }

    public static boolean cycle(ItemStack wandStack, WandOption option, boolean forward) {
        if (option == null || !isWritableStack(wandStack)) {
            return false;
        }

        WandState state = read(wandStack);
        int next = cycleNetworkValue(state, option, forward);
        return next >= 0 && updateNetworkValue(wandStack, option, next);
    }

    public static int getNetworkValue(WandState state, WandOption option) {
        if (state == null || option == null) {
            return -1;
        }
        switch (option) {
            case CORES:
                return state.getSelectedCoreIndex();
            case MATERIAL_CORES:
                return state.getSelectedMaterialIndex();
            case LOCK:
                return state.getLock().ordinal();
            case DIRECTION:
                return state.getDirection().ordinal();
            case REPLACE:
                return state.isReplace() ? 1 : 0;
            case MATCH:
                return state.getMatch().ordinal();
            case RANDOM:
                return state.isRandom() ? 1 : 0;
            default:
                return -1;
        }
    }

    public static int cycleNetworkValue(WandState state, WandOption option, boolean forward) {
        if (state == null || option == null || !state.isEnabled(option)) {
            return -1;
        }
        int current = getNetworkValue(state, option);
        int size;
        switch (option) {
            case CORES:
                size = state.getBehaviorCores().size();
                break;
            case MATERIAL_CORES:
                size = state.getMaterialCores().size() + 1;
                break;
            case LOCK:
                size = WandState.Lock.values().length;
                break;
            case DIRECTION:
                size = WandState.Direction.values().length;
                break;
            case MATCH:
                size = WandState.Match.values().length;
                break;
            case REPLACE:
            case RANDOM:
                size = 2;
                break;
            default:
                return -1;
        }
        return wrap(current + (forward ? 1 : -1), size);
    }

    public static boolean isValidNetworkValue(WandState state, WandOption option, int value) {
        if (state == null || option == null || !state.isEnabled(option) || value < 0) {
            return false;
        }
        switch (option) {
            case CORES:
                return value < state.getBehaviorCores().size() && value <= Byte.MAX_VALUE;
            case MATERIAL_CORES:
                return value <= state.getMaterialCores().size() && value <= Byte.MAX_VALUE;
            case LOCK:
                return value < WandState.Lock.values().length;
            case DIRECTION:
                return value < WandState.Direction.values().length;
            case REPLACE:
            case RANDOM:
                return value <= 1;
            case MATCH:
                return value < WandState.Match.values().length;
            default:
                return false;
        }
    }

    public static boolean updateNetworkValue(ItemStack wandStack, WandOption option, int value) {
        WandState state = read(wandStack);
        if (!isWritableStack(wandStack) || !isValidNetworkValue(state, option, value)) {
            return false;
        }
        switch (option) {
            case CORES:
                return update(wandStack, data -> data.setByte(TAG_CORE_SELECTOR, (byte) value));
            case MATERIAL_CORES:
                return update(wandStack, data -> data.setByte(TAG_MATERIAL_SELECTOR, (byte) value));
            case LOCK:
                return writeString(wandStack, option, enumValue(WandState.Lock.values()[value]));
            case DIRECTION:
                return writeString(wandStack, option, enumValue(WandState.Direction.values()[value]));
            case REPLACE:
            case RANDOM:
                return update(wandStack, data -> data.setBoolean(option.getId(), value == 1));
            case MATCH:
                return writeString(wandStack, option, enumValue(WandState.Match.values()[value]));
            default:
                return false;
        }
    }

    public static boolean addUpgrade(ItemStack wandStack, IWandUpgrade upgrade) {
        if (!isWritableStack(wandStack) || !(upgrade instanceof Item) || !(upgrade instanceof IWandUpgrade)) {
            return false;
        }

        Item item = (Item) upgrade;
        ResourceLocation registryName = item.getRegistryName();
        if (registryName == null || ForgeRegistries.ITEMS.getValue(registryName) != item) {
            ConstructionWandLegacy.LOGGER.warn("Unregistered wand upgrade: {}", registryName);
            return false;
        }

        WandState state = read(wandStack);
        if (state.hasUpgrade(upgrade)) {
            return false;
        }
        boolean materialCore = upgrade instanceof IWandCore && ((IWandCore) upgrade).isMaterialCore();

        return update(wandStack, data -> {
            NBTTagList list = new NBTTagList();
            for (IWandCore installed : state.getCores()) {
                if (installed instanceof Item && installed.getRegistryName() != null) {
                    list.appendTag(new NBTTagString(installed.getRegistryName().toString()));
                }
            }
            for (IWandUpgrade installed : state.getUpgrades()) {
                if (installed instanceof Item && installed.getRegistryName() != null) {
                    list.appendTag(new NBTTagString(installed.getRegistryName().toString()));
                }
            }
            list.appendTag(new NBTTagString(registryName.toString()));
            data.setTag(WandOption.CORES.getId(), list);
            data.setByte(TAG_CORE_SELECTOR, (byte) state.getSelectedCoreIndex());

            int materialIndex = state.getSelectedMaterialIndex();
            if (materialIndex == WandState.NO_MATERIAL_CORE && materialCore) {
                // Select a freshly installed material core instead of leaving the wand without one.
                materialIndex = state.getMaterialCores().size() + 1;
            }
            data.setByte(TAG_MATERIAL_SELECTOR, (byte) materialIndex);
        });
    }

    public static NBTTagCompound readData(ItemStack wandStack) {
        NBTTagCompound data = findData(wandStack);
        return data == null ? new NBTTagCompound() : data.copy();
    }

    /**
     * Every installed core that exists as an item, i.e. the ones that can be taken off the wand
     * again. The always present default core is not an item and therefore never listed.
     */
    public static List<ItemStack> coreItems(ItemStack wandStack) {
        if (!isWritableStack(wandStack)) {
            return Collections.emptyList();
        }
        List<ItemStack> items = new ArrayList<>();
        for (IWandCore core : read(wandStack).getCores()) {
            if (core instanceof Item && core.getRegistryName() != null) {
                items.add(new ItemStack((Item) core));
            }
        }
        return items;
    }

    /**
     * Takes one installed core off the wand.
     * <p>
     * The selections are kept where they still make sense: a wand that had the removed core selected
     * falls back to the default behaviour core and to no material core.
     *
     * @return whether the core was installed and got removed
     */
    public static boolean removeUpgrade(ItemStack wandStack, IWandUpgrade upgrade) {
        if (!isWritableStack(wandStack) || !(upgrade instanceof Item) || !(upgrade instanceof IWandUpgrade)) {
            return false;
        }
        WandState state = read(wandStack);
        if (upgrade.getRegistryName() == null || !state.hasUpgrade(upgrade)) {
            return false;
        }

        List<IWandCore> behaviorCores = state.getBehaviorCores();
        List<IWandCore> materialCores = state.getMaterialCores();
        int selectedCore = state.getSelectedCoreIndex();
        int selectedMaterial = state.getSelectedMaterialIndex();

        int coreSelector = selectionAfterRemoval(behaviorCores, selectedCore, upgrade, 0);
        int materialIndex = selectedMaterial == WandState.NO_MATERIAL_CORE ? -1 : selectionAfterRemoval(materialCores, selectedMaterial - 1, upgrade, -1);
        int materialSelector = materialIndex < 0 ? WandState.NO_MATERIAL_CORE : materialIndex + 1;

        return update(wandStack, data -> {
            NBTTagList list = new NBTTagList();
            for (IWandCore installed : state.getCores()) {
                if (!installed.equals(upgrade) && installed instanceof Item && installed.getRegistryName() != null) {
                    list.appendTag(new NBTTagString(installed.getRegistryName().toString()));
                }
            }
            for (IWandUpgrade installed : state.getUpgrades()) {
                if (!installed.equals(upgrade) && installed instanceof Item && installed.getRegistryName() != null) {
                    list.appendTag(new NBTTagString(installed.getRegistryName().toString()));
                }
            }
            data.setTag(WandOption.CORES.getId(), list);
            data.setByte(TAG_CORE_SELECTOR, (byte) coreSelector);
            data.setByte(TAG_MATERIAL_SELECTOR, (byte) materialSelector);
        });
    }

    /**
     * Where a selection points once one core is taken out of the list, or {@code fallback} when the
     * removed core was the selected one.
     */
    private static int selectionAfterRemoval(List<IWandCore> cores, int selectedIndex, IWandUpgrade upgrade, int fallback) {
        if (selectedIndex < 0 || selectedIndex >= cores.size() || cores.get(selectedIndex).equals(upgrade)) {
            return fallback;
        }
        int removedBefore = 0;
        for (int i = 0; i < selectedIndex; i++) {
            if (cores.get(i).equals(upgrade)) {
                removedBefore++;
            }
        }
        return selectedIndex - removedBefore;
    }

    public static boolean update(ItemStack wandStack, Consumer<NBTTagCompound> updater) {
        if (!isWritableStack(wandStack) || updater == null) {
            return false;
        }
        updater.accept(getOrCreateData(wandStack));
        return true;
    }

    private static CoreLists readCores(@Nullable NBTTagCompound data) {
        List<IWandCore> behavior = new ArrayList<>();
        List<IWandCore> material = new ArrayList<>();
        List<IWandUpgrade> upgrades = new ArrayList<>();
        behavior.add(DEFAULT_CORE);
        if (data == null) {
            return new CoreLists(behavior, material, upgrades);
        }

        NBTTagList list = data.getTagList(WandOption.CORES.getId(), Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            String rawId = list.getStringTagAt(i);
            ResourceLocation id;
            try {
                id = new ResourceLocation(rawId);
            } catch (RuntimeException exception) {
                ConstructionWandLegacy.LOGGER.warn("Invalid wand upgrade id: {}", rawId);
                continue;
            }
            if (!VALID_NAMESPACE.matcher(id.getNamespace()).matches() || !VALID_PATH.matcher(id.getPath()).matches()) {
                ConstructionWandLegacy.LOGGER.warn("Invalid wand upgrade id: {}", rawId);
                continue;
            }

            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                ConstructionWandLegacy.LOGGER.warn("Unknown wand upgrade: {}", rawId);
                continue;
            }
            if (!(item instanceof IWandUpgrade)) {
                ConstructionWandLegacy.LOGGER.warn("Item is not a wand upgrade: {}", rawId);
                continue;
            }
            if (item.getRegistryName() == null || !id.equals(item.getRegistryName())) {
                ConstructionWandLegacy.LOGGER.warn("Unregistered wand upgrade: {}", rawId);
                continue;
            }

            IWandUpgrade upgrade = (IWandUpgrade) item;
            if (!(upgrade instanceof IWandCore)) {
                // Passive upgrades are not selectable, they are simply installed.
                if (!upgrades.contains(upgrade)) {
                    upgrades.add(upgrade);
                }
                continue;
            }

            IWandCore core = (IWandCore) upgrade;
            List<IWandCore> target = core.isMaterialCore() ? material : behavior;
            if (!target.contains(core)) {
                target.add(core);
            }
        }
        return new CoreLists(behavior, material, upgrades);
    }

    private static boolean readBoolean(@Nullable NBTTagCompound data, WandOption option, boolean defaultValue) {
        return data != null && data.hasKey(option.getId()) ? data.getBoolean(option.getId()) : defaultValue;
    }

    private static <E extends Enum<E>> E readEnum(@Nullable NBTTagCompound data, WandOption option, Class<E> enumClass, E defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        E value = parseEnum(enumClass, data.getString(option.getId()));
        return value == null ? defaultValue : value;
    }

    @Nullable
    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Nullable
    private static Boolean parseBoolean(String value) {
        if ("yes".equals(value)) {
            return Boolean.TRUE;
        }
        if ("no".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean writeString(ItemStack wandStack, WandOption option, String value) {
        return update(wandStack, data -> data.setString(option.getId(), value));
    }

    private static int findCore(List<IWandCore> cores, String registryName) {
        for (int i = 0; i < cores.size(); i++) {
            ResourceLocation id = cores.get(i).getRegistryName();
            if (id != null && id.toString().equals(registryName)) {
                return i;
            }
        }
        return -1;
    }

    private static int wrap(int value, int size) {
        int wrapped = value % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isWritableStack(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    @Nullable
    private static NBTTagCompound findData(ItemStack wandStack) {
        if (wandStack == null || wandStack.isEmpty()) {
            return null;
        }
        NBTTagCompound root = wandStack.getTagCompound();
        if (root == null || !root.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompoundTag(TAG_ROOT);
    }

    private static NBTTagCompound getOrCreateData(ItemStack wandStack) {
        NBTTagCompound root = wandStack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            wandStack.setTagCompound(root);
        }
        if (!root.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND)) {
            root.setTag(TAG_ROOT, new NBTTagCompound());
        }
        return root.getCompoundTag(TAG_ROOT);
    }

    private static final class CoreLists {
        private final List<IWandCore> behavior;
        private final List<IWandCore> material;
        private final List<IWandUpgrade> upgrades;

        private CoreLists(List<IWandCore> behavior, List<IWandCore> material, List<IWandUpgrade> upgrades) {
            this.behavior = behavior;
            this.material = material;
            this.upgrades = upgrades;
        }
    }
}
