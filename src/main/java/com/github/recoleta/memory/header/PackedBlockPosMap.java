package com.github.recoleta.memory.header;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * {@link Map} facade for {@link BlockPos} keys backed by packed long
 * coordinates.
 *
 * <p>The public contract remains {@code Map<BlockPos, V>} for vanilla and
 * Forge compatibility, while the storage key is {@link BlockPos#asLong()}.
 * This removes the retained immutable {@code BlockPos} key objects from
 * sparse chunk maps such as block entities and pending block-entity NBT.</p>
 *
 * @param <V> value type
 */
public final class PackedBlockPosMap<V> extends AbstractMap<BlockPos, V> {

    private final Long2ObjectOpenHashMap<V> backing;

    /**
     * Creates an empty packed map.
     */
    public PackedBlockPosMap() {
        this.backing = new Long2ObjectOpenHashMap<>();
    }

    /**
     * Creates an empty packed map sized for the expected number of entries.
     *
     * @param expected expected entry count
     */
    public PackedBlockPosMap(final int expected) {
        this.backing = new Long2ObjectOpenHashMap<>(expected);
    }

    @Override
    public V put(final BlockPos key, final V value) {
        return backing.put(key.asLong(), value);
    }

    @Override
    public V get(final Object key) {
        return key instanceof BlockPos pos ? backing.get(pos.asLong()) : null;
    }

    @Override
    public V remove(final Object key) {
        return key instanceof BlockPos pos ? backing.remove(pos.asLong()) : null;
    }

    @Override
    public boolean containsKey(final Object key) {
        return key instanceof BlockPos pos && backing.containsKey(pos.asLong());
    }

    @Override
    public boolean containsValue(final Object value) {
        return backing.containsValue(value);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public void putAll(final Map<? extends BlockPos, ? extends V> map) {
        for (final Entry<? extends BlockPos, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<Entry<BlockPos, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<BlockPos, V>> iterator() {
                final ObjectIterator<Long2ObjectMap.Entry<V>> iterator = backing.long2ObjectEntrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<BlockPos, V> next() {
                        final Long2ObjectMap.Entry<V> entry = iterator.next();
                        return new SimpleEntry<>(BlockPos.of(entry.getLongKey()), entry.getValue()) {
                            @Override
                            public V setValue(final V value) {
                                return entry.setValue(value);
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return backing.size();
            }

            @Override
            public void clear() {
                backing.clear();
            }
        };
    }
}
