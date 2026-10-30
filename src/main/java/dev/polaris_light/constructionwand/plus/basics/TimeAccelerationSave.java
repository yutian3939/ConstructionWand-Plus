package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The accelerated blocks of a world, written next to the level data.
 * <p>
 * A permanent effect that vanished as soon as the player left the world would be no permanent effect at
 * all, so the table is part of the save. It is kept in the overworld of the server and holds the dimension
 * of every entry itself, because one table covers the whole server and not one level.
 * <p>
 * The time outside the world is not counted: a saved entry comes back with the time it had, the same way
 * an entry in an unloaded chunk is held while the chunk is away.
 */
public class TimeAccelerationSave extends SavedData
{
    public static final String NAME = "constructionwand_time_acceleration";

    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_RATE = "rate";
    private static final String TAG_REMAINING = "remaining";
    private static final String TAG_TOTAL = "total";
    private static final String TAG_HEADING = "heading";
    private static final String TAG_BLOCK = "block";

    public TimeAccelerationSave() {
    }

    /**
     * Reads the table of a save and hands it to {@link TimeAcceleration} right away, so an entry is in place
     * before the first tick of the world.
     */
    public static TimeAccelerationSave load(CompoundTag tag, HolderLookup.Provider registries) {
        List<TimeAcceleration.StoredEntry> entries = new ArrayList<>();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for(int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString(TAG_DIMENSION)));
            entries.add(new TimeAcceleration.StoredEntry(dimension, BlockPos.of(entry.getLong(TAG_POS)),
                    entry.getUUID(TAG_OWNER), entry.getInt(TAG_RATE), entry.getInt(TAG_REMAINING),
                    entry.getInt(TAG_TOTAL), entry.getFloat(TAG_HEADING), entry.getString(TAG_BLOCK)));
        }

        TimeAcceleration.restore(entries);
        return new TimeAccelerationSave();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for(TimeAcceleration.StoredEntry entry : TimeAcceleration.storedSnapshot()) {
            CompoundTag stored = new CompoundTag();
            stored.putString(TAG_DIMENSION, entry.dimension().location().toString());
            stored.putLong(TAG_POS, entry.pos().asLong());
            stored.putUUID(TAG_OWNER, entry.owner());
            stored.putInt(TAG_RATE, entry.rate());
            stored.putInt(TAG_REMAINING, entry.remaining());
            stored.putInt(TAG_TOTAL, entry.total());
            stored.putFloat(TAG_HEADING, entry.heading());
            stored.putString(TAG_BLOCK, entry.blockId());
            list.add(stored);
        }

        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    /**
     * Opens the table of a running server. Reading it, if it has not been read yet, is what puts the
     * accelerations of the save back in place.
     */
    public static TimeAccelerationSave of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TimeAccelerationSave::new, TimeAccelerationSave::load), NAME);
    }

    /**
     * Marks the table as changed whenever an acceleration is added or taken away, and answers for the file
     * being written with the world.
     */
    public static Runnable dirtyMarker(ServerLevel overworld) {
        return () -> of(overworld).setDirty();
    }
}
