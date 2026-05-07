package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.util.INBTSerializable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;

/**
 * Avoids building two full {@code CompoundTag} snapshots when comparing
 * capability dispatchers in {@code ItemStack#areCapsCompatible}. The
 * vanilla implementation calls {@code serializeNBT().equals(other.serializeNBT())},
 * which allocates two whole {@code CompoundTag}s plus their backing
 * {@code HashMap}s and child tags on every compare.
 *
 * <p>This mixin walks the writer arrays in lock-step and compares each
 * entry's serialized form individually, returning {@code false} as
 * soon as a mismatch is detected. The early-exit avoids the bulk of
 * the temporary allocations whenever stacks differ on any cap.</p>
 *
 * <p>When two stacks are fully equal the total work is identical to
 * vanilla (each {@code serializeNBT} runs once per side), but the
 * intermediate {@code CompoundTag} containers are never built, so
 * young-gen pressure still drops noticeably.</p>
 */
@Mixin(value = CapabilityDispatcher.class, remap = false)
public abstract class CapabilityDispatcherFastCompareMixin {


    @Shadow @Final private INBTSerializable<Tag>[] writers;
    @Shadow @Final private String[] names;

    /**
     * @author recoleta
     * @reason Compare capability writers entry-by-entry to avoid building
     *         two full CompoundTag snapshots on every ItemStack equality
     *         check.
     */
    @Overwrite
    public boolean areCompatible(final CapabilityDispatcher other) {
        if (other == null) {
            return this.writers.length == 0;
        }
        // Identity short-circuit (mixin self-cast for compiler).
        if ((Object) this == other) {
            return true;
        }

        final INBTSerializable<Tag>[] a = this.writers;
        final INBTSerializable<Tag>[] b = ((CapabilityDispatcherFastCompareMixin) (Object) other).writers;
        if (a.length != b.length) {
            return false;
        }
        if (a.length == 0) {
            return true;
        }

        if (!MemoryConfig.cachedCapabilityFastCompare()) {
            // Faithful vanilla path.
            return ((CapabilityDispatcher) (Object) this).serializeNBT()
                    .equals(other.serializeNBT());
        }

        final String[] aNames = this.names;
        final String[] bNames = ((CapabilityDispatcherFastCompareMixin) (Object) other).names;

        for (int i = 0; i < a.length; i++) {
            // Names are derived from registries; reference inequality with
            // value equality is rare, but we compare safely just in case
            // a mod constructs equivalent dispatchers in different orders.
            if (!Objects.equals(aNames[i], bNames[i])) {
                RecoletaCounters.CAP_FAST_COMPARE_SHORT_CIRCUIT.increment();
                return false;
            }
            final Tag ta = a[i].serializeNBT();
            final Tag tb = b[i].serializeNBT();
            if (!Objects.equals(ta, tb)) {
                RecoletaCounters.CAP_FAST_COMPARE_SHORT_CIRCUIT.increment();
                return false;
            }
        }
        return true;
    }
}

