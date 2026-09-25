package com.xinyihl.constructionwandlegacy.lighting;

import com.xinyihl.constructionwandlegacy.config.ModConfig;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Coalesces Chunk lighting bookkeeping performed by one synchronous wand execution.
 */
public final class DeferredLightingBatch implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("ConstructionWandLegacy Lighting");
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final DeferredLightingBatch NOOP = new DeferredLightingBatch(null, false);

    private final State state;
    private final boolean active;
    private boolean closed;

    private DeferredLightingBatch(State state, boolean active) {
        this.state = state;
        this.active = active;
    }

    public static DeferredLightingBatch begin(World world) {
        if (world == null || world.isRemote || !ModConfig.performance.deferredLightingUpdates) {
            return NOOP;
        }

        State current = CURRENT.get();
        if (current != null) {
            if (current.world != world) {
                return NOOP;
            }
            current.depth++;
            return new DeferredLightingBatch(current, true);
        }

        State created = new State(world);
        CURRENT.set(created);
        return new DeferredLightingBatch(created, true);
    }

    /** Called from the injected head of Chunk.relightBlock. */
    public static boolean deferRelight(Chunk chunk, int x, int y, int z) {
        State state = CURRENT.get();
        if (!canDefer(state, chunk) || !isColumnCoordinate(x, z)) {
            return false;
        }
        state.updates.computeIfAbsent(chunk, ignored -> new ChunkUpdates()).recordRelight(x, y, z);
        return true;
    }

    /** Called from the injected head of Chunk.propagateSkylightOcclusion. */
    public static boolean deferSkylightOcclusion(Chunk chunk, int x, int z) {
        State state = CURRENT.get();
        if (!canDefer(state, chunk) || !isColumnCoordinate(x, z)) {
            return false;
        }
        state.updates.computeIfAbsent(chunk, ignored -> new ChunkUpdates()).recordSkylightOcclusion(x, z);
        return true;
    }

    private static boolean canDefer(State state, Chunk chunk) {
        return state != null
                && chunk != null
                && chunk.getWorld() == state.world
                && chunk instanceof ChunkLightingAccess;
    }

    private static boolean isColumnCoordinate(int x, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    @Override
    public void close() {
        if (!active || closed) {
            return;
        }
        closed = true;

        if (Thread.currentThread() != state.owner) {
            LOGGER.error("Deferred lighting batch was closed from a different thread; updates will be flushed by the owner scope");
            return;
        }
        if (--state.depth > 0) {
            return;
        }

        CURRENT.remove();
        state.flush();
    }

    private static final class State {
        private final World world;
        private final Thread owner = Thread.currentThread();
        private final IdentityHashMap<Chunk, ChunkUpdates> updates = new IdentityHashMap<>();
        private int depth = 1;

        private State(World world) {
            this.world = world;
        }

        private void flush() {
            for (Map.Entry<Chunk, ChunkUpdates> entry : updates.entrySet()) {
                Chunk chunk = entry.getKey();
                ChunkLightingAccess access = (ChunkLightingAccess) chunk;
                ChunkUpdates chunkUpdates = entry.getValue();

                for (int column = chunkUpdates.relightColumns.nextSetBit(0); column >= 0; column = chunkUpdates.relightColumns.nextSetBit(column + 1)) {
                    int x = column & 15;
                    int z = column >> 4;
                    try {
                        access.constructionwandlegacy$relightBlock(x, chunkUpdates.maxRelightY[column], z);
                    } catch (RuntimeException exception) {
                        LOGGER.error("Failed to flush deferred relight for chunk {}, column {},{}", chunk.getPos(), x, z, exception);
                    }
                }

                for (int column = chunkUpdates.skylightOcclusionColumns.nextSetBit(0); column >= 0; column = chunkUpdates.skylightOcclusionColumns.nextSetBit(column + 1)) {
                    int x = column & 15;
                    int z = column >> 4;
                    try {
                        access.constructionwandlegacy$propagateSkylightOcclusion(x, z);
                    } catch (RuntimeException exception) {
                        LOGGER.error("Failed to flush deferred skylight occlusion for chunk {}, column {},{}", chunk.getPos(), x, z, exception);
                    }
                }
            }
            updates.clear();
        }
    }

    private static final class ChunkUpdates {
        private final BitSet relightColumns = new BitSet(256);
        private final BitSet skylightOcclusionColumns = new BitSet(256);
        private final int[] maxRelightY = new int[256];

        private ChunkUpdates() {
            Arrays.fill(maxRelightY, Integer.MIN_VALUE);
        }

        private void recordRelight(int x, int y, int z) {
            int column = x | z << 4;
            relightColumns.set(column);
            maxRelightY[column] = Math.max(maxRelightY[column], y);
        }

        private void recordSkylightOcclusion(int x, int z) {
            skylightOcclusionColumns.set(x | z << 4);
        }
    }
}
