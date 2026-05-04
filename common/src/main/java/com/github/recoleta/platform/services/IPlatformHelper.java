package com.github.recoleta.platform.services;

import java.nio.file.Path;

/**
 * Per-loader platform glue. Each loader module supplies a single
 * implementation registered via {@code META-INF/services}.
 */
public interface IPlatformHelper {

    /** "Forge", "NeoForge" or "Fabric". */
    String getPlatformName();

    /** Whether the named mod is loaded on the active platform. */
    boolean isModLoaded(String modId);

    /** Whether we are running in a development environment. */
    boolean isDevelopmentEnvironment();

    /** Absolute path to the loader-specific config directory. */
    Path getConfigDir();
}
