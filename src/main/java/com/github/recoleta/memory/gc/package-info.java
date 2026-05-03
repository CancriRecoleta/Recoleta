/**
 * Userland port of <b>JEP 521 - Generational Shenandoah</b>.
 *
 * <p>JEP 521 promotes Shenandoah to a generational, low-pause concurrent
 * collector available only on JDK 25+. This package mirrors the same
 * three ideas in pure Java 17 code that runs inside the mod:</p>
 *
 * <ul>
 *   <li><b>Generational pooling</b> &mdash;
 *       {@link com.github.recoleta.memory.gc.GenerationalPool} keeps a
 *       small <i>young</i> tier sized for hot allocations and a larger
 *       <i>old</i> tier for survivors, mimicking the way generational
 *       collectors split live data by age.</li>
 *   <li><b>Bounded incremental work</b> &mdash;
 *       {@link com.github.recoleta.memory.gc.IncrementalCleaner} drains
 *       at most <i>N</i> entries per game tick from any registered
 *       {@link java.lang.ref.ReferenceQueue}, capping the per-tick pause
 *       and approximating Shenandoah's concurrent-marking budget.</li>
 *   <li><b>Heap-pressure feedback</b> &mdash;
 *       {@link com.github.recoleta.memory.gc.LowPauseScheduler} schedules
 *       slack-trimming and cache eviction only when the JVM signals it
 *       is under memory pressure, avoiding work during steady state.</li>
 * </ul>
 */
package com.github.recoleta.memory.gc;

