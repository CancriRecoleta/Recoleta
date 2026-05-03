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

    private RecoletaInterns() {
        /* singleton holder - never instantiated */
    }
}

