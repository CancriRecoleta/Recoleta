# Optional JDK 25 runtime arguments

Recoleta does **not** require a JDK 25 runtime; everything works on
JDK 17. However, if you happen to run on JDK 25 or newer, the flags
below activate the native JVM equivalents of the userland ports
shipped by this mod, stacking the two for maximum effect.

Add them to `user_jvm_args.txt` (Forge launcher) or your client
launcher's "JVM arguments" field:

```
-XX:+UnlockExperimentalVMOptions
-XX:+UseCompactObjectHeaders
-XX:+UseShenandoahGC
-XX:ShenandoahGCMode=generational
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

Notes:

* `UseCompactObjectHeaders` is JEP 519 (stable in JDK 25). The
  `UnlockExperimentalVMOptions` line is harmless and forward-compatible.
* `UseShenandoahGC` + `ShenandoahGCMode=generational` is JEP 521.
  These conflict with `-XX:+UseG1GC`; remove any prior G1 flags.
* `AlwaysPreTouch` removes the lazy paging cost of the heap commit at
  startup; useful for servers with strict tick budgets.
* `DisableExplicitGC` blocks rogue `System.gc()` calls (some mods do
  call it). Recoleta's `IncrementalCleaner` covers reference draining
  itself.

## JDK 17 baseline tuning (always safe)

```
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:+ParallelRefProcEnabled
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=20
-XX:G1ReservePercent=20
-XX:G1HeapRegionSize=32M
```

