package com.xinyihl.constructionwandlegacy.lighting;

/**
 * Implemented on Chunk by the lighting coremod.
 */
public interface ChunkLightingAccess {
    void constructionwandlegacy$relightBlock(int x, int y, int z);

    void constructionwandlegacy$propagateSkylightOcclusion(int x, int z);
}
