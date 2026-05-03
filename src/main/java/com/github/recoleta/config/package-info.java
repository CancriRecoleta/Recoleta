/**
 * User-facing configuration for every Recoleta optimisation.
 *
 * <p>All toggles are persisted in a single TOML file
 * ({@code config/recoleta-memory.toml}). Each subsystem reads the values
 * lazily so that disabling a feature at runtime is honoured without a
 * JVM restart.</p>
 */
package com.github.recoleta.config;

