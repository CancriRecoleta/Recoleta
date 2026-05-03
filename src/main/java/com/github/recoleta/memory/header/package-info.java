/**
 * Userland port of <b>JEP 519 - Compact Object Headers</b>.
 *
 * <p>JEP 519 shrinks every Java object header from 12 to 8 bytes,
 * yielding roughly 8% live-set reduction in the typical Minecraft heap.
 * It is a JVM feature and only available on JDK 25+.</p>
 *
 * <p>This package achieves the <i>same</i> spirit on JDK 17 by going
 * further: instead of compacting headers, it <i>removes</i> them
 * entirely for the value-like classes that dominate Minecraft's
 * allocation profile. Each helper class encodes the relevant fields
 * into a single primitive {@code long} that can be stored in a
 * {@code long}-keyed fastutil collection or a {@code long[]} without
 * ever materialising an Object.</p>
 *
 * <table>
 *   <caption>Per-instance footprint comparison (8-byte alignment)</caption>
 *   <tr><th>Form</th><th>Header</th><th>Payload</th><th>Total</th></tr>
 *   <tr><td>Vanilla {@code BlockPos}</td><td>12 B</td><td>3*4 B</td><td>24 B</td></tr>
 *   <tr><td>JEP 519 {@code BlockPos}</td><td>8 B</td><td>3*4 B</td><td>24 B*</td></tr>
 *   <tr><td>Recoleta {@link com.github.recoleta.memory.header.PackedBlockPos}</td><td>0 B</td><td>8 B</td><td>8 B</td></tr>
 * </table>
 *
 * <p>* Padded to alignment, so JEP 519 only saves one slot when an
 * object's payload happens to fit in 24 bytes including the new
 * 8-byte header. The packed-long form always saves a header.</p>
 *
 * <p>Encodings preserved here:</p>
 * <ul>
 *   <li>{@link com.github.recoleta.memory.header.PackedBlockPos}   - x/y/z block coordinates (vanilla-compatible bit layout)</li>
 *   <li>{@link com.github.recoleta.memory.header.PackedChunkPos}   - x/z chunk coordinates</li>
 *   <li>{@link com.github.recoleta.memory.header.PackedSectionPos} - x/y/z section (16<sup>3</sup>) coordinates</li>
 *   <li>{@link com.github.recoleta.memory.header.PackedAabb}       - quantised axis-aligned bounding box</li>
 *   <li>{@link com.github.recoleta.memory.header.PackedLongSet}    - storage-friendly set of packed values</li>
 * </ul>
 */
package com.github.recoleta.memory.header;

