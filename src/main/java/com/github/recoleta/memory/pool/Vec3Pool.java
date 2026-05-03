package com.github.recoleta.memory.pool;

import com.github.recoleta.memory.gc.GenerationalPool;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-local generational pool that recycles a tiny mutable
 * three-double holder for short-lived vector arithmetic.
 *
 * <p>{@link Vec3} itself is immutable (record-like), which is great for
 * correctness but means every operation allocates. For inner-loop
 * computations that never escape the local scope, callers can borrow
 * a {@link Slot}, mutate its components in place, and release it.</p>
 */
public final class Vec3Pool {

    /**
     * Mutable three-double slot. Use directly in arithmetic and convert
     * to an immutable {@link Vec3} only at the outer boundary.
     */
    public static final class Slot {
        /** X component. */ public double x;
        /** Y component. */ public double y;
        /** Z component. */ public double z;

        /**
         * Sets all three components.
         *
         * @param x x component
         * @param y y component
         * @param z z component
         * @return {@code this} for chaining
         */
        public Slot set(final double x, final double y, final double z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }

        /**
         * Materialises this mutable slot into an immutable {@link Vec3}.
         * Allocates - call only at the boundary of the hot loop.
         *
         * @return a fresh immutable vector
         */
        public Vec3 toVec3() {
            return new Vec3(x, y, z);
        }
    }

    private static final ThreadLocal<GenerationalPool<Slot>> POOL =
            ThreadLocal.withInitial(() -> new GenerationalPool<>(Slot::new));

    private static final LongAdder ACQUIRE_COUNT = new LongAdder();
    private static final LongAdder RELEASE_COUNT = new LongAdder();

    private Vec3Pool() {
        /* utility class - never instantiated */
    }

    /**
     * @return a zeroed slot ready to be mutated
     */
    public static Slot acquire() {
        ACQUIRE_COUNT.increment();
        return POOL.get().acquire().set(0.0D, 0.0D, 0.0D);
    }

    /**
     * Returns the slot to its thread-local pool.
     *
     * @param slot instance previously returned from {@link #acquire()}
     */
    public static void release(final Slot slot) {
        RELEASE_COUNT.increment();
        POOL.get().release(slot);
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
        final GenerationalPool<Slot> pool = POOL.get();
        return pool.youngSize() + pool.oldSize();
    }
}

