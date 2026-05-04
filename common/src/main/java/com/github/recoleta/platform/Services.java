package com.github.recoleta.platform;

import com.github.recoleta.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Cached lookup of loader-supplied service implementations.
 *
 * <p>Each loader module ships a {@code META-INF/services/} entry
 * pointing at its concrete {@link IPlatformHelper} class; this holder
 * resolves it at first access and caches the result.</p>
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
        /* utility class - never instantiated */
    }

    private static <T> T load(final Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        return loaded;
    }
}
