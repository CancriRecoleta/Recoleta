# Recoleta

A pure-Java-17 memory-reduction toolkit for **Minecraft 1.20.1 Forge**.

The mod ports the *spirit* of two JDK 25 JEPs into the running game
without requiring a JDK 25 runtime:

| JEP | Native scope (JDK 25) | Recoleta port (JDK 17) | Package |
|---|---|---|---|
| 519 - Compact Object Headers   | Shrinks every object header from 12 B to 8 B | Encodes the dominant Minecraft value classes (`BlockPos`, `ChunkPos`, `SectionPos`, `AABB`) into a single primitive `long` — the per-instance footprint drops from 24-56 B to **8 B** | `memory.header` |
| 521 - Generational Shenandoah  | Adds young/old generations to a low-pause concurrent GC | Generational object pool, bounded per-tick `ReferenceQueue` drainer and heap-pressure watcher that mimic the same low-pause / locality contract inside the mod loop | `memory.gc` |

## Additional reduction paths

* `memory.cache.SoftLruCache` &mdash; bounded LRU + soft-reference values
* `memory.cache.WeakInternTable` &mdash; per-mod weak intern table
* `memory.pool.MutableBlockPosPool` &mdash; thread-local generational pool of `MutableBlockPos`
* `memory.pool.Vec3Pool` &mdash; mutable three-double slots for inner-loop arithmetic
* `memory.SlackTrimmer` &mdash; trims `ArrayList`/`StringBuilder` slack on idle ticks
* `memory.MemoryEvents` &mdash; subscribes to JVM old-gen pool notifications and triggers eviction
* `mixin.client.ParticleEvictionLimitMixin` &mdash; caps the vanilla 16384 per-render-style particle queue

## Configuration

All toggles live in `config/recoleta-memory.toml` (Forge config). Edit at
runtime; values are re-read on every access.

## Optional JDK 25 runtime tuning

If you *do* run on JDK 25, see `JVM_ARGS.md` for the canonical flag set
that activates the native JEP 519 + 521 implementations. Recoleta does
not require these flags — its userland equivalents work on JDK 17.

