package dev.polaris_light.constructionwand.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The accelerated blocks the server told this client about.
 * <p>
 * The list is replaced as a whole whenever the server changes it, and the remaining time is counted down
 * here in between so the client can draw it without the server having to send anything per tick. An effect
 * of {@code -1} never runs out.
 */
public final class TimeClientState
{
    private static final Map<ResourceKey<Level>, Map<BlockPos, TimeAcceleration.Entry>> ACTIVE = new HashMap<>();

    private TimeClientState() {
    }

    /**
     * Replaces everything that is known, which is what the server sends on every change.
     */
    public static void accept(List<TimeAcceleration.Entry> entries) {
        ACTIVE.clear();
        for(TimeAcceleration.Entry entry : entries) {
            ACTIVE.computeIfAbsent(entry.dimension(), key -> new HashMap<>()).put(entry.pos(), entry);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /**
     * Counts the remaining time down, and forgets what has run out. Called once per client tick.
     */
    public static void tick() {
        if(ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, TimeAcceleration.Entry>>> levels = ACTIVE.entrySet().iterator();
        while(levels.hasNext()) {
            Map<BlockPos, TimeAcceleration.Entry> blocks = levels.next().getValue();
            Iterator<Map.Entry<BlockPos, TimeAcceleration.Entry>> entries = blocks.entrySet().iterator();
            while(entries.hasNext()) {
                Map.Entry<BlockPos, TimeAcceleration.Entry> block = entries.next();
                TimeAcceleration.Entry entry = block.getValue();
                if(entry.remaining() == TimeAcceleration.PERMANENT) continue;
                if(entry.remaining() <= 1) {
                    entries.remove();
                    continue;
                }
                block.setValue(new TimeAcceleration.Entry(entry.dimension(), entry.pos(), entry.rate(),
                        entry.remaining() - 1, entry.total(), entry.heading(), entry.blockId()));
            }
            if(blocks.isEmpty()) levels.remove();
        }
    }

    /**
     * @return the accelerated blocks of a dimension, empty when there are none
     */
    /**
     * @return every block that is accelerated right now, in every dimension, in a stable order. The screen
     * lists them so the player can see what the wand is doing, not just where it is pointed.
     */
    public static List<TimeAcceleration.Entry> all() {
        List<TimeAcceleration.Entry> entries = new java.util.ArrayList<>();
        ACTIVE.values().forEach(blocks -> entries.addAll(blocks.values()));
        entries.sort(java.util.Comparator.comparingLong(entry -> entry.pos().asLong()));
        return entries;
    }

    public static List<TimeAcceleration.Entry> of(@Nullable Level level) {
        if(level == null) return List.of();
        Map<BlockPos, TimeAcceleration.Entry> blocks = ACTIVE.get(level.dimension());
        return blocks == null ? List.of() : new ArrayList<>(blocks.values());
    }

    @Nullable
    public static TimeAcceleration.Entry at(@Nullable Level level, BlockPos pos) {
        if(level == null) return null;
        Map<BlockPos, TimeAcceleration.Entry> blocks = ACTIVE.get(level.dimension());
        return blocks == null ? null : blocks.get(pos);
    }
}
