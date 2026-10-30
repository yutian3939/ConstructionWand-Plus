package thetadev.constructionwand.basics;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the server side wand configuration that clients need.
 * <p>
 * The wand screen, the block preview and the matching all read configuration values. On a dedicated
 * server the client never sees the server's config file, so the values that influence those views
 * (per wand statistics, tile entity rules, placement rules and similar block groups) are shipped to
 * the client in one packet and used there instead of the local defaults.
 */
public final class ServerRules
{
    /**
     * Bounds of the variable length fields, so a broken or malicious packet cannot allocate freely.
     */
    public static final int MAX_LIST_ENTRIES = 1024;
    public static final int MAX_STRING_CHARS = 512;

    private final long revision;
    private final Map<ResourceLocation, WandRule> wands;
    private final boolean teWhitelist;
    private final List<String> teList;
    private final List<String> placementWhitelist;
    private final List<String> placementBlacklist;
    private final List<String> propertyCopyWhitelist;
    private final List<String> similarBlocks;
    private final int slayStone;
    private final int slayIron;
    private final int slayDiamond;
    private final int slayInfinity;

    public ServerRules(long revision, Map<ResourceLocation, WandRule> wands, boolean teWhitelist, List<String> teList,
                       List<String> placementWhitelist, List<String> placementBlacklist, List<String> propertyCopyWhitelist,
                       List<String> similarBlocks, int slayStone, int slayIron, int slayDiamond, int slayInfinity) {
        this.revision = revision;
        this.wands = Collections.unmodifiableMap(new LinkedHashMap<>(wands));
        this.teWhitelist = teWhitelist;
        this.teList = immutable(teList);
        this.placementWhitelist = immutable(placementWhitelist);
        this.placementBlacklist = immutable(placementBlacklist);
        this.propertyCopyWhitelist = immutable(propertyCopyWhitelist);
        this.similarBlocks = immutable(similarBlocks);
        this.slayStone = slayStone;
        this.slayIron = slayIron;
        this.slayDiamond = slayDiamond;
        this.slayInfinity = slayInfinity;
    }

    public static List<String> sanitize(@Nullable List<?> values) {
        if(values == null || values.isEmpty()) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>(Math.min(values.size(), MAX_LIST_ENTRIES));
        for(Object value : values) {
            if(result.size() == MAX_LIST_ENTRIES) break;
            if(!(value instanceof String text)) continue;
            if(text.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_CHARS) continue;
            result.add(text);
        }
        return result;
    }

    private static List<String> immutable(List<String> values) {
        if(values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * The wand statistics the client needs to display limits and run its preview.
     */
    public record WandRule(int durability, int limit, int angel, int destruction, boolean upgradeable)
    {
    }

    public long getRevision() {
        return revision;
    }

    public Map<ResourceLocation, WandRule> getWands() {
        return wands;
    }

    public boolean isTeWhitelist() {
        return teWhitelist;
    }

    public List<String> getTeList() {
        return teList;
    }

    public List<String> getPlacementWhitelist() {
        return placementWhitelist;
    }

    public List<String> getPlacementBlacklist() {
        return placementBlacklist;
    }

    public List<String> getPropertyCopyWhitelist() {
        return propertyCopyWhitelist;
    }

    public List<String> getSimilarBlocks() {
        return similarBlocks;
    }

    public int getSlayStone() {
        return slayStone;
    }

    public int getSlayIron() {
        return slayIron;
    }

    public int getSlayDiamond() {
        return slayDiamond;
    }

    public int getSlayInfinity() {
        return slayInfinity;
    }
}
