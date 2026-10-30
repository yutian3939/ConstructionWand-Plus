package dev.polaris_light.constructionwand.plus.basics;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/**
 * The numbers the apotheosis core pinned onto enchanting tables, per position and dimension.
 * <p>
 * A right click with the core fixes the three stats of a table instead of opening it, and they stay fixed: every
 * time that table is opened afterwards, the pinned numbers are written onto its menu. The pins belong to the
 * world rather than to a wand, so a table keeps them across restarts and for every player.
 */
public final class ApotheosisPins extends SavedData
{
    /** Name of the file the pins are kept in, inside the data folder of the overworld. */
    private static final String NAME = "constructionwand_apotheosis_pins";

    private static final String KEY_PINS = "pins";
    private static final String KEY_DIMENSION = "dimension";
    private static final String KEY_POS = "pos";
    private static final String KEY_RANK = "rank";
    private static final String KEY_QUANTA = "quanta";
    private static final String KEY_ARCANA = "arcana";

    private final Map<GlobalPos, int[]> pins = new HashMap<>();

    /**
     * @return the pins of that world, read from its data folder the first time they are asked for
     */
    public static ApotheosisPins get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ApotheosisPins::new, ApotheosisPins::load, DataFixTypes.LEVEL), NAME);
    }

    /**
     * @param rank, quanta, arcana the numbers to pin, each clamped to the range a table accepts
     * @return whether something changed, so the caller can save and report it
     */
    public boolean pin(GlobalPos pos, int rank, int quanta, int arcana) {
        int[] values = new int[]{clamp(rank), clamp(quanta), clamp(arcana)};
        int[] old = pins.put(pos, values);
        boolean changed = old == null || old[0] != values[0] || old[1] != values[1] || old[2] != values[2];
        if(changed) setDirty();
        return changed;
    }

    /**
     * @return whether a pin was there to remove
     */
    public boolean unpin(GlobalPos pos) {
        boolean removed = pins.remove(pos) != null;
        if(removed) setDirty();
        return removed;
    }

    /**
     * @return the pinned numbers as {@code {rank, quanta, arcana}}, or {@code null} when the table is not pinned
     */
    @Nullable
    public int[] get(GlobalPos pos) {
        return pins.get(pos);
    }

    public int count() {
        return pins.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for(Map.Entry<GlobalPos, int[]> entry : pins.entrySet()) {
            CompoundTag pin = new CompoundTag();
            pin.putString(KEY_DIMENSION, entry.getKey().dimension().location().toString());
            pin.putLong(KEY_POS, entry.getKey().pos().asLong());
            pin.putInt(KEY_RANK, entry.getValue()[0]);
            pin.putInt(KEY_QUANTA, entry.getValue()[1]);
            pin.putInt(KEY_ARCANA, entry.getValue()[2]);
            list.add(pin);
        }
        tag.put(KEY_PINS, list);
        return tag;
    }

    public static ApotheosisPins load(CompoundTag tag, HolderLookup.Provider registries) {
        ApotheosisPins pins = new ApotheosisPins();
        ListTag list = tag.getList(KEY_PINS, Tag.TAG_COMPOUND);
        for(int index = 0; index < list.size(); index++) {
            CompoundTag pin = list.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(pin.getString(KEY_DIMENSION));
            if(dimension == null) continue;

            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
            pins.pins.put(GlobalPos.of(key, BlockPos.of(pin.getLong(KEY_POS))),
                    new int[]{clamp(pin.getInt(KEY_RANK)), clamp(pin.getInt(KEY_QUANTA)),
                            clamp(pin.getInt(KEY_ARCANA))});
        }
        return pins;
    }

    private static int clamp(int value) {
        return Math.min(ApotheosisLevels.STAT_MAX, Math.max(0, value));
    }
}