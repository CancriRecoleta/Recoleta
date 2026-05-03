/**
 * Mixin package - all classes here are loaded by the Sponge mixin
 * processor and must remain Java 17 bytecode-compatible (the
 * compatibility level declared in {@code recoleta.mixins.json}).
 *
 * <p>Recoleta's design rule for mixins: <b>be small, be optional</b>.
 * Every mixin reads its enable-toggle from
 * {@link com.github.recoleta.config.MemoryConfig} so that a user can
 * disable any individual patch without restarting the JVM.</p>
 */
package com.github.recoleta.mixin;

