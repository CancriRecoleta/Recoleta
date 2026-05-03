package com.github.recoleta.memory.header;

import net.minecraft.core.SectionPos;

/**
 * Header-less encoding of a {@link SectionPos} (16<sup>3</sup> chunk
 * section) into a single {@code long}.
 *
 * <p>The bit layout is intentionally identical to
 * {@link SectionPos#asLong(int, int, int)} so the encoded value can be
 * passed directly to {@code SectionPos.of(long)} or stored in vanilla
 * section-keyed maps without any conversion.</p>
 */
public final class PackedSectionPos {

    private PackedSectionPos() {
        /* utility class - never instantiated */
    }

    /**
     * Encodes the three section coordinates.
     *
     * @param sx section X (block X &gt;&gt; 4)
     * @param sy section Y (block Y &gt;&gt; 4)
     * @param sz section Z (block Z &gt;&gt; 4)
     * @return packed value compatible with {@link SectionPos#asLong(int, int, int)}
     */
    public static long pack(final int sx, final int sy, final int sz) {
        return SectionPos.asLong(sx, sy, sz);
    }

    /**
     * Encodes a vanilla {@link SectionPos}.
     *
     * @param pos non-null section position
     * @return packed value
     */
    public static long pack(final SectionPos pos) {
        return pos.asLong();
    }

    /**
     * Extracts the section X coordinate.
     *
     * @param packed value produced by {@link #pack(int, int, int)}
     * @return section X
     */
    public static int unpackX(final long packed) {
        return SectionPos.x(packed);
    }

    /**
     * Extracts the section Y coordinate.
     *
     * @param packed value produced by {@link #pack(int, int, int)}
     * @return section Y
     */
    public static int unpackY(final long packed) {
        return SectionPos.y(packed);
    }

    /**
     * Extracts the section Z coordinate.
     *
     * @param packed value produced by {@link #pack(int, int, int)}
     * @return section Z
     */
    public static int unpackZ(final long packed) {
        return SectionPos.z(packed);
    }

    /**
     * Materialises the packed value into a vanilla {@link SectionPos}.
     *
     * @param packed value produced by {@link #pack(int, int, int)}
     * @return a fresh {@code SectionPos}
     */
    public static SectionPos toSectionPos(final long packed) {
        return SectionPos.of(packed);
    }
}

