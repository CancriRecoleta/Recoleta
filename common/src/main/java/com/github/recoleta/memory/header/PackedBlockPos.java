package com.github.recoleta.memory.header;

import net.minecraft.core.BlockPos;

/**
 * Header-less encoding of a {@link BlockPos} into a single {@code long}.
 *
 * <p>Bit layout matches vanilla {@link BlockPos#asLong(int, int, int)}
 * exactly so that packed values can be stored in - and round-tripped
 * through - the {@code Long2ObjectOpenHashMap} structures that vanilla
 * itself uses for chunk and entity bookkeeping:</p>
 *
 * <pre>
 *   bits 64..38  : signed X    (26 bits, range +-33,554,432)
 *   bits 37..26  : signed Y    (12 bits, range +-2048)
 *   bits 25..0   : signed Z    (26 bits, range +-33,554,432)
 * </pre>
 *
 * <p>All operations are static and allocation-free. They are safe to
 * call from any thread; the encoded value is a pure primitive.</p>
 *
 * <p>Per-instance footprint vs vanilla {@code BlockPos} (24 B with
 * header padding on JDK 17): <b>8 B</b> &mdash; a 3x reduction, which
 * compounds rapidly when storing thousands of positions in a tick loop
 * or chunk cache.</p>
 */
public final class PackedBlockPos {

    /** X coordinate occupies the upper 26 bits. */
    private static final int X_BITS = 26;
    /** Y coordinate occupies the middle 12 bits. */
    private static final int Y_BITS = 12;
    /** Z coordinate occupies the lower 26 bits. */
    private static final int Z_BITS = 26;

    private static final long X_MASK = (1L << X_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;

    private static final int Y_SHIFT = Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + Y_BITS;

    /** Sentinel value safely outside the valid world range; useful for "absent" marker keys. */
    public static final long INVALID = Long.MIN_VALUE;

    private PackedBlockPos() {
        /* utility class - never instantiated */
    }

    /**
     * Encodes the three block coordinates into a single {@code long}.
     *
     * @param x world block X
     * @param y world block Y
     * @param z world block Z
     * @return the packed value, identical to {@link BlockPos#asLong(int, int, int)}
     */
    public static long pack(final int x, final int y, final int z) {
        return ((long) x & X_MASK) << X_SHIFT
             | ((long) y & Y_MASK) << Y_SHIFT
             | ((long) z & Z_MASK);
    }

    /**
     * Encodes a vanilla {@link BlockPos}.
     *
     * @param pos non-null position
     * @return the packed value
     */
    public static long pack(final BlockPos pos) {
        return pack(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Extracts the X component of a packed value (sign-extended).
     *
     * @param packed value previously produced by {@link #pack(int, int, int)}
     * @return signed world block X
     */
    public static int unpackX(final long packed) {
        return (int) (packed << (64 - X_SHIFT - X_BITS) >> (64 - X_BITS));
    }

    /**
     * Extracts the Y component of a packed value (sign-extended).
     *
     * @param packed value previously produced by {@link #pack(int, int, int)}
     * @return signed world block Y
     */
    public static int unpackY(final long packed) {
        return (int) (packed << (64 - Y_SHIFT - Y_BITS) >> (64 - Y_BITS));
    }

    /**
     * Extracts the Z component of a packed value (sign-extended).
     *
     * @param packed value previously produced by {@link #pack(int, int, int)}
     * @return signed world block Z
     */
    public static int unpackZ(final long packed) {
        return (int) (packed << (64 - Z_BITS) >> (64 - Z_BITS));
    }

    /**
     * Materialises the packed value into a vanilla immutable {@link BlockPos}.
     * This <strong>does</strong> allocate; prefer
     * {@link #toMutable(long, BlockPos.MutableBlockPos)} on hot paths.
     *
     * @param packed value previously produced by {@link #pack(int, int, int)}
     * @return a fresh {@code BlockPos}
     */
    public static BlockPos toBlockPos(final long packed) {
        return new BlockPos(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    /**
     * Writes the packed coordinates into the supplied mutable target,
     * avoiding any allocation.
     *
     * @param packed value previously produced by {@link #pack(int, int, int)}
     * @param target mutable position to update in place
     * @return {@code target} for chaining
     */
    public static BlockPos.MutableBlockPos toMutable(final long packed, final BlockPos.MutableBlockPos target) {
        return target.set(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    /**
     * Returns the squared distance between two packed positions, computed
     * without unpacking through {@code BlockPos}.
     *
     * @param a first packed value
     * @param b second packed value
     * @return squared block distance
     */
    public static long distanceSq(final long a, final long b) {
        final long dx = (long) (unpackX(a) - unpackX(b));
        final long dy = (long) (unpackY(a) - unpackY(b));
        final long dz = (long) (unpackZ(a) - unpackZ(b));
        return dx * dx + dy * dy + dz * dz;
    }
}

