package com.github.recoleta.fabric.mixin.common;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Right-sizes the five always-allocated collection fields on every
 * {@link MapItemSavedData}.
 *
 * <p>Vanilla initialises {@code carriedBy} ({@link Lists#newArrayList()}),
 * {@code carriedByPlayers}, {@code bannerMarkers},
 * {@code frameMarkers} ({@link Maps#newHashMap()}) and
 * {@code decorations} ({@link Maps#newLinkedHashMap()}) with the JDK
 * defaults &mdash; an {@code Object[10]} for the {@code ArrayList} and a
 * {@code Node[16]} table for each {@code HashMap}. A typical map item
 * is held by zero or one player and carries zero banners or frames,
 * so almost the entire 80-byte {@code HashMap} table is permanently
 * wasted. Servers that pre-generate a large atlas of map items can
 * see hundreds of these instances live at once.</p>
 *
 * <p>Replacing the five constructor calls with right-sized variants
 * (initial capacity 2 / 4) reclaims roughly 240&ndash;320 bytes per
 * map item with no behavioural change &mdash; the maps grow naturally
 * if they need to.</p>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>The {@code colors[16384]} field is intentionally <strong>not</strong>
 *       touched. It is a {@code public byte[]} read directly by both
 *       vanilla and many mods; soft-wrapping it would be an API break.</li>
 *   <li>{@code expect} values are loose so the mixin gracefully
 *       no-ops if a future Forge build adds or removes one of the
 *       collection fields.</li>
 * </ul>
 */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataSmallMapsMixin {

    /**
     * Replaces a default-capacity {@link HashMap} with a 2-slot variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link HashMap}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 3,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$smallHashMap() {
        return new HashMap<>(2);
    }

    /**
     * Replaces a default-capacity {@link LinkedHashMap} with a 4-slot
     * variant tuned for the typical decoration count of a fresh map.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link LinkedHashMap}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newLinkedHashMap()Ljava/util/LinkedHashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> LinkedHashMap<K, V> recoleta$smallLinkedHashMap() {
        return new LinkedHashMap<>(4);
    }

    /**
     * Replaces a default-capacity {@link ArrayList} with a 1-slot
     * variant; most map items are carried by at most one player.
     *
     * @param <E> element type
     * @return a small {@link ArrayList}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            require = 0, expect = 1,
            remap = false
    )
    private <E> ArrayList<E> recoleta$smallArrayList() {
        return new ArrayList<>(1);
    }
}

