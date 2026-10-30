package thetadev.constructionwand.plus.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.items.ModItems;

/**
 * Registration of everything the plus content adds.
 * <p>
 * All of it lives in one dedicated creative tab, in the order wands, behaviour cores, material cores,
 * upgrades, so the wand, its cores and its upgrades are not scattered across the vanilla tabs.
 */
public final class PlusItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ConstructionWand.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ConstructionWand.MODID);

    // Behaviour cores
    public static final RegistryObject<Item> CORE_DIGGING = ITEMS.register("core_digging", () -> new ItemCoreDigging(propUpgrade()));
    public static final RegistryObject<Item> CORE_SLAY = ITEMS.register("core_slay", () -> new ItemCoreSlay(propUpgrade()));

    // Material cores
    public static final RegistryObject<Item> CORE_STORAGE = ITEMS.register("core_storage", () -> new ItemCoreStorage(propUpgrade()));
    // Optional cores: only exist while the mod they need is installed.
    public static final RegistryObject<Item> CORE_AE = registerConditional("core_ae",
            thetadev.constructionwand.plus.compat.ae2.AE2Compat::isLoaded,
            () -> new thetadev.constructionwand.plus.compat.ae2.ItemCoreAE(propUpgrade()));
    public static final RegistryObject<Item> CORE_PROJECTE = registerConditional("core_projecte",
            thetadev.constructionwand.plus.compat.projecte.ProjectECompat::isLoaded,
            () -> new thetadev.constructionwand.plus.compat.projecte.ItemCoreProjectE(propUpgrade()));

    // Passive upgrades
    public static final RegistryObject<Item> UPGRADE_FORTUNE = ITEMS.register("upgrade_fortune",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_fortune.desc", propUpgrade()));
    public static final RegistryObject<Item> UPGRADE_AUTO_SMELT = ITEMS.register("upgrade_auto_smelt",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_auto_smelt.desc", propUpgrade()));
    public static final RegistryObject<Item> UPGRADE_FLIGHT = ITEMS.register("upgrade_flight",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_flight.desc", propUpgrade()));
    public static final RegistryObject<Item> UPGRADE_DEFENSE = ITEMS.register("upgrade_defense",
            () -> new ItemWandUpgrade(ConstructionWand.MODID + ".upgrade.upgrade_defense.desc", propUpgrade()));

    public static final RegistryObject<Item>[] WANDS = ModItems.WANDS;
    public static final RegistryObject<Item>[] BEHAVIOUR_CORES = new RegistryObject[]{ModItems.CORE_ANGEL, ModItems.CORE_DESTRUCTION, CORE_DIGGING, CORE_SLAY};
    public static final RegistryObject<Item>[] MATERIAL_CORES = new RegistryObject[]{CORE_STORAGE, CORE_AE, CORE_PROJECTE};
    public static final RegistryObject<Item>[] UPGRADES = new RegistryObject[]{UPGRADE_FORTUNE, UPGRADE_AUTO_SMELT, UPGRADE_FLIGHT, UPGRADE_DEFENSE};

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ConstructionWand.MODID))
            .icon(() -> ModItems.WAND_INFINITY.get() == null ? ItemStack.EMPTY : new ItemStack(ModItems.WAND_INFINITY.get()))
            .displayItems((parameters, output) -> {
                for(RegistryObject<Item> item : WANDS) accept(output, item);
                for(RegistryObject<Item> item : BEHAVIOUR_CORES) accept(output, item);
                for(RegistryObject<Item> item : MATERIAL_CORES) accept(output, item);
                for(RegistryObject<Item> item : UPGRADES) accept(output, item);
            })
            .build());

    private PlusItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }

    /**
     * Registers an item only when the mod it belongs to is installed, so a wand never shows a core
     * that cannot work.
     */
    private static RegistryObject<Item> registerConditional(String name, java.util.function.BooleanSupplier condition,
                                                            java.util.function.Supplier<Item> factory) {
        return condition.getAsBoolean() ? ITEMS.register(name, factory) : null;
    }

    private static void accept(CreativeModeTab.Output output, RegistryObject<Item> item) {
        if(item != null && item.isPresent()) output.accept(item.get());
    }

    private static Item.Properties propUpgrade() {
        return new Item.Properties().stacksTo(1);
    }
}
