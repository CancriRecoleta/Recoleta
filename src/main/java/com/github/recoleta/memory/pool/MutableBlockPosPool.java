package com.github.recoleta.memory.pool;

import com.github.recoleta.memory.gc.GenerationalPool;
import net.minecraft.core.BlockPos;

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

    /** One generational pool per thread; never shared, so no synchronisation. */
    private static final ThreadLocal<GenerationalPool<BlockPos.MutableBlockPos>> POOL =
            ThreadLocal.withInitial(() -> new GenerationalPool<>(BlockPos.MutableBlockPos::new));

    private MutableBlockPosPool() {
        /* utility class - never instantiated */
    }

    /**
     * @return a {@code MutableBlockPos} ready to be filled via {@link BlockPos.MutableBlockPos#set(int, int, int)}
     */
    public static BlockPos.MutableBlockPos acquire() {
        return POOL.get().acquire();
    }

    /**
     * Returns the instance to its thread-local pool.
     *
     * @param pos instance previously returned from {@link #acquire()}
     */
    public static void release(final BlockPos.MutableBlockPos pos) {
        POOL.get().release(pos);
    }
}

