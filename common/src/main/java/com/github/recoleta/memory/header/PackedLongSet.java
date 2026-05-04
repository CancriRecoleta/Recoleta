package com.github.recoleta.memory.header;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Allocation-thin set of packed {@code long} values.
 *
 * <p>Wraps fastutil's {@link LongOpenHashSet} (already shipped by
 * Forge) so that callers using {@link PackedBlockPos},
 * {@link PackedChunkPos} or {@link PackedSectionPos} can store
 * thousands of positions without ever boxing into {@link Long}.</p>
 *
 * <p>Per-entry footprint: 8 B in a {@code long[]} backing array, vs
 * 24 B for an immutable {@code BlockPos} in a {@code HashSet}
 * (plus the 32-48 B {@code HashMap.Node} wrapper in the latter).</p>
 */
public final class PackedLongSet {

    /** Backing fastutil set. Public because exposing a richer API would bloat this class. */
    private final LongOpenHashSet backing;

    /**
     * Creates an empty set with the default fastutil capacity.
     */
    public PackedLongSet() {
        this.backing = new LongOpenHashSet();
    }

    /**
     * Creates an empty set sized for the expected number of entries.
     *
     * @param expected number of entries the caller expects to store
     */
    public PackedLongSet(final int expected) {
        this.backing = new LongOpenHashSet(expected);
    }

    /**
     * Adds a packed value.
     *
     * @param packed value to insert
     * @return {@code true} if the value was not already present
     */
    public boolean add(final long packed) {
        return backing.add(packed);
    }

    /**
     * Removes a packed value.
     *
     * @param packed value to remove
     * @return {@code true} if the value was present
     */
    public boolean remove(final long packed) {
        return backing.remove(packed);
    }

    /**
     * @param packed value to test
     * @return {@code true} if the value is currently in the set
     */
    public boolean contains(final long packed) {
        return backing.contains(packed);
    }

    /**
     * @return current cardinality
     */
    public int size() {
        return backing.size();
    }

    /**
     * Empties the set and shrinks the backing table to its minimum capacity.
     */
    public void clearAndCompact() {
        backing.clear();
        backing.trim();
    }

    /**
     * @return a primitive iterator over the packed values; never allocates {@link Long}
     */
    public LongIterator iterator() {
        return backing.iterator();
    }
}

