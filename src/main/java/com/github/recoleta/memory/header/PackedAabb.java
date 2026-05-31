package com.github.recoleta.memory.header;

import net.minecraft.world.phys.AABB;

/**
 * Quantised, header-less encoding of an {@link AABB} into a single {@code long}.
 *
 * <p>Each axis-minimum is stored as a {@code short} delta around an
 * origin block in 1/16-block units, while extents share the lowest 16
 * bits. This is intended for short-lived broad-phase queries
 * (entity-vs-block sweeps, particle culling) where the loss of fine
 * precision is acceptable but the per-tick allocation churn of
 * thousands of {@code AABB} objects is not.</p>
 *
 * <pre>
 *   bits 63..48 : minX delta  (signed short, units of 1/16 block)
 *   bits 47..32 : minY delta  (signed short, units of 1/16 block)
 *   bits 31..16 : minZ delta  (signed short, units of 1/16 block)
 *   bits 15..0  : extents     (X:6 | Y:5 | Z:5 bits, units of 1/16 block)
 * </pre>
 *
 * <p>Per-instance footprint vs vanilla {@code AABB} (six doubles + header
 * = 56 B): <b>8 B</b> &mdash; a 7x reduction.</p>
 */
public final class PackedAabb {

    /** Quantisation step: 1 unit = 1/16 of a block. */
    public static final double UNIT = 1.0D / 16.0D;

    private PackedAabb() {
        /* utility class - never instantiated */
    }

    /**
     * Encodes a vanilla {@link AABB} relative to an origin block.
     *
     * <p>The box must lie within ~2048 blocks of the origin and have
     * extents below 4 blocks on X and 2 blocks on Y/Z. Inputs outside
     * those ranges are clamped silently.</p>
     *
     * @param box      non-null bounding box
     * @param originX  block-X origin used as the encoding reference
     * @param originY  block-Y origin used as the encoding reference
     * @param originZ  block-Z origin used as the encoding reference
     * @return packed value
     */
    public static long pack(final AABB box, final int originX, final int originY, final int originZ) {
        final short dx = quantiseShort(box.minX - originX);
        final short dy = quantiseShort(box.minY - originY);
        final short dz = quantiseShort(box.minZ - originZ);
        final int ex = clamp((int) Math.round((box.maxX - box.minX) / UNIT), 0, 63);
        final int ey = clamp((int) Math.round((box.maxY - box.minY) / UNIT), 0, 31);
        final int ez = clamp((int) Math.round((box.maxZ - box.minZ) / UNIT), 0, 31);
        final int extents = (ex & 0x3F) << 10 | (ey & 0x1F) << 5 | (ez & 0x1F);
        return ((long) dx & 0xFFFFL) << 48
             | ((long) dy & 0xFFFFL) << 32
             | ((long) dz & 0xFFFFL) << 16
             | ((long) extents & 0xFFFFL);
    }

    /**
     * Materialises the packed value back into a vanilla {@link AABB}.
     *
     * @param packed   value produced by {@link #pack(AABB, int, int, int)}
     * @param originX  the same origin used at pack time
     * @param originY  the same origin used at pack time
     * @param originZ  the same origin used at pack time
     * @return a fresh {@code AABB}
     */
    public static AABB toAabb(final long packed, final int originX, final int originY, final int originZ) {
        final double minX = originX + (short) (packed >>> 48) * UNIT;
        final double minY = originY + (short) (packed >>> 32) * UNIT;
        final double minZ = originZ + (short) (packed >>> 16) * UNIT;
        final int extents = (int) (packed & 0xFFFFL);
        final double sizeX = ((extents >>> 10) & 0x3F) * UNIT;
        final double sizeY = ((extents >>> 5) & 0x1F) * UNIT;
        final double sizeZ = (extents & 0x1F) * UNIT;
        return new AABB(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
    }

    /**
     * Quantises a double-precision delta into a {@code short} in 1/16
     * units, with clamping at {@code short} range boundaries.
     *
     * @param value delta in blocks
     * @return quantised short
     */
    private static short quantiseShort(final double value) {
        final long q = Math.round(value / UNIT);
        if (q > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (q < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) q;
    }

    private static int clamp(final int v, final int lo, final int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}

