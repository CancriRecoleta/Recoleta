/**
 * Top-level helpers that schedule memory work against the Forge event
 * bus, plus the heap-pressure watcher.
 *
 * <p>{@link com.github.recoleta.memory.SlackTrimmer} reclaims the
 * trailing capacity of long-lived collections during low-tick periods,
 * and {@link com.github.recoleta.memory.MemoryEvents} polls the JVM
 * memory beans to fire callbacks when occupancy crosses the
 * configured threshold.</p>
 */
package com.github.recoleta.memory;

