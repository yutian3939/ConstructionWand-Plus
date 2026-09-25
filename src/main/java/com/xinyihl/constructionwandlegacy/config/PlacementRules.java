package com.xinyihl.constructionwandlegacy.config;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class PlacementRules {
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");
    private static final PlacementRules EMPTY = new PlacementRules(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);

    private final List<BlockRule> placementWhitelist;
    private final List<BlockRule> placementBlacklist;
    private final List<String> propertyCopyWhitelist;
    private final boolean allowTileEntityPlacement;

    private PlacementRules(List<BlockRule> placementWhitelist, List<BlockRule> placementBlacklist, List<String> propertyCopyWhitelist, boolean allowTileEntityPlacement) {
        this.placementWhitelist = placementWhitelist;
        this.placementBlacklist = placementBlacklist;
        this.propertyCopyWhitelist = propertyCopyWhitelist;
        this.allowTileEntityPlacement = allowTileEntityPlacement;
    }

    public static PlacementRules empty() {
        return EMPTY;
    }

    public static PlacementRules compile(String[] whitelist, String[] blacklist, String[] propertyKeywords, boolean allowTileEntities) {
        return compile(whitelist, blacklist, propertyKeywords, allowTileEntities, ConfigWarnings::warn, ForgeRegistries.BLOCKS::containsKey);
    }

    public static PlacementRules compile(String[] whitelist, String[] blacklist, String[] propertyKeywords, boolean allowTileEntities, Consumer<String> warningSink) {
        return compile(whitelist, blacklist, propertyKeywords, allowTileEntities, warningSink, ForgeRegistries.BLOCKS::containsKey);
    }

    public static PlacementRules compile(String[] whitelist, String[] blacklist, String[] propertyKeywords, boolean allowTileEntities, Consumer<String> warningSink, Predicate<ResourceLocation> blockExists) {
        Objects.requireNonNull(warningSink, "warningSink");
        Objects.requireNonNull(blockExists, "blockExists");

        List<BlockRule> compiledWhitelist = compileBlockRules(whitelist, "placement whitelist", warningSink, blockExists);
        List<BlockRule> compiledBlacklist = compileBlockRules(blacklist, "placement blacklist", warningSink, blockExists);
        List<String> compiledProperties = compilePropertyRules(propertyKeywords, warningSink);
        return new PlacementRules(compiledWhitelist, compiledBlacklist, compiledProperties, allowTileEntities);
    }

    private static List<BlockRule> compileBlockRules(String[] entries, String ruleName, Consumer<String> warningSink, Predicate<ResourceLocation> blockExists) {
        if (entries == null) {
            warn(warningSink, ruleName, null);
            return Collections.emptyList();
        }

        List<BlockRule> rules = new ArrayList<>();
        for (String raw : entries) {
            BlockRule rule = BlockRule.parse(raw, ruleName, warningSink, blockExists);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private static List<String> compilePropertyRules(String[] entries, Consumer<String> warningSink) {
        if (entries == null) {
            warn(warningSink, "property copy whitelist", null);
            return Collections.emptyList();
        }

        List<String> rules = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) {
                warn(warningSink, "property copy whitelist", null);
                continue;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                warn(warningSink, "property copy whitelist", raw);
                continue;
            }
            rules.add(value);
        }
        return Collections.unmodifiableList(rules);
    }

    private static boolean matchesAny(List<BlockRule> rules, IBlockState state) {
        for (BlockRule rule : rules) {
            if (rule.matches(state)) {
                return true;
            }
        }
        return false;
    }

    private static void warn(Consumer<String> warningSink, String ruleName, @Nullable String raw) {
        warningSink.accept("Invalid " + ruleName + " config entry (raw value: " + raw + ")");
    }

    public boolean isPlacementAllowed(ItemStack placeStack, @Nullable IBlockState placeState) {
        if (placeStack.isEmpty() || !(placeStack.getItem() instanceof ItemBlock)) {
            return false;
        }

        Block block = ((ItemBlock) placeStack.getItem()).getBlock();
        IBlockState state = placeState != null ? placeState : block.getDefaultState();
        if (!allowTileEntityPlacement && block.hasTileEntity(state)) {
            return false;
        }
        if (!placementWhitelist.isEmpty() && !matchesAny(placementWhitelist, state)) {
            return false;
        }
        return !matchesAny(placementBlacklist, state);
    }

    public boolean isPropertyCopyAllowed(@Nullable IProperty<?> property) {
        if (property == null || propertyCopyWhitelist.isEmpty()) {
            return false;
        }

        String name = property.getName().toLowerCase(Locale.ROOT);
        for (String keyword : propertyCopyWhitelist) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static final class BlockRule {
        private final ResourceLocation blockId;
        @Nullable
        private final Integer meta;

        private BlockRule(ResourceLocation blockId, @Nullable Integer meta) {
            this.blockId = blockId;
            this.meta = meta;
        }

        @Nullable
        private static BlockRule parse(@Nullable String raw, String ruleName, Consumer<String> warningSink, Predicate<ResourceLocation> blockExists) {
            if (raw == null) {
                warn(warningSink, ruleName, null);
                return null;
            }

            String value = raw.trim();
            if (value.isEmpty()) {
                warn(warningSink, ruleName, raw);
                return null;
            }

            String[] parts = value.split("@", 2);
            ResourceLocation id;
            try {
                id = new ResourceLocation(parts[0]);
            } catch (RuntimeException exception) {
                warn(warningSink, ruleName, raw);
                return null;
            }
            if (!VALID_NAMESPACE.matcher(id.getNamespace()).matches() || !VALID_PATH.matcher(id.getPath()).matches()) {
                warn(warningSink, ruleName, raw);
                return null;
            }
            if (!blockExists.test(id)) {
                warn(warningSink, ruleName, raw);
                return null;
            }

            Integer meta = null;
            if (parts.length == 2) {
                try {
                    meta = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    warn(warningSink, ruleName, raw);
                    return null;
                }
            }
            return new BlockRule(id, meta);
        }

        private boolean matches(IBlockState state) {
            ResourceLocation stateId = state.getBlock().getRegistryName();
            if (!blockId.equals(stateId)) {
                return false;
            }
            return meta == null || state.getBlock().getMetaFromState(state) == meta;
        }
    }
}
