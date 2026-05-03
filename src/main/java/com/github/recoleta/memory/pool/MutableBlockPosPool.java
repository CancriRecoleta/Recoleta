package com.github.recoleta.memory.pool;

import com.github.recoleta.memory.gc.GenerationalPool;
import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-local generational pool of {@link BlockPos.MutableBlockPos}.
 *
 * <p>{@code MutableBlockPos} is a 24-byte object whose only state is
 * three {@code int}s plus a header. Allocating one per inner-loop
 * iteration (as a few vanilla code paths do) churns the young
 * generation needlessly. This pool returns a thread-local instance
 * that the caller is expected to release once it goes out of scope.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   BlockPos.MutableBlockPos p = MutableBlockPosPool.acquire().set(x, y, z);
 *   try {
 *       // ... use p ...
 *   } finally {
 *       MutableBlockPosPool.release(p);
 *   }
 * </pre>
 */
public final class MutableBlockPosPool {

    /**
     * Auto-closeable wrapper around a borrowed
     * {@link BlockPos.MutableBlockPos} for {@code try-with-resources}.
     *
     * <pre>
     *   try (MutableBlockPosPool.Lease lease = MutableBlockPosPool.lease()) {
     *       lease.pos.set(x, y, z);
     *       // ... use lease.pos ...
     *   }
     * </pre>
     */
    public static final class Lease implements AutoCloseable {
        /** The borrowed mutable position. */
        public final BlockPos.MutableBlockPos pos;
        private boolean closed;

        Lease(final BlockPos.MutableBlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release(pos);
            }
        }
    }

    /** One generational pool per thread; never shared, so no synchronisation. */
    private static final ThreadLocal<GenerationalPool<BlockPos.MutableBlockPos>> POOL =
            ThreadLocal.withInitial(() -> new GenerationalPool<>(BlockPos.MutableBlockPos::new));

    private static final LongAdder ACQUIRE_COUNT = new LongAdder();
    private static final LongAdder RELEASE_COUNT = new LongAdder();

    private MutableBlockPosPool() {
        /* utility class - never instantiated */
    }

    /**
     * @return a {@code MutableBlockPos} ready to be filled via {@link BlockPos.MutableBlockPos#set(int, int, int)}
     */
    public static BlockPos.MutableBlockPos acquire() {
        ACQUIRE_COUNT.increment();
        return POOL.get().acquire();
    }

    /**
     * Convenience wrapper for {@code try-with-resources} usage.
     *
     * @return an auto-closing lease bound to a fresh mutable position
     */
    public static Lease lease() {
        return new Lease(acquire());
    }

    /**
     * Returns the instance to its thread-local pool.
     *
     * @param pos instance previously returned from {@link #acquire()}
     */
    public static void release(final BlockPos.MutableBlockPos pos) {
        RELEASE_COUNT.increment();
        POOL.get().release(pos);
    }

    public static long acquireCount() {
        return ACQUIRE_COUNT.sum();
    }

    public static long releaseCount() {
        return RELEASE_COUNT.sum();
    }

    public static long outstandingCount() {
        return acquireCount() - releaseCount();
    }

    public static int cachedCountCurrentThread() {
        final GenerationalPool<BlockPos.MutableBlockPos> pool = POOL.get();
        return pool.youngSize() + pool.oldSize();
    }
}

