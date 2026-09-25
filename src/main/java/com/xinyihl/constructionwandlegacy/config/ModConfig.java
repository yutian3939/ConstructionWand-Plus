package com.xinyihl.constructionwandlegacy.config;

import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID, name = Tags.MOD_NAME)
public final class ModConfig {
    @Config.Name("wandLimits")
    public static final WandLimits wandLimits = new WandLimits();

    @Config.Name("placement")
    public static final Placement placement = new Placement();

    @Config.Name("matching")
    public static final Matching matching = new Matching();

    @Config.Name("wandUpgrades")
    public static final WandUpgrades wandUpgrades = new WandUpgrades();

    @Config.Name("performance")
    public static final Performance performance = new Performance();

    private ModConfig() {
    }

    public static final class WandLimits {
        @Config.RangeInt(min = 1, max = 4096)
        @Config.Comment("Max blocks one use of a Stone Wand may place, break or dig")
        public int stoneWandMaxBlocks = 64;

        @Config.RangeInt(min = 1, max = 4096)
        @Config.Comment("Max blocks one use of an Iron Wand may place, break or dig")
        public int ironWandMaxBlocks = 128;

        @Config.RangeInt(min = 1, max = 4096)
        @Config.Comment("Max blocks one use of a Diamond Wand may place, break or dig")
        public int diamondWandMaxBlocks = 512;

        @Config.RangeInt(min = 1, max = 4096)
        @Config.Comment("Max blocks one use of an Infinity Wand may place, break or dig")
        public int infinityWandMaxBlocks = 1024;
    }

    public static final class Placement {
        @Config.Comment("Allow placing blocks with TileEntity via wand")
        public boolean allowTileEntityPlacement = true;

        @Config.Comment("Placement whitelist entries. Format: modid:block or modid:block@meta. Empty = disabled.")
        public String[] blockWhitelist = new String[0];

        @Config.Comment("Placement blacklist entries. Format: modid:block or modid:block@meta.")
        public String[] blockBlacklist = new String[0];

        @Config.Comment("Whitelist keywords for TARGET mode property copy. Property name containing any keyword will be copied.")
        public String[] propertyCopyWhitelist = new String[]{"facing", "axis", "rotation", "half", "hinge", "shape", "part", "face"};
    }

    public static final class Matching {
        @Config.Comment({
                "Similar block matching groups for SIMILAR mode, entries separated by ';', e.g. minecraft:dirt;minecraft:grass",
                "Entries are block or item ids. Every group matches the blocks of its members against each other.",
                "Fluid blocks have no item and are matched by block: minecraft:water;minecraft:flowing_water treats a source and flowing water as one kind, EXACT keeps them apart."
        })
        public String[] similarBlocks = new String[]{
                "minecraft:grass;minecraft:dirt",
                "minecraft:water;minecraft:flowing_water",
                "minecraft:lava;minecraft:flowing_lava"
        };
    }

    public static final class WandUpgrades {
        @Config.RangeInt(min = 1, max = 10)
        @Config.Comment("Fortune level the fortune upgrade component gives the digging core")
        public int fortuneLevel = 3;
    }

    public static final class Performance {
        @Config.Comment({
                "Defer Chunk height-map and skylight-column updates while a wand executes, then merge them per column.",
                "Experimental: keep disabled unless large wand operations spend significant time in Chunk.relightBlock.",
                "Changing this option requires a full game or server restart so the Chunk Coremod can be applied safely."
        })
        @Config.RequiresMcRestart
        public boolean deferredLightingUpdates = false;
    }
}
