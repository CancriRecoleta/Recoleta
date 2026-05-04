package com.github.recoleta.memory.cache;

/**
 * Singleton holder for the cross-mod {@link WeakInternTable} instances
 * consumed by Recoleta mixins.
 *
 * <p>Defining the tables in a single, statically-reachable place avoids
 * the lifecycle hazard of class-init ordering: vanilla constructors run
 * extremely early (some during JVM bootstrap before any mod has been
 * registered) and need a guaranteed-available intern table the moment
 * they ask for it. The fields here are class-load eager so the first
 * vanilla {@code ResourceLocation} or {@code CompoundTag} constructor
 * call after Recoleta loads succeeds without further wiring.</p>
 */
public final class RecoletaInterns {

    /**
     * Shared intern table for arbitrary {@link String} values produced by
     * vanilla. Currently used by the mixins on
     * {@code net.minecraft.resources.ResourceLocation} and
     * {@code net.minecraft.nbt.CompoundTag} to canonicalise namespaces,
     * paths and tag keys.
     */
    public static final WeakInternTable<String> STRINGS = new WeakInternTable<>();

    /**
     * Shared intern table for {@link net.minecraft.network.chat.Style}
     * values. Vanilla {@code MutableComponent} fields like color, bold
     * and italic flags are stored on a {@code Style} record-like
     * object; many Components share identical style content but each
     * gets its own instance through {@code Component#withStyle(...)}
     * derivations. Interning canonicalises those instances so a
     * long-lived component tree only retains one {@code Style} per
     * unique style content.
     *
     * <p>{@code Style} provides correct {@code equals}/{@code hashCode}
     * across all ten of its fields, so the {@link WeakInternTable}
     * uses content equality unmodified. Soft references on the
     * canonical entries let the JVM reclaim styles whose only strong
     * referrers have been garbage-collected.</p>
     */
    public static final WeakInternTable<net.minecraft.network.chat.Style> STYLES = new WeakInternTable<>();

    private RecoletaInterns() {
        /* singleton holder - never instantiated */
    }
}

