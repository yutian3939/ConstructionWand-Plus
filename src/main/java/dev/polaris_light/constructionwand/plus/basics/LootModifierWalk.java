package dev.polaris_light.constructionwand.plus.basics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * The items other mods inject into the loot of a mob.
 * <p>
 * A global loot modifier is not part of a loot table: it is a small json file under
 * {@code data/&lt;namespace&gt;/loot_modifiers/} that the game applies while a table is rolled, so walking the
 * table itself can never see what it adds. This class reads those files straight from the data pack instead,
 * which is static data and needs no rolling, no sampling and no knowledge of the mods:
 * <ul>
 *   <li>{@code add_item} / {@code remove_item} style modifiers name their items;</li>
 *   <li>{@code add_table} style modifiers name another table, which is walked as usual.</li>
 * </ul>
 * The conditions are only read for the entity they belong to: a modifier that names a mob is applied to that mob
 * alone, so a table injection for creepers does not hand out its item for a cow.
 */
public final class LootModifierWalk
{
    /** Folder the modifiers live in, inside the data pack. */
    private static final String FOLDER = "loot_modifiers";

    /** Parsed modifiers of the running server, dropped whenever the server starts. */
    private static final Map<ResourceLocation, Modifier> CACHE = new HashMap<>();

    private LootModifierWalk() {
    }

    /**
     * @return the items every modifier of the data pack would inject into that victim's loot, whether or not
     * its chance would have fired
     */
    public static List<ItemStack> injected(ServerLevel level, LivingEntity victim) {
        List<ItemStack> found = new ArrayList<>();
        MinecraftServer server = level.getServer();
        if(server == null) return found;

        ensureLoaded(server);

        ResourceLocation table = victim.getLootTable().location();
        for(Modifier modifier : CACHE.values()) {
            if(!modifier.appliesTo(victim, table)) continue;

            for(ResourceLocation item : modifier.items()) {
                Item resolved = BuiltInRegistries.ITEM.containsKey(item) ? BuiltInRegistries.ITEM.get(item) : null;
                if(resolved != null) found.add(new ItemStack(resolved));
            }
            for(ResourceLocation nested : modifier.tables()) {
                LootTable nestedTable = server.reloadableRegistries()
                        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, nested));
                found.addAll(LootTableWalk.possible(level, nestedTable));
            }
        }
        return found;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static void ensureLoaded(MinecraftServer server) {
        if(!CACHE.isEmpty()) return;

        Map<ResourceLocation, Resource> files;
        try {
            files = server.getResourceManager().listResources(FOLDER, path -> path.getPath().endsWith(".json"));
        }
        catch(Exception e) {
            return;
        }

        for(Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            try(var reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                CACHE.put(entry.getKey(), parse(json));
            }
            catch(Exception ignored) {
                // A modifier this class cannot read is simply not offered; the normal roll still happens.
            }
        }
    }

    private static Modifier parse(JsonObject json) {
        List<ResourceLocation> items = new ArrayList<>();
        List<ResourceLocation> tables = new ArrayList<>();
        List<ResourceLocation> entityTypes = new ArrayList<>();
        List<ResourceLocation> lootTables = new ArrayList<>();
        boolean entityFree = true;

        collectLocations(json.get("items"), items);
        collectLocations(json.get("item"), items);
        collectLocations(json.get("loot_table"), tables);
        collectLocations(json.get("tables"), tables);

        JsonElement conditions = json.get("conditions");
        if(conditions != null && conditions.isJsonArray()) {
            for(JsonElement element : conditions.getAsJsonArray()) {
                if(!element.isJsonObject()) continue;
                JsonObject condition = element.getAsJsonObject();
                JsonElement predicate = condition.get("predicate");
                if(predicate != null && predicate.isJsonObject()) {
                    JsonElement type = predicate.getAsJsonObject().get("type");
                    collectLocations(type, entityTypes);
                    if(type != null) entityFree = false;
                }
                // neoforge:loot_table_id names the table the modifier is meant for; reliquary and others use
                // the same idea under the longer key, so both are read.
                JsonElement table = condition.get("loot_table");
                collectLocations(table, lootTables);
                if(table != null) entityFree = false;

                JsonElement tableId = condition.get("loot_table_id");
                collectLocations(tableId, lootTables);
                if(tableId != null) entityFree = false;
            }
        }

        return new Modifier(List.copyOf(items), List.copyOf(tables), List.copyOf(entityTypes),
                List.copyOf(lootTables), entityFree);
    }

    /**
     * Reads one id, or an array of ids, or an object holding an {@code item}/{@code id} field, into the list.
     */
    private static void collectLocations(@Nullable JsonElement element, List<ResourceLocation> out) {
        if(element == null) return;

        if(element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for(JsonElement child : array) {
                collectLocations(child, out);
            }
            return;
        }

        if(element.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if(id != null) out.add(id);
            return;
        }

        if(element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for(String key : new String[]{"item", "id", "name", "type", "loot_table"}) {
                JsonElement child = object.get(key);
                if(child != null && child.isJsonPrimitive()) {
                    ResourceLocation id = ResourceLocation.tryParse(child.getAsString());
                    if(id != null) out.add(id);
                }
            }
        }
    }

    /**
     * @param entityFree whether the modifier names no entity at all, so it applies to every victim
     */
    private record Modifier(List<ResourceLocation> items, List<ResourceLocation> tables,
                            List<ResourceLocation> entityTypes, List<ResourceLocation> lootTables,
                            boolean entityFree)
    {
        private boolean appliesTo(LivingEntity victim, ResourceLocation table) {
            if(!lootTables.isEmpty() && !lootTables.contains(table)) return false;
            if(!entityTypes.isEmpty()) return entityTypes.contains(
                    BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
            return entityFree;
        }
    }
}