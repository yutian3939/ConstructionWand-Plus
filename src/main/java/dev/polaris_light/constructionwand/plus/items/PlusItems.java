package dev.polaris_light.constructionwand.plus.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.ModItems;
/**
 * Registration of everything the plus content adds.
 * <p>
 * All of it lives in one dedicated creative tab, in the order wands, behaviour cores, material cores,
 * upgrades, so the wand, its cores and its upgrades are not scattered across the vanilla tabs.
 */
public final class PlusItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ConstructionWand.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ConstructionWand.MODID);

    // Behaviour cores
    public static final DeferredHolder<Item, Item> CORE_DIGGING = ITEMS.register("core_digging", () -> new ItemCoreDigging(propUpgrade()));
    public static final DeferredHolder<Item, Item> CORE_SLAY = ITEMS.register("core_slay", () -> new ItemCoreSlay(propUpgrade()));
    public static final DeferredHolder<Item, Item> CORE_TIME = ITEMS.register("core_time", () -> new ItemCoreTime(propUpgrade()));
    // Optional core: only exists while both Apotheosis and Apothic Enchanting are installed.
    public static final DeferredHolder<Item, Item> CORE_APOTHEOSIS = registerIfAllLoaded("core_apotheosis",
            new String[]{"apotheosis", "apothic_enchanting"}, () -> new ItemCoreApotheosis(propUpgrade()));

    // Material cores
    // The storage core carries its own binding; the other two only stand for a compatibility module of
    // this fork (see their classes), which is reached through the container handlers that already exist.
    public static final DeferredHolder<Item, Item> CORE_STORAGE = ITEMS.register("core_storage", () -> new ItemCoreStorage(propUpgrade()));
    // Optional cores: only exist while the mod they stand for is installed.
    public static final DeferredHolder<Item, Item> CORE_AE = registerIfLoaded("core_ae", "ae2",
            () -> new ItemCoreAE(propUpgrade()));
    public static final DeferredHolder<Item, Item> CORE_PROJECTE = registerIfLoaded("core_projecte", "projecte",
            () -> new ItemCoreProjectE(propUpgrade()));

    // Passive upgrades
    public static final DeferredHolder<Item, Item> UPGRADE_FORTUNE = ITEMS.register("upgrade_fortune",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_fortune.desc", propUpgrade()));
    public static final DeferredHolder<Item, Item> UPGRADE_SUPER_FORTUNE = ITEMS.register("upgrade_super_fortune",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_super_fortune.desc", propUpgrade()));
    public static final DeferredHolder<Item, Item> UPGRADE_AUTO_SMELT = ITEMS.register("upgrade_auto_smelt",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_auto_smelt.desc", propUpgrade()));
    public static final DeferredHolder<Item, Item> UPGRADE_FLIGHT = ITEMS.register("upgrade_flight",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_flight.desc", propUpgrade()));
    public static final DeferredHolder<Item, Item> UPGRADE_DEFENSE = ITEMS.register("upgrade_defense",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_defense.desc", propUpgrade()));

    public static final DeferredHolder<Item, Item>[] WANDS = ModItems.WANDS;
    public static final DeferredHolder<Item, Item>[] BEHAVIOUR_CORES = new DeferredHolder[]{ModItems.CORE_ANGEL,
            ModItems.CORE_DESTRUCTION, CORE_DIGGING, CORE_SLAY, CORE_TIME, CORE_APOTHEOSIS};
    public static final DeferredHolder<Item, Item>[] MATERIAL_CORES = new DeferredHolder[]{CORE_STORAGE, CORE_AE, CORE_PROJECTE};
    public static final DeferredHolder<Item, Item>[] UPGRADES = new DeferredHolder[]{UPGRADE_FORTUNE,
            UPGRADE_SUPER_FORTUNE, UPGRADE_AUTO_SMELT, UPGRADE_FLIGHT, UPGRADE_DEFENSE};

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ConstructionWand.MODID))
            .icon(() -> {
                Item infinity = ModItems.WAND_INFINITY.get();
                return infinity == null ? ItemStack.EMPTY : new ItemStack(infinity);
            })
            .displayItems((parameters, output) -> {
                for(DeferredHolder<Item, Item> item : WANDS) accept(output, item);
                for(DeferredHolder<Item, Item> item : BEHAVIOUR_CORES) accept(output, item);
                for(DeferredHolder<Item, Item> item : MATERIAL_CORES) accept(output, item);
                for(DeferredHolder<Item, Item> item : UPGRADES) accept(output, item);
            })
            .build());

    private PlusItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }

    /**
     * Registers an item only while the mod it stands for is installed, so a wand never shows a core
     * that cannot work.
     *
     * @return the holder, or {@code null} when that mod is absent
     */
    private static DeferredHolder<Item, Item> registerIfLoaded(String name, String modId,
                                                               java.util.function.Supplier<Item> factory) {
        if(ModList.get() == null || !ModList.get().isLoaded(modId)) return null;
        return ITEMS.register(name, factory);
    }

    /**
     * @return the registered item, or {@code null} while any of the mods is missing
     */
    private static DeferredHolder<Item, Item> registerIfAllLoaded(String name, String[] modIds,
                                                                 java.util.function.Supplier<Item> factory) {
        if(ModList.get() == null) return null;
        for(String modId : modIds) {
            if(!ModList.get().isLoaded(modId)) return null;
        }
        return ITEMS.register(name, factory);
    }

    private static void accept(CreativeModeTab.Output output, DeferredHolder<Item, Item> item) {
        if(item != null && item.isBound()) output.accept(item.get());
    }

    private static Item.Properties propUpgrade() {
        return new Item.Properties().stacksTo(1);
    }
}
