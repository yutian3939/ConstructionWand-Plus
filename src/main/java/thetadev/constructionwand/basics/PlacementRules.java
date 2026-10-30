package thetadev.constructionwand.basics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import thetadev.constructionwand.ConstructionWand;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Placement whitelist/blacklist and the property copy keywords.
 * <p>
 * The rules are compiled lazily from either the local configuration or the server rules the client
 * received, and recompiled when {@link ServerRuleCache} changes. An empty whitelist allows every
 * block; the blacklist always wins.
 */
public final class PlacementRules
{
    private static volatile Compiled compiled;

    private PlacementRules() {
    }

    public static void invalidate() {
        compiled = null;
    }

    private static Compiled compiled() {
        Compiled local = compiled;
        if(local == null) {
            local = compile();
            compiled = local;
        }
        return local;
    }

    private static Compiled compile() {
        List<?> whitelist;
        List<?> blacklist;
        List<?> properties;

        ServerRules rules = ServerRuleCache.get();
        if(rules != null) {
            whitelist = rules.getPlacementWhitelist();
            blacklist = rules.getPlacementBlacklist();
            properties = rules.getPropertyCopyWhitelist();
        }
        else {
            whitelist = ConfigServer.BLOCK_WHITELIST.get();
            blacklist = ConfigServer.BLOCK_BLACKLIST.get();
            properties = ConfigServer.PROPERTY_COPY_WHITELIST.get();
        }
        return new Compiled(toIds(whitelist), toIds(blacklist), toKeywords(properties));
    }

    /**
     * @return whether the block may be placed by the wand at all
     */
    public static boolean isPlacementAllowed(BlockItem item, @Nullable BlockState state) {
        if(item == null || state == null) return false;

        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if(id == null) return false;

        Compiled rules = compiled();
        if(!rules.whitelist.isEmpty() && !rules.whitelist.contains(id)) return false;
        return !rules.blacklist.contains(id);
    }

    /**
     * @return whether a block property may be copied from the supporting block in TARGET mode. A
     * property is copied when its name contains any of the configured keywords.
     */
    public static boolean isPropertyCopyAllowed(@Nullable Property<?> property) {
        if(property == null) return false;
        Compiled rules = compiled();
        if(rules.properties.isEmpty()) return false;

        String name = property.getName().toLowerCase(Locale.ROOT);
        for(String keyword : rules.properties) {
            if(name.contains(keyword)) return true;
        }
        return false;
    }

    private static Set<ResourceLocation> toIds(List<?> entries) {
        if(entries == null || entries.isEmpty()) return Collections.emptySet();
        Set<ResourceLocation> ids = new HashSet<>();
        for(Object entry : entries) {
            if(!(entry instanceof String text)) continue;
            ResourceLocation id = parse(text);
            if(id != null) ids.add(id);
        }
        return ids;
    }

    private static List<String> toKeywords(List<?> entries) {
        if(entries == null || entries.isEmpty()) return Collections.emptyList();
        List<String> keywords = new ArrayList<>();
        for(Object entry : entries) {
            if(!(entry instanceof String text)) continue;
            String value = text.trim().toLowerCase(Locale.ROOT);
            if(!value.isEmpty()) keywords.add(value);
        }
        return keywords;
    }

    /**
     * Accepts {@code modid:block} and tolerates the {@code modid:block@meta} form of older versions
     * by ignoring the meta suffix.
     */
    @Nullable
    private static ResourceLocation parse(String raw) {
        String value = raw.trim();
        int meta = value.indexOf('@');
        if(meta >= 0) value = value.substring(0, meta);
        if(value.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if(id == null) {
            ConstructionWand.LOGGER.warn("Invalid placement rule entry: " + raw);
            return null;
        }
        return id;
    }

    private static final class Compiled
    {
        private final Set<ResourceLocation> whitelist;
        private final Set<ResourceLocation> blacklist;
        private final List<String> properties;

        private Compiled(Set<ResourceLocation> whitelist, Set<ResourceLocation> blacklist, List<String> properties) {
            this.whitelist = whitelist;
            this.blacklist = blacklist;
            this.properties = properties;
        }
    }
}
