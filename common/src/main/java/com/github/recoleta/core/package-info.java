/**
 * Lifecycle and configuration wiring for Recoleta.
 *
 * <p>Holds the {@code @Mod}-bound bootstrap, the Forge lifecycle
 * listeners, and the central {@code MemoryConfig} loader. No memory
 * optimisation logic lives here; this package is a thin adapter
 * between Forge events and the {@code com.github.recoleta.memory}
 * subsystems.</p>
 */
package com.github.recoleta.core;

