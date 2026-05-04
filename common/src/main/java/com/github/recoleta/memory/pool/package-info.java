/**
 * Thread-local pools of mutable Minecraft value classes.
 *
 * <p>Used by hot inner loops (entity ticks, particle updates, render
 * passes) to avoid allocating a fresh wrapper every tick. All pools
 * here live on top of
 * {@link com.github.recoleta.memory.gc.GenerationalPool}.</p>
 */
package com.github.recoleta.memory.pool;

