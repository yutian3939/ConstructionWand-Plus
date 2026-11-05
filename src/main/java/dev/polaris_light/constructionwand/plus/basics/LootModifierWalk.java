package dev.polaris_light.constructionwand.plus.basics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.level.ServerLevel;
import dev.polaris_light.constructionwand.ConstructionWand;

import javax.annotation.Nullable;

/**
 * The items other mods inject into the loot of a mob.
 * <p>
 * A global loot modifier is not part of a loot table: it is a small json file under
 * {@code data/&lt;namespace&gt;/loot_modifiers/} that the game applies while a table is rolled, so walking the
 * table itself can never see what it adds. This class reads those files straight from the data pack instead,
 * which is static data and needs no rolling, no sampling and no knowledge of the mods:
 * <ul>
 *   <li>{@code add_item} style modifiers name their items;</li>
 *   <li>{@code inject_loot} and {@code add_table} style modifiers name another table, which is walked as usual.</li>
 * </ul>
 * <h2>What is not offered</h2>
 * A modifier only counts when it says <em>where</em> it belongs: a mob it names, a mob tag it names, or the loot
 * table it is written for. Everything else is left out, because the same folder also holds modifiers for blocks,
 * plants, chests and vaults, and their item has no business being handed out for a kill. A modifier that is about
 * the weapon instead of the victim (a knife, a damage type, the killer's held item) is left out as well: the wand
 * is the weapon here.
 * <p>
 * The chance conditions are deliberately not read. Handing out everything a table can produce is the point of the
 * option, so a one in fifty entry is offered like a guaranteed one.
 */
public final class LootModifierWalk
{
    /** Folder the modifiers live in, inside the data pack. */
    private static final String FOLDER = "loot_modifiers";

    /** Effect types that take loot away or turn it into something else; their item list means the opposite. */
    private static final List<String> TAKING_TYPES = List.of("remove", "replace", "convert", "clear", "smelt");

    /** Conditions about the weapon or the damage dealt, which a wand kill never satisfies. */
    private static final List<String> WEAPON_CONDITIONS = List.of("match_tool", "damage_source_properties",
            "enchantment_active");

    /** Parsed modifiers of the running server. They never change while it runs, so they are read once. */
    private static final Map<ResourceLocation, Modifier> CACHE = new HashMap<>();

    /** The server the cache belongs to; a new server means a new data pack. */
    @Nullable
    private static MinecraftServer loadedFor;

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

    private static void ensureLoaded(MinecraftServer server) {
        if(loadedFor == server) return;
        loadedFor = server;
        CACHE.clear();

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
                Modifier modifier = parse(json);
                if(modifier != null) CACHE.put(entry.getKey(), modifier);
            }
            catch(Exception ignored) {
                // A modifier this class cannot read is simply not offered; the normal roll still happens.
            }
        }
        // Readable in the log at debug level, which the development runs print: a modifier that is missing
        // from this count is one the data pack does not contain, not one the walk rejected.
        ConstructionWand.LOGGER.debug("Read {} loot modifiers for the guaranteed loot", CACHE.size());
    }

    /**
     * @return the modifier, or {@code null} when it only takes loot away or is about the weapon
     */
    @Nullable
    private static Modifier parse(JsonObject json) {
        String type = nameOf(json.get("type"));
        if(type != null) {
            for(String taking : TAKING_TYPES) {
                if(type.contains(taking)) return null;
            }
        }

        Draft draft = new Draft();
        readIds(json.get("items"), draft.items);
        readIds(json.get("item"), draft.items);
        // Every name a mod gave the table it adds: neoforge calls it "table", the injection modifiers call it
        // "loot_table", and a few use the plural.
        readIds(json.get("loot_table"), draft.tables);
        readIds(json.get("loot_tables"), draft.tables);
        readIds(json.get("tables"), draft.tables);
        readIds(json.get("table"), draft.tables);
        // The table an injection is written for. Reliquary names it here as well as in a loot_table_id
        // condition, but a modifier that only writes it into this field would otherwise name no victim at all
        // and be dropped by the check below.
        readIds(json.get("loot_table_to_inject_into"), draft.lootTables);
        readConditions(json.get("conditions"), draft, 0);

        if(draft.denied) return null;
        return new Modifier(List.copyOf(draft.items), List.copyOf(draft.tables), List.copyOf(draft.entityTypes),
                List.copyOf(draft.entityTags), List.copyOf(draft.lootTables));
    }

    /**
     * Reads the conditions of a modifier, including the ones nested in {@code any_of}, {@code all_of} and
     * {@code inverted}.
     */
    private static void readConditions(@Nullable JsonElement element, Draft draft, int depth) {
        if(element == null || depth > 4 || draft.denied) return;

        if(element.isJsonArray()) {
            for(JsonElement child : element.getAsJsonArray()) {
                readConditions(child, draft, depth);
            }
            return;
        }
        if(!element.isJsonObject()) return;

        JsonObject condition = element.getAsJsonObject();

        String name = nameOf(condition.get("condition"));
        if(name != null) {
            for(String weapon : WEAPON_CONDITIONS) {
                if(name.endsWith(weapon)) draft.denied = true;
            }
        }

        // A predicate about the killer describes the weapon, not the loot of the victim. Only a predicate about
        // the victim itself says something about which mobs this modifier is for.
        JsonElement owner = condition.get("entity");
        boolean aboutVictim = owner == null || (owner.isJsonPrimitive() && "this".equals(owner.getAsString()));
        if(!aboutVictim) draft.denied = true;

        JsonElement predicate = condition.get("predicate");
        if(aboutVictim && predicate != null && predicate.isJsonObject()) {
            readEntityType(predicate.getAsJsonObject().get("type"), draft);
        }

        if(aboutVictim) {
            readIds(condition.get("loot_table"), draft.lootTables);
            readIds(condition.get("loot_table_id"), draft.lootTables);
            readIds(condition.get("loot_table_ids"), draft.lootTables);
        }

        for(String key : new String[]{"terms", "term", "conditions", "children", "modifier"}) {
            readConditions(condition.get(key), draft, depth + 1);
        }
    }

    /**
     * Reads an entity type predicate: an id, an array of ids, or a tag written with a leading {@code #}.
     */
    private static void readEntityType(@Nullable JsonElement element, Draft draft) {
        if(element == null) return;

        if(element.isJsonArray()) {
            for(JsonElement child : element.getAsJsonArray()) {
                readEntityType(child, draft);
            }
            return;
        }
        if(!element.isJsonPrimitive()) return;

        String value = element.getAsString();
        if(value.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(value.substring(1));
            if(tag != null) draft.entityTags.add(tag);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if(id != null) draft.entityTypes.add(id);
    }

    /**
     * Reads one id, or an array of ids, or an object holding an {@code item}/{@code id} field, into the list.
     */
    private static void readIds(@Nullable JsonElement element, List<ResourceLocation> out) {
        if(element == null) return;

        if(element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for(JsonElement child : array) {
                readIds(child, out);
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

    /** @return the id of a condition or effect type, or {@code null} when there is none */
    @Nullable
    private static String nameOf(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** What the conditions of one modifier said about where it belongs. */
    private static final class Draft
    {
        final List<ResourceLocation> items = new ArrayList<>();
        final List<ResourceLocation> tables = new ArrayList<>();
        final List<ResourceLocation> entityTypes = new ArrayList<>();
        final List<ResourceLocation> entityTags = new ArrayList<>();
        final List<ResourceLocation> lootTables = new ArrayList<>();
        boolean denied;
    }

    /**
     * @param entityTypes the mobs the modifier names
     * @param entityTags the mob tags the modifier names
     * @param lootTables the loot tables the modifier is written for
     */
    private record Modifier(List<ResourceLocation> items, List<ResourceLocation> tables,
                            List<ResourceLocation> entityTypes, List<ResourceLocation> entityTags,
                            List<ResourceLocation> lootTables)
    {
        private boolean appliesTo(LivingEntity victim, ResourceLocation table) {
            // Guessing is worse than missing an item: a modifier that names neither a mob nor a loot table is
            // about something else, a plant or a chest, and its item would be noise on every corpse.
            if(lootTables.isEmpty() && entityTypes.isEmpty() && entityTags.isEmpty()) return false;

            if(!lootTables.isEmpty() && !lootTables.contains(table)) return false;

            if(!entityTypes.isEmpty() || !entityTags.isEmpty()) {
                EntityType<?> type = victim.getType();
                if(!entityTypes.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                    Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
                    boolean tagged = false;
                    for(ResourceLocation tag : entityTags) {
                        if(holder.is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
                            tagged = true;
                            break;
                        }
                    }
                    if(!tagged) return false;
                }
            }
            return true;
        }
    }
}
