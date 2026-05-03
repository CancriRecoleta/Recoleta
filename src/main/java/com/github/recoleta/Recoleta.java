package com.github.recoleta;

import com.github.recoleta.core.ModInit;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point for Recoleta.
 *
 * <p>Recoleta is a Minecraft 1.20.1 Forge mod whose sole goal is to
 * shrink the resident memory footprint and GC pressure of the running
 * JVM <strong>without requiring a JDK 25 runtime</strong>. It targets
 * the spirit of two JEPs and ports them into pure Java 17 userland
 * code that lives inside this mod:</p>
 *
 * <ul>
 *   <li><b>JEP 519 - Compact Object Headers</b> is approximated by the
 *       {@code com.github.recoleta.memory.header} package: short-lived
 *       Minecraft value objects ({@code BlockPos}, {@code ChunkPos},
 *       {@code SectionPos}, {@code AABB}) are encoded into single
 *       {@code long} primitives, eliminating their per-instance object
 *       header entirely. The saving on JDK 17 (12-byte header + 12-byte
 *       payload + 8-byte alignment = 24 bytes) is even larger than
 *       JEP 519 would deliver natively (~8 bytes per object).</li>
 *
 *   <li><b>JEP 521 - Generational Shenandoah</b> is approximated by the
 *       {@code com.github.recoleta.memory.gc} package: a generational
 *       object pool with young/old tiers, an incremental
 *       {@link java.lang.ref.ReferenceQueue} drainer that performs
 *       bounded slices of work per game tick (mimicking Shenandoah's
 *       low-pause concurrent collector), and a heap-pressure watcher
 *       that opportunistically evicts soft caches.</li>
 * </ul>
 *
 * <p>Additional reduction paths live in {@code memory.cache},
 * {@code memory.pool}, {@code memory.SlackTrimmer} and the targeted
 * mixins under {@code com.github.recoleta.mixin}.</p>
 *
 * <p>This class is intentionally trivial; all wiring lives in
 * {@link ModInit#bootstrap(FMLJavaModLoadingContext)}.</p>
 */
@Mod(Recoleta.MODID)
public final class Recoleta {

    /** Canonical mod identifier; must match {@code mods.toml} and the mixin refmap. */
    public static final String MODID = "recoleta";

    /**
     * Constructed by Forge during mod loading. All real work happens in
     * {@link ModInit#bootstrap(FMLJavaModLoadingContext)} so that the
     * {@code @Mod}-annotated class remains side-effect free and easy to audit.
     */
    public Recoleta() {
        ModInit.bootstrap(FMLJavaModLoadingContext.get());
    }
}
