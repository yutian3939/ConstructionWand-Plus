package thetadev.constructionwand.basics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thetadev.constructionwand.items.ModItems;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigServer
{
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue LIMIT_CREATIVE;
    public static final ForgeConfigSpec.IntValue MAX_RANGE;
    public static final ForgeConfigSpec.IntValue UNDO_HISTORY;
    public static final ForgeConfigSpec.BooleanValue ANGEL_FALLING;

    public static final ForgeConfigSpec.ConfigValue<List<?>> SIMILAR_BLOCKS;
    private static final String[] SIMILAR_BLOCKS_DEFAULT = {
            "minecraft:dirt;minecraft:grass_block;minecraft:coarse_dirt;minecraft:podzol;minecraft:mycelium;minecraft:farmland;minecraft:dirt_path;minecraft:rooted_dirt"
    };

    public static final ForgeConfigSpec.BooleanValue TE_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<?>> TE_LIST;
    private static final String[] TE_LIST_DEFAULT = {"chiselsandbits"};

    // Plus content

    public static final ForgeConfigSpec.IntValue FORTUNE_LEVEL;
    public static final ForgeConfigSpec.IntValue MAX_BOUND_CONTAINERS;
    public static final ForgeConfigSpec.BooleanValue DIGGING_BREAK_UNBREAKABLE;
    public static final ForgeConfigSpec.BooleanValue DIGGING_DROP_UNBREAKABLE;
    public static final ForgeConfigSpec.BooleanValue SLAY_AFFECT_PLAYERS;
    public static final ForgeConfigSpec.IntValue SLAY_RADIUS_STONE;
    public static final ForgeConfigSpec.IntValue SLAY_RADIUS_IRON;
    public static final ForgeConfigSpec.IntValue SLAY_RADIUS_DIAMOND;
    public static final ForgeConfigSpec.IntValue SLAY_RADIUS_INFINITY;

    // Placement rules (mirrors the plus config)
    public static final ForgeConfigSpec.ConfigValue<List<?>> BLOCK_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<?>> BLOCK_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<?>> PROPERTY_COPY_WHITELIST;
    private static final String[] PROPERTY_COPY_DEFAULT = {
            "facing", "axis", "rotation", "half", "hinge", "shape", "part", "face"
    };

    private static final HashMap<ResourceLocation, WandProperties> wandProperties = new HashMap<>();

    // Bumped on every snapshot so a client can tell an outdated packet apart.
    private static long revision = 0L;

    /**
     * Slay radius of the slay core, in Manhattan distance, per wand tier.
     */
    public static int getSlayRadius(Item wand) {
        ServerRules rules = ServerRuleCache.get();
        if(rules != null) {
            if(wand == ModItems.WAND_INFINITY.get()) return rules.getSlayInfinity();
            if(wand == ModItems.WAND_DIAMOND.get()) return rules.getSlayDiamond();
            if(wand == ModItems.WAND_IRON.get()) return rules.getSlayIron();
            return rules.getSlayStone();
        }
        if(wand == ModItems.WAND_INFINITY.get()) return SLAY_RADIUS_INFINITY.get();
        if(wand == ModItems.WAND_DIAMOND.get()) return SLAY_RADIUS_DIAMOND.get();
        if(wand == ModItems.WAND_IRON.get()) return SLAY_RADIUS_IRON.get();
        return SLAY_RADIUS_STONE.get();
    }


    public static WandProperties getWandProperties(Item wand) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(wand);
        // The rules the server sent win over the local defaults, so the wand screen and the preview
        // agree with a dedicated server.
        WandProperties fromRules = ServerRuleCache.wandProperties(id);
        if(fromRules != null) return fromRules;
        return wandProperties.getOrDefault(id, WandProperties.DEFAULT);
    }

    /**
     * @return a snapshot of the values a client needs, see {@link ServerRules}
     */
    public static ServerRules toServerRules() {
        Map<ResourceLocation, ServerRules.WandRule> wands = new LinkedHashMap<>();
        for(RegistryObject<Item> holder : ModItems.WANDS) {
            Item item = holder.get();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            WandProperties properties = id == null ? null : wandProperties.get(id);
            if(id == null || properties == null) continue;
            wands.put(id, new ServerRules.WandRule(properties.getDurability(), properties.getLimit(),
                    properties.getAngel(), properties.getDestruction(), properties.isUpgradeable()));
        }

        return new ServerRules(++revision, wands, TE_WHITELIST.get(), ServerRules.sanitize(TE_LIST.get()),
                ServerRules.sanitize(BLOCK_WHITELIST.get()), ServerRules.sanitize(BLOCK_BLACKLIST.get()),
                ServerRules.sanitize(PROPERTY_COPY_WHITELIST.get()), ServerRules.sanitize(SIMILAR_BLOCKS.get()),
                SLAY_RADIUS_STONE.get(), SLAY_RADIUS_IRON.get(), SLAY_RADIUS_DIAMOND.get(), SLAY_RADIUS_INFINITY.get());
    }

    public static class WandProperties
    {
        public static final WandProperties DEFAULT = new WandProperties(null, null, null, null, null, null);

        private final ForgeConfigSpec.IntValue durability;
        private final ForgeConfigSpec.IntValue limit;
        private final ForgeConfigSpec.IntValue angel;
        private final ForgeConfigSpec.IntValue destruction;
        private final ForgeConfigSpec.BooleanValue upgradeable;
        // Non-null for a fixed snapshot (the rules a server sent), null for a config backed instance.
        private final Values values;

        private WandProperties(ForgeConfigSpec.IntValue durability, ForgeConfigSpec.IntValue limit,
                               ForgeConfigSpec.IntValue angel, ForgeConfigSpec.IntValue destruction,
                               ForgeConfigSpec.BooleanValue upgradeable, Values values) {
            this.durability = durability;
            this.limit = limit;
            this.angel = angel;
            this.destruction = destruction;
            this.upgradeable = upgradeable;
            this.values = values;
        }

        public WandProperties(ForgeConfigSpec.Builder builder, RegistryObject<Item> wandSupplier, int defDurability, int defLimit,
                              int defAngel, int defDestruction, boolean defUpgradeable) {
            values = null;
            ResourceLocation registryName = wandSupplier.getId();
            builder.push(registryName.getPath());

            if(defDurability > 0) {
                builder.comment("Wand durability");
                durability = builder.defineInRange("durability", defDurability, 1, Integer.MAX_VALUE);
            }
            else durability = null;
            builder.comment("Wand block limit");
            limit = builder.defineInRange("limit", defLimit, 1, Integer.MAX_VALUE);
            builder.comment("Max placement distance with angel core (0 to disable angel core)");
            angel = builder.defineInRange("angel", defAngel, 0, Integer.MAX_VALUE);
            builder.comment("Destruction core switch for this wand tier (0 disables it). The number of blocks it may break is the wand's 'limit' above, same as construction and digging.");
            destruction = builder.defineInRange("destruction", defDestruction, 0, Integer.MAX_VALUE);
            builder.comment("Allow wand upgrading by putting the wand together with a wand core in a crafting grid.");
            upgradeable = builder.define("upgradeable", defUpgradeable);
            builder.pop();

            wandProperties.put(registryName, this);
        }

        /**
         * Creates a fixed set of values, used for the rules a client received from a server.
         */
        public static WandProperties of(int durability, int limit, int angel, int destruction, boolean upgradeable) {
            return new WandProperties(null, null, null, null, null, new Values(durability, limit, angel, destruction, upgradeable));
        }

        public int getDurability() {
            if(values != null) return values.durability;
            return durability == null ? -1 : durability.get();
        }

        public int getLimit() {
            if(values != null) return values.limit;
            return limit == null ? 0 : limit.get();
        }

        public int getAngel() {
            if(values != null) return values.angel;
            return angel == null ? 0 : angel.get();
        }

        public int getDestruction() {
            if(values != null) return values.destruction;
            return destruction == null ? 0 : destruction.get();
        }

        /**
         * @return whether the destruction core is switched off for this wand tier. The entry no
         * longer sets the block count, it only still disables the core when set to 0, so the core
         * shares the wand's block limit with construction and digging.
         */
        public boolean isDestructionDisabled() {
            return getDestruction() <= 0;
        }

        public boolean isUpgradeable() {
            if(values != null) return values.upgradeable;
            return upgradeable != null && upgradeable.get();
        }

        private record Values(int durability, int limit, int angel, int destruction, boolean upgradeable)
        {
        }
    }

    static {
        final var builder = new ForgeConfigSpec.Builder();

        builder.comment("This is the Server config for ConstructionWand.",
                "If you're not familiar with Forge's new split client/server config, let me explain:",
                "Client config is stored in the /config folder and only contains client specific settings like graphics and keybinds.",
                "Mod behavior is configured in the Server config, which is world-specific and thus located",
                "in the /saves/myworld/serverconfig folder. If you want to change the serverconfig for all",
                "new worlds, copy the config files in the /defaultconfigs folder.");

        new WandProperties(builder, ModItems.WAND_STONE, Tiers.STONE.getUses(), 9, 0, 0, false);
        new WandProperties(builder, ModItems.WAND_IRON, Tiers.IRON.getUses(), 27, 2, 9, true);
        new WandProperties(builder, ModItems.WAND_DIAMOND, Tiers.DIAMOND.getUses(), 128, 8, 25, true);
        new WandProperties(builder, ModItems.WAND_INFINITY, 0, 1024, 16, 81, true);

        builder.push("misc");
        builder.comment("Block limit for Infinity Wand used in creative mode");
        LIMIT_CREATIVE = builder.defineInRange("InfinityWandCreative", 2048, 1, Integer.MAX_VALUE);
        builder.comment("Maximum placement range (0: unlimited). Affects all wands and is meant for lag prevention, not game balancing.");
        MAX_RANGE = builder.defineInRange("MaxRange", 100, 0, Integer.MAX_VALUE);
        builder.comment("Number of operations that can be undone");
        UNDO_HISTORY = builder.defineInRange("UndoHistory", 3, 0, Integer.MAX_VALUE);
        builder.comment("Place blocks below you while falling > 10 blocks with angel core (Can be used to save you from drops/the void)");
        ANGEL_FALLING = builder.define("AngelFalling", false);
        builder.comment("Blocks to treat equally when in Similar mode. Enter block IDs seperated by ;");
        SIMILAR_BLOCKS = builder.defineList("SimilarBlocks", Arrays.asList(SIMILAR_BLOCKS_DEFAULT), obj -> true);
        builder.pop();

        builder.push("tileentity");
        builder.comment("White/Blacklist for Tile Entities. Allow/Prevent blocks with TEs from being placed by wand.",
                "You can either add block ids like minecraft:chest or mod ids like minecraft");
        TE_LIST = builder.defineList("TEList", Arrays.asList(TE_LIST_DEFAULT), obj -> true);
        builder.comment("If set to TRUE, treat TEList as a whitelist, otherwise blacklist");
        TE_WHITELIST = builder.define("TEWhitelist", false);
        builder.pop();

        builder.push("wandUpgrades");
        builder.comment("Fortune level the fortune upgrade component gives the digging core");
        FORTUNE_LEVEL = builder.defineInRange("fortuneLevel", 3, 1, 10);
        builder.pop();

        builder.push("storage");
        builder.comment("Max containers one wand may bind for the storage core. Set -1 for unlimited.");
        MAX_BOUND_CONTAINERS = builder.defineInRange("maxBoundContainers", 8, -1, 4096);
        builder.pop();

        builder.push("digging");
        builder.comment("Whether the digging core may break unbreakable blocks (bedrock, barrier, ...).");
        DIGGING_BREAK_UNBREAKABLE = builder.define("breakUnbreakable", false);
        builder.comment("Whether broken unbreakable blocks are dropped. Only applies when breakUnbreakable is enabled.");
        DIGGING_DROP_UNBREAKABLE = builder.define("dropUnbreakable", false);
        builder.pop();

        builder.push("slay");
        builder.comment("Whether the slay core may kill players as well. The wielder is never targeted.");
        SLAY_AFFECT_PLAYERS = builder.define("affectPlayers", true);
        builder.comment("Max slay radius (Manhattan distance) of the slay core on a Stone Wand");
        SLAY_RADIUS_STONE = builder.defineInRange("stoneSlayRadius", 16, 1, 256);
        builder.comment("Max slay radius (Manhattan distance) of the slay core on an Iron Wand");
        SLAY_RADIUS_IRON = builder.defineInRange("ironSlayRadius", 32, 1, 256);
        builder.comment("Max slay radius (Manhattan distance) of the slay core on a Diamond Wand");
        SLAY_RADIUS_DIAMOND = builder.defineInRange("diamondSlayRadius", 64, 1, 256);
        builder.comment("Max slay radius (Manhattan distance) of the slay core on an Infinity Wand");
        SLAY_RADIUS_INFINITY = builder.defineInRange("infinitySlayRadius", 128, 1, 256);
        builder.pop();

        builder.push("placement");
        builder.comment("Placement whitelist entries. Format: modid:block. Empty = disabled.");
        BLOCK_WHITELIST = builder.defineList("BlockWhitelist", Collections.emptyList(), obj -> obj instanceof String);
        builder.comment("Placement blacklist entries. Format: modid:block.");
        BLOCK_BLACKLIST = builder.defineList("BlockBlacklist", Collections.emptyList(), obj -> obj instanceof String);
        builder.comment("Whitelist keywords for TARGET mode property copy. A property whose name contains any keyword is copied.");
        PROPERTY_COPY_WHITELIST = builder.defineList("PropertyCopyWhitelist", Arrays.asList(PROPERTY_COPY_DEFAULT), obj -> obj instanceof String);
        builder.pop();

        SPEC = builder.build();
    }
}
