# Recoleta

A pure-Java-17 memory-reduction toolkit for **Minecraft 1.19.2 Forge**.

The mod ports the *spirit* of two JDK 25 JEPs into the running game
without requiring a JDK 25 runtime:

| JEP | Native scope (JDK 25) | Recoleta port (JDK 17) | Domain |
|---|---|---|---|
| 519 - Compact Object Headers   | Shrinks every object header from 12 B to 8 B | Encodes dominant Minecraft value data into compact primitive forms so typical per-instance footprint drops from 24-56 B to **8 B** | Header/value packing |
| 521 - Generational Shenandoah  | Adds young/old generations to a low-pause concurrent GC | Uses generational reuse pools, bounded per-tick reference draining, and heap-pressure callbacks to mimic low-pause locality inside the game loop | Pooling and pressure-aware cleanup |

## Additional reduction paths

* Bounded LRU with soft-reference values for cache-like workloads
* Weak interning for repetitive strings to reduce duplicate heap copies
* Thread-local reusable position/vector carriers for hot loops
* Idle-time slack trimming for long-lived expandable buffers
* Heap-pressure notifications wired to proactive eviction/reclaim callbacks
* Per-render-style particle cap (below vanilla 16384) to reduce client memory spikes
* Path collision caching now uses compact packed keys to reduce heavy key churn.
* Path stepping reuses mutable vector slots to avoid transient vector allocations.
* Spawn-distance checks use scalar math to avoid temporary geometry objects.
* Hot spawn-position loops reuse mutable coordinate carriers instead of repeatedly allocating.
* Small-NBT paths right-size backing maps for common tiny payloads.
* NBT-load paths repack freshly-decoded compounds so loaded tags carry no slack.
* Empty list-tag containers start with a small backing array sized for typical few-entry payloads (enchantments, lore, modifier lists).
* Capability comparison short-circuits per-entry to avoid building two full tag snapshots on every item-stack equality check.
* Chunk streaming packets start their block-entity list at a small capacity instead of the default 10-slot one.

Each reduction reports its hit count via the `/recoleta memory status`
command so operators can verify a tuning is actually active.

## Configuration

All toggles live in `config/recoleta-memory.toml` (Forge config). Edit at
runtime; values are re-read on every access.

## Optional JDK 25 runtime tuning

If you *do* run on JDK 25, see `JVM_ARGS.md` for the canonical flag set
that activates the native JEP 519 + 521 implementations. Recoleta does
not require these flags — its userland equivalents work on JDK 17.

