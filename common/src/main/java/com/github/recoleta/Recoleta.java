package com.github.recoleta;

/**
 * Loader-agnostic constants for the Recoleta mod.
 *
 * <p>The mod entry point lives in the per-loader modules
 * ({@code com.github.recoleta.forge.RecoletaForge},
 * {@code com.github.recoleta.neoforge.RecoletaNeoForge},
 * {@code com.github.recoleta.fabric.RecoletaFabric}); each one delegates
 * one-shot wiring to {@link com.github.recoleta.core.RecoletaBootstrap}.</p>
 */
public final class Recoleta {

    /** Canonical mod identifier; must match the per-loader manifests. */
    public static final String MODID = "recoleta";

    private Recoleta() {
        /* constants only */
    }
}
