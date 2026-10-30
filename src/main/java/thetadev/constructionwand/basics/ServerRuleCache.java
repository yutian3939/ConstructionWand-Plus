package thetadev.constructionwand.basics;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client side cache of the rules the server sent.
 * <p>
 * While no rules have been received (single player, a server without the mod's packets) every
 * accessor falls back to the local configuration, so behaviour is unchanged. As soon as a snapshot
 * arrives the cached values take over, which is what makes the wand screen and the preview agree
 * with a dedicated server.
 */
public final class ServerRuleCache
{
    private static volatile ServerRules rules;
    private static volatile Map<ResourceLocation, ConfigServer.WandProperties> wandProperties = Collections.emptyMap();

    private ServerRuleCache() {
    }

    public static synchronized void apply(@Nullable ServerRules value) {
        rules = value;
        wandProperties = value == null ? Collections.emptyMap() : build(value);
        // The similar block groups and the placement rules are derived from configuration, so they
        // are recompiled from the new source.
        ReplacementRegistry.init();
        PlacementRules.invalidate();
    }

    public static void clear() {
        apply(null);
    }

    public static boolean isActive() {
        return rules != null;
    }

    @Nullable
    public static ServerRules get() {
        return rules;
    }

    /**
     * @return the wand statistics to use, or {@code null} when no rules have been received
     */
    @Nullable
    public static ConfigServer.WandProperties wandProperties(ResourceLocation id) {
        if(rules == null || id == null) return null;
        return wandProperties.get(id);
    }

    private static Map<ResourceLocation, ConfigServer.WandProperties> build(ServerRules value) {
        Map<ResourceLocation, ConfigServer.WandProperties> map = new HashMap<>();
        for(Map.Entry<ResourceLocation, ServerRules.WandRule> entry : value.getWands().entrySet()) {
            ServerRules.WandRule rule = entry.getValue();
            map.put(entry.getKey(), ConfigServer.WandProperties.of(rule.durability(), rule.limit(),
                    rule.angel(), rule.destruction(), rule.upgradeable()));
        }
        return Collections.unmodifiableMap(map);
    }
}
