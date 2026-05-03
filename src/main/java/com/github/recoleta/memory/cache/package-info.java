/**
 * Soft- and weak-referenced cache primitives used by Recoleta and
 * available to other mods.
 *
 * <p>Every cache here registers its {@link java.lang.ref.ReferenceQueue}
 * with {@link com.github.recoleta.memory.gc.IncrementalCleaner} so that
 * stale entries are evicted in small, bounded slices on each tick
 * instead of in stop-the-world bursts.</p>
 */
package com.github.recoleta.memory.cache;

