package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Right-sizes the no-arg CompoundTag map for the common tiny-NBT case.
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Map<String, Tag> tags;

    /** Slack threshold below which the input map is repacked. */
    private static final int RECOLETA$SMALL_LOAD_THRESHOLD = 8;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void recoleta$smallDefaultMap(final CallbackInfo ci) {
        if (MemoryConfig.enableCompoundTagSmallMaps()) {
            this.tags = new HashMap<>(4);
        }
    }

    /**
     * Right-sizes the backing map for the NBT-load path
     * ({@code TagType.load(...)} → {@code new CompoundTag(map)}). The
     * vanilla path passes a default-capacity {@code HashMap} (16 slots)
     * even though most loaded compounds carry only a handful of entries.
     *
     * <p>Replacing the reference with a right-sized copy at construction
     * time is safe because {@code CompoundTag} never re-exposes its
     * internal {@code tags} map. Only triggered when the source map is
     * a small {@code HashMap}, so external pre-built map identities are
     * preserved on the slow path.</p>
     */
    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void recoleta$smallLoadedMap(final Map<String, Tag> source, final CallbackInfo ci) {
        if (!MemoryConfig.enableCompoundTagSmallMaps()) {
            return;
        }
        if (!(source instanceof HashMap<?, ?>)) {
            return;
        }
        final int size = source.size();
        if (size > RECOLETA$SMALL_LOAD_THRESHOLD) {
            return;
        }

        final int initial = size <= 1 ? 2 : (size <= 3 ? 4 : 8);
        final HashMap<String, Tag> packed = new HashMap<>(initial);
        packed.putAll(source);
        this.tags = packed;
        RecoletaCounters.COMPOUND_TAG_LOAD_REPACK.increment();
    }
}

