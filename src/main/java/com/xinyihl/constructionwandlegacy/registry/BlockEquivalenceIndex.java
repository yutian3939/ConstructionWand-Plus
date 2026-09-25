package com.xinyihl.constructionwandlegacy.registry;

import com.xinyihl.constructionwandlegacy.config.ConfigWarnings;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Similar-block groups for {@code SIMILAR} matching.
 * <p>
 * A group entry can name a block, an item, or - like water and lava - a block that has no item at
 * all, so every group is indexed twice: once by item for the material lookup, once by block for the
 * matching itself.
 */
public final class BlockEquivalenceIndex {
    private static final BlockEquivalenceIndex EMPTY = new BlockEquivalenceIndex(Collections.emptyMap(), Collections.emptyMap());

    private final Map<Item, Set<Item>> matchingItems;
    private final Map<Block, Set<Block>> matchingBlocks;

    private BlockEquivalenceIndex(Map<Item, Set<Item>> matchingItems, Map<Block, Set<Block>> matchingBlocks) {
        this.matchingItems = matchingItems;
        this.matchingBlocks = matchingBlocks;
    }

    public static BlockEquivalenceIndex empty() {
        return EMPTY;
    }

    public static BlockEquivalenceIndex compile(String[] groups) {
        return compile(groups, ConfigWarnings::warn);
    }

    public static BlockEquivalenceIndex compile(String[] groups, Consumer<String> warningSink) {
        Objects.requireNonNull(warningSink, "warningSink");
        if (groups == null) {
            warn(warningSink, null, null);
            return EMPTY;
        }

        Map<Item, Set<Item>> itemIndex = new HashMap<>();
        Map<Block, Set<Block>> blockIndex = new HashMap<>();
        for (String rawGroup : groups) {
            List<Item> items = new ArrayList<>();
            List<Block> blocks = new ArrayList<>();
            parseGroup(rawGroup, warningSink, items, blocks);

            for (Item item : items) {
                Set<Item> matches = itemIndex.computeIfAbsent(item, ignored -> new LinkedHashSet<>());
                matches.addAll(items);
                matches.remove(item);
            }
            for (Block block : blocks) {
                Set<Block> matches = blockIndex.computeIfAbsent(block, ignored -> new LinkedHashSet<>());
                matches.addAll(blocks);
                matches.remove(block);
            }
        }
        return new BlockEquivalenceIndex(immutable(itemIndex), immutable(blockIndex));
    }

    private static <K> Map<K, Set<K>> immutable(Map<K, Set<K>> index) {
        Map<K, Set<K>> result = new HashMap<>();
        for (Map.Entry<K, Set<K>> entry : index.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolves one group into the items and the blocks it names. An entry counts as valid when either
     * registry knows it, which is what makes fluids (blocks without an item) usable in a group.
     */
    private static void parseGroup(@Nullable String rawGroup, Consumer<String> warningSink, List<Item> items, List<Block> blocks) {
        if (rawGroup == null || rawGroup.trim().isEmpty()) {
            warn(warningSink, rawGroup, null);
            return;
        }

        Set<Item> groupItems = new LinkedHashSet<>();
        Set<Block> groupBlocks = new LinkedHashSet<>();
        for (String rawId : rawGroup.split(";", -1)) {
            String value = rawId.trim();
            if (value.isEmpty()) {
                warn(warningSink, rawGroup, rawId);
                continue;
            }

            ResourceLocation id;
            try {
                id = new ResourceLocation(value);
            } catch (RuntimeException exception) {
                warn(warningSink, rawGroup, rawId);
                continue;
            }

            Block block = Block.REGISTRY.getObject(id);
            Item item = Item.REGISTRY.getObject(id);
            if (item == null && block != null) {
                item = Item.getItemFromBlock(block);
            }
            if (block == null && item != null) {
                block = Block.getBlockFromItem(item);
            }

            boolean resolved = false;
            if (item != null && item != Items.AIR) {
                groupItems.add(item);
                resolved = true;
            }
            if (block != null && block != Blocks.AIR) {
                groupBlocks.add(block);
                resolved = true;
            }
            if (!resolved) {
                warn(warningSink, rawGroup, rawId);
            }
        }
        items.addAll(groupItems);
        blocks.addAll(groupBlocks);
    }

    private static void warn(Consumer<String> warningSink, @Nullable String rawGroup, @Nullable String rawId) {
        String detail = rawId == null ? "" : ", invalid id: " + rawId;
        warningSink.accept("Invalid similar-block config entry (raw value: " + rawGroup + detail + ")");
    }

    public Set<Item> matchingItems(Item item) {
        Set<Item> matches = matchingItems.get(item);
        return matches == null ? Collections.emptySet() : matches;
    }

    public boolean matchBlocks(Block first, Block second) {
        if (first == second) {
            return true;
        }
        if (first == Blocks.AIR || second == Blocks.AIR) {
            return false;
        }

        // Fluids and every other block without an item are only reachable through their block.
        if (matchingBlocks(first).contains(second)) {
            return true;
        }

        Item firstItem = Item.getItemFromBlock(first);
        Item secondItem = Item.getItemFromBlock(second);
        if (firstItem == null || secondItem == null) {
            return false;
        }
        return matchingItems(firstItem).contains(secondItem);
    }

    private Set<Block> matchingBlocks(Block block) {
        Set<Block> matches = matchingBlocks.get(block);
        return matches == null ? Collections.emptySet() : matches;
    }
}
