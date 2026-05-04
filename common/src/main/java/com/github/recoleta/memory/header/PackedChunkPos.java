package com.github.recoleta.memory.header;

import net.minecraft.world.level.ChunkPos;

/**
 * Header-less encoding of a {@link ChunkPos} into a single {@code long}.
 *
 * <p>Layout matches vanilla {@link ChunkPos#asLong(int, int)}: the high
 * 32 bits store {@code z}, the low 32 bits store {@code x}. Stored
 * values therefore interoperate with {@code Long2ObjectOpenHashMap}
 * keyed by {@code ChunkPos.asLong}.</p>
 */
public final class PackedChunkPos {

    private PackedChunkPos() {
        /* utility class - never instantiated */
    }

    /**
     * Encodes the chunk coordinates.
     *
     * @param x chunk X (block X &gt;&gt; 4)
     * @param z chunk Z (block Z &gt;&gt; 4)
     * @return packed value
     */
    public static long pack(final int x, final int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    /**
     * Encodes a vanilla {@link ChunkPos}.
     *
     * @param pos non-null chunk position
     * @return packed value
     */
    public static long pack(final ChunkPos pos) {
        return pack(pos.x, pos.z);
    }

    /**
     * Extracts the chunk X coordinate.
     *
     * @param packed value previously produced by {@link #pack(int, int)}
     * @return chunk X
     */
    public static int unpackX(final long packed) {
        return (int) packed;
    }

    /**
     * Extracts the chunk Z coordinate.
     *
     * @param packed value previously produced by {@link #pack(int, int)}
     * @return chunk Z
     */
    public static int unpackZ(final long packed) {
        return (int) (packed >>> 32);
    }

    /**
     * Materialises the packed value into a vanilla {@link ChunkPos}.
     *
     * @param packed value previously produced by {@link #pack(int, int)}
     * @return a fresh {@code ChunkPos}
     */
    public static ChunkPos toChunkPos(final long packed) {
        return new ChunkPos(unpackX(packed), unpackZ(packed));
    }

    /**
     * Returns the chunk's first block X (origin corner).
     *
     * @param packed value previously produced by {@link #pack(int, int)}
     * @return block X of the chunk's NE corner
     */
    public static int minBlockX(final long packed) {
        return unpackX(packed) << 4;
    }

    /**
     * Returns the chunk's first block Z (origin corner).
     *
     * @param packed value previously produced by {@link #pack(int, int)}
     * @return block Z of the chunk's NE corner
     */
    public static int minBlockZ(final long packed) {
        return unpackZ(packed) << 4;
    }

    /**
     * Returns the squared chess-board distance (in chunks) between two packed positions.
     *
     * @param a first packed chunk
     * @param b second packed chunk
     * @return squared chunk distance
     */
    public static long distanceSq(final long a, final long b) {
        final long dx = (long) (unpackX(a) - unpackX(b));
        final long dz = (long) (unpackZ(a) - unpackZ(b));
        return dx * dx + dz * dz;
    }
}

