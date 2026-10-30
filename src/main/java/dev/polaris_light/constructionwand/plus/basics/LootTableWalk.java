package dev.polaris_light.constructionwand.plus.basics;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.TagEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lists every item a loot table can produce, instead of sampling the table.
 * <p>
 * The vanilla API only offers to <em>roll</em> a table, which is why a sampling approach (roll often, keep
 * the union) only ever covers the likely entries: a two percent drop is missed in one roll out of fifty.
 * Reading the table instead is what the Just Enough Resources addon does to show mob drops, and it is the
 * only way to name an entry that is rare but possible. The table is therefore walked: pools, their entries,
 * the child entries of the composite types, tags, and nested tables.
 * <p>
 * An entry type the walk does not know is skipped silently, the same way a loot viewer simply shows what it
 * understands.
 */
public final class LootTableWalk
{
    /** How deep a table may nest another one before the walk gives up; loot tables never nest deeply. */
    private static final int MAX_DEPTH = 8;

    private LootTableWalk() {
    }

    /**
     * @return one stack of each item the table can produce, in the order the table lists them
     */
    public static List<ItemStack> possible(ServerLevel level, LootTable table) {
        List<ItemStack> items = new ArrayList<>();
        walk(level, table, items, new HashSet<>(), new HashSet<>(), 0);
        return items;
    }

    private static void walk(ServerLevel level, LootTable table, List<ItemStack> items, Set<Item> seen,
                             Set<LootTable> visited, int depth) {
        if(depth > MAX_DEPTH || !visited.add(table)) return;

        for(LootPool pool : table.pools) {
            for(LootPoolEntryContainer entry : pool.entries) {
                read(level, entry, items, seen, visited, depth);
            }
        }
    }

    private static void read(ServerLevel level, LootPoolEntryContainer entry, List<ItemStack> items,
                             Set<Item> seen, Set<LootTable> visited, int depth) {
        if(entry instanceof LootItem lootItem) {
            add(lootItem.item, items, seen);
            return;
        }
        if(entry instanceof TagEntry tagEntry) {
            // A tag entry can roll any member of the tag, so every member is a possible drop.
            Optional<HolderSet.Named<Item>> members = level.registryAccess()
                    .lookupOrThrow(Registries.ITEM).get(tagEntry.tag);
            members.ifPresent(holders -> holders.forEach(member -> add(member, items, seen)));
            return;
        }
        if(entry instanceof NestedLootTable nested) {
            Either<ResourceKey<LootTable>, LootTable> contents = nested.contents;
            LootTable inner = contents.right().orElseGet(() -> resolve(level, contents.left().orElse(null)));
            if(inner != null) walk(level, inner, items, seen, visited, depth + 1);
            return;
        }
        if(entry instanceof DynamicLoot dynamic) {
            LootTable inner = resolve(level, ResourceKey.create(Registries.LOOT_TABLE, dynamic.name));
            if(inner != null) walk(level, inner, items, seen, visited, depth + 1);
            return;
        }
        if(entry instanceof CompositeEntryBase composite) {
            // Alternatives, sequences and groups all hold child entries; every child is possible.
            for(LootPoolEntryContainer child : composite.children) {
                read(level, child, items, seen, visited, depth);
            }
        }
        // An empty entry, and anything a mod invented, contributes nothing.
    }

    @Nullable
    private static LootTable resolve(ServerLevel level, @Nullable ResourceKey<LootTable> key) {
        if(key == null || level.getServer() == null) return null;
        return level.getServer().reloadableRegistries().getLootTable(key);
    }

    private static void add(@Nullable Holder<Item> holder, List<ItemStack> items, Set<Item> seen) {
        if(holder == null) return;
        Item item = holder.value();
        if(!seen.add(item)) return;
        items.add(new ItemStack(item));
    }
}