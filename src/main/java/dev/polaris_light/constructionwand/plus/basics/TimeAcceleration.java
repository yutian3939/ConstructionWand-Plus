package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.Direction;
import java.util.Set;
import java.util.HashSet;
import java.util.Deque;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import dev.polaris_light.constructionwand.basics.ConfigServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The blocks the time core keeps accelerated.
 * <p>
 * An accelerated block is ticked more than once per game tick: the block entity ticker vanilla would have
 * called once is called {@code rate} times in total, and a block that only grows by random ticks gets
 * {@code rate} times the usual random tick chances.
 * <p>
 * The speed and the duration are the wand's own settings and are applied as they are: right clicking a
 * block again simply re-applies them, so nothing has to be climbed by clicking. A duration of {@code -1}
 * never runs out.
 * <p>
 * The table lives on the server and is written into the save as {@link TimeAccelerationSave}, so leaving the
 * world and coming back keeps the effects; the time spent outside the world is not counted, the same way a
 * block in an unloaded chunk is held. A block that is gone or can no longer be ticked is forgotten.
 */
public final class TimeAcceleration
{
    /**
     * The average number of game ticks between two random ticks of a block, which is what vanilla's three
     * random ticks per chunk section and tick amount to. Extra random ticks are rolled against it so that
     * a multiplier of {@code n} really means {@code n} times the growth speed.
     */
    private static final int RANDOM_TICK_AVERAGE = 1365;

    /**
     * The remaining time of an effect that never runs out.
     */
    public static final int PERMANENT = -1;

    private static final Map<ResourceKey<Level>, Map<BlockPos, Stored>> ACTIVE = new HashMap<>();

    private TimeAcceleration() {
    }

    /**
     * The blocks that belong to the same body as the one pointed at: everything reachable through touching
     * faces that is the same block, which is what a vein, a wall or a machine made of one block looks like.
     * The walk stops at {@code limit} blocks so a floor of stone cannot turn one click into a thousand.
     *
     * @return the blocks found, the pointed at block first, never more than {@code limit}
     */
    public static List<BlockPos> connected(Level level, BlockPos origin, int limit) {
        List<BlockPos> found = new ArrayList<>(Math.min(limit, 64));
        BlockState originState = level.getBlockState(origin);
        if(originState.isAir() || limit <= 0) return found;

        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(origin);
        seen.add(origin);

        while(!open.isEmpty() && found.size() < limit) {
            BlockPos pos = open.poll();
            found.add(pos);
            for(Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if(found.size() + open.size() >= limit) break;
                if(!seen.add(next)) continue;
                if(!level.getBlockState(next).is(originState.getBlock())) continue;
                open.add(next);
            }
        }
        return found;
    }

    public enum Result
    {
        /** the block is accelerated with the settings it was given */
        APPLIED,
        /** the acceleration of the block was taken away */
        REMOVED,
        /** the block was not accelerated, so there was nothing to take away */
        NOT_ACCELERATED,
        /** the block cannot be accelerated at all */
        INVALID,
        /** the time core is switched off on this server */
        DISABLED
    }

    /**
     * @param remaining ticks left, or {@link #PERMANENT}
     */
    public record Outcome(Result result, int rate, int remaining)
    {
        public boolean permanent() {
            return remaining == PERMANENT;
        }

        public int seconds() {
            return remaining == PERMANENT ? 0 : Math.max(0, remaining / 20);
        }
    }

    /**
     * One accelerated block, as it travels to the clients that draw it.
     */
    public record Entry(ResourceKey<Level> dimension, BlockPos pos, int rate, int remaining, int total,
                        float heading, String blockId)
    {
    }

    /**
     * One accelerated block, with the player who paid for it, as it is written into the save. The owner is
     * what "cancel everything I accelerated" works from, so it has to survive a reload as well.
     */
    public record StoredEntry(ResourceKey<Level> dimension, BlockPos pos, UUID owner, int rate, int remaining,
                              int total, float heading, String blockId)
    {
    }

    private record Stored(UUID owner, int rate, int remaining, int total, float heading, String blockId)
    {
        private Entry toEntry(ResourceKey<Level> dimension, BlockPos pos) {
            return new Entry(dimension, pos, rate, remaining, total, heading, blockId);
        }
    }

    /**
     * Rounds a yaw to the nearest of the four sides a block can be read from: south, west, north, east. A
     * readout laid out at an angle would be neither upright nor clearly turned, so only these four are used.
     *
     * @return the yaw in degrees, in {@code [0, 360)}
     */
    public static float cardinal(float yaw) {
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        snapped %= 360.0F;
        return snapped < 0.0F ? snapped + 360.0F : snapped;
    }

    /**
     * @return the multiplier the block is currently accelerated at, or {@code 0} when it is not
     */
    public static int current(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Stored> entries = ACTIVE.get(level.dimension());
        if(entries == null) return 0;
        Stored entry = entries.get(pos);
        return entry == null ? 0 : entry.rate();
    }

    /**
     * Applies the settings of the wand to the block, replacing whatever it was accelerated with before.
     */
    public static Outcome apply(ServerPlayer player, ServerLevel level, BlockPos pos, int rate, int durationTicks) {
        if(!ConfigServer.TIME_CORE_ENABLED.get()) return new Outcome(Result.DISABLED, 0, 0);
        if(rate <= 1) return new Outcome(Result.INVALID, 0, 0);

        BlockState state = level.getBlockState(pos);
        if(!isAccelerable(level, pos, state)) return new Outcome(Result.INVALID, 0, 0);

        Map<BlockPos, Stored> entries = ACTIVE.computeIfAbsent(level.dimension(), key -> new LinkedHashMap<>());
        if(!entries.containsKey(pos)) evictOldest(entries, player.getUUID());
        // The heading is the direction the player faces right now, rounded to one of the four sides: the
        // readout on the top of the block is written to be read from there and keeps that side for as long
        // as the effect lasts.
        // The id of the block is stored too: the screen can then name a block in another dimension, or in a
        // chunk this client has never loaded.
        entries.put(pos, new Stored(player.getUUID(), rate, durationTicks, durationTicks,
                cardinal(player.getYRot()), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        markDirty();

        // Tick it right away, so the very first click is visible before the next tick comes around.
        accelerate(level, pos, rate, state);
        return new Outcome(Result.APPLIED, rate, durationTicks);
    }

    /**
     * Takes the acceleration off a block, which is what a sneaking right click with the time core does.
     */
    public static Result remove(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Stored> entries = ACTIVE.get(level.dimension());
        if(entries == null || entries.remove(pos) == null) return Result.NOT_ACCELERATED;
        if(entries.isEmpty()) ACTIVE.remove(level.dimension());
        markDirty();
        return Result.REMOVED;
    }

    /**
     * Takes every acceleration of one player away, wherever it is in the world.
     *
     * @return how many blocks were let go
     */
    public static int clearOwnedBy(UUID owner) {
        int removed = 0;
        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Stored>>> levels = ACTIVE.entrySet().iterator();
        while(levels.hasNext()) {
            Map<BlockPos, Stored> entries = levels.next().getValue();
            Iterator<Map.Entry<BlockPos, Stored>> blocks = entries.entrySet().iterator();
            while(blocks.hasNext()) {
                if(blocks.next().getValue().owner().equals(owner)) {
                    blocks.remove();
                    removed++;
                }
            }
            if(entries.isEmpty()) levels.remove();
        }
        if(removed > 0) markDirty();
        return removed;
    }

    /**
     * Whether the block offers anything to accelerate: a block entity with a ticker, or a block that grows
     * by random ticks.
     */
    public static boolean isAccelerable(ServerLevel level, BlockPos pos, BlockState state) {
        if(state.isAir()) return false;
        if(isDenied(state)) return false;
        if(!level.isInWorldBounds(pos)) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if(blockEntity != null) return ticker(level, blockEntity) != null;
        return state.isRandomlyTicking();
    }

    /**
     * Runs the extra ticks of every accelerated block. Called once per server tick.
     *
     * @return whether anything was forgotten, which is when the clients have to be told
     */
    public static boolean tick(MinecraftServer server) {
        if(ACTIVE.isEmpty()) return false;

        boolean changed = false;
        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Stored>>> levels = ACTIVE.entrySet().iterator();
        while(levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, Stored>> levelEntry = levels.next();
            ServerLevel level = server.getLevel(levelEntry.getKey());
            if(level == null) continue;

            Iterator<Map.Entry<BlockPos, Stored>> blocks = levelEntry.getValue().entrySet().iterator();
            while(blocks.hasNext()) {
                Map.Entry<BlockPos, Stored> blockEntry = blocks.next();
                BlockPos pos = blockEntry.getKey();
                Stored entry = blockEntry.getValue();

                // Held while the chunk is not loaded, so time does not pass for a block nobody can see.
                if(!level.isLoaded(pos)) continue;

                // The entry is dropped when the block it belongs to is no longer there, not when the
                // block merely declines to be ticked this once: a machine that is paused, or a block whose
                // own logic skips a tick, used to take a permanent acceleration with it.
                boolean replaced = !entry.blockId().isEmpty()
                        && !BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()
                        .equals(entry.blockId());
                boolean refused = !accelerate(level, pos, entry.rate(), null);
                boolean gone = replaced || (refused && entry.remaining() != PERMANENT);
                // A permanent effect has no timer to run out.
                boolean expired = !gone && entry.remaining() != PERMANENT && entry.remaining() <= 1;
                if(gone || expired) {
                    blocks.remove();
                    changed = true;
                    continue;
                }
                if(entry.remaining() != PERMANENT) {
                    blockEntry.setValue(new Stored(entry.owner(), entry.rate(), entry.remaining() - 1,
                            entry.total(), entry.heading(), entry.blockId()));
                }
            }

            if(levelEntry.getValue().isEmpty()) levels.remove();
        }
        if(changed) markDirty();
        return changed;
    }

    /**
     * @return every accelerated block of every dimension, for the clients that draw them
     */
    public static List<Entry> snapshot() {
        List<Entry> entries = new ArrayList<>();
        for(Map.Entry<ResourceKey<Level>, Map<BlockPos, Stored>> level : ACTIVE.entrySet()) {
            for(Map.Entry<BlockPos, Stored> block : level.getValue().entrySet()) {
                entries.add(block.getValue().toEntry(level.getKey(), block.getKey()));
            }
        }
        return entries;
    }

    /**
     * Forgets every acceleration, called when the server stops so a new world does not inherit them.
     */
    /**
     * Told whenever the table gains or loses a block, so the save can be marked as changed. Timer runs are
     * not reported: the save reads the current table when the world is written, so a countdown is stored
     * without a write behind every tick.
     */
    @Nullable
    private static Runnable dirtyListener;

    public static void setDirtyListener(@Nullable Runnable listener) {
        dirtyListener = listener;
    }

    private static void markDirty() {
        if(dirtyListener != null) dirtyListener.run();
    }

    /**
     * @return every accelerated block with its owner, for the save
     */
    public static List<StoredEntry> storedSnapshot() {
        List<StoredEntry> entries = new ArrayList<>();
        for(Map.Entry<ResourceKey<Level>, Map<BlockPos, Stored>> level : ACTIVE.entrySet()) {
            for(Map.Entry<BlockPos, Stored> block : level.getValue().entrySet()) {
                Stored stored = block.getValue();
                entries.add(new StoredEntry(level.getKey(), block.getKey(), stored.owner(), stored.rate(),
                        stored.remaining(), stored.total(), stored.heading(), stored.blockId()));
            }
        }
        return entries;
    }

    /**
     * Puts the table of a save back in place. Called while the level is being read, before anything ticks.
     */
    public static void restore(List<StoredEntry> entries) {
        ACTIVE.clear();
        for(StoredEntry entry : entries) {
            if(entry.rate() <= 1) continue;
            ACTIVE.computeIfAbsent(entry.dimension(), key -> new LinkedHashMap<>())
                    .put(entry.pos(), new Stored(entry.owner(), entry.rate(), entry.remaining(), entry.total(),
                            entry.heading(), entry.blockId()));
        }
        markDirty();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /**
     * Ticks the block {@code rate - 1} extra times, so that together with the tick vanilla does the block
     * runs exactly {@code rate} times per game tick.
     *
     * @param state the block state when it is already known, {@code null} to read it
     * @return whether the block is still accelerable
     */
    @SuppressWarnings("unchecked")
    private static boolean accelerate(ServerLevel level, BlockPos pos, int rate, @Nullable BlockState state) {
        BlockState current = state == null ? level.getBlockState(pos) : state;
        if(current.isAir()) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if(blockEntity != null) {
            if(blockEntity.isRemoved()) return false;

            BlockEntityType<BlockEntity> type = (BlockEntityType<BlockEntity>) blockEntity.getType();
            BlockEntityTicker<BlockEntity> ticker = current.getTicker(level, type);
            if(ticker == null) return false;

            for(int i = 1; i < rate; i++) {
                ticker.tick(level, pos, blockEntity.getBlockState(), blockEntity);
            }
            return true;
        }

        if(current.isRandomlyTicking()) {
            RandomSource random = level.getRandom();
            for(int i = 1; i < rate; i++) {
                if(random.nextInt(RANDOM_TICK_AVERAGE) == 0) current.randomTick(level, pos, random);
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static BlockEntityTicker<BlockEntity> ticker(ServerLevel level, BlockEntity blockEntity) {
        BlockEntityType<BlockEntity> type = (BlockEntityType<BlockEntity>) blockEntity.getType();
        return blockEntity.getBlockState().getTicker(level, type);
    }

    /**
     * Keeps one player from holding an unbounded number of blocks accelerated. The oldest one of that
     * player gives way, which is the one they are least likely to still be watching.
     */
    private static void evictOldest(Map<BlockPos, Stored> entries, UUID owner) {
        int max = ConfigServer.TIME_CORE_MAX_ACTIVE.get();
        if(max <= 0) return;

        int owned = 0;
        for(Stored entry : entries.values()) {
            if(entry.owner().equals(owner)) owned++;
        }
        if(owned < max) return;

        Iterator<Map.Entry<BlockPos, Stored>> blocks = entries.entrySet().iterator();
        while(blocks.hasNext()) {
            if(blocks.next().getValue().owner().equals(owner)) {
                blocks.remove();
                return;
            }
        }
    }

    /**
     * A block that must never be accelerated, from the configuration. A plain mod id denies everything
     * that mod adds, which is how the tile entity list of the stock config works as well.
     */
    private static boolean isDenied(BlockState state) {
        List<?> denied = ConfigServer.TIME_CORE_DENY.get();
        if(denied.isEmpty()) return false;

        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for(Object entry : denied) {
            if(!(entry instanceof String text) || text.isEmpty()) continue;
            if(text.equals(id)) return true;
            if(!text.contains(":") && id.startsWith(text + ":")) return true;
        }
        return false;
    }
}
