package com.github.recoleta.neoforge.mixin.common;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-sizes the {@code itemsById} {@link Int2ObjectOpenHashMap} on
 * every {@link SynchedEntityData}.
 *
 * <p>Vanilla initialises it with the default fastutil capacity of 16
 * even though the {@code MAX_ID_VALUE} of 254 is essentially never
 * approached: typical entities define 5-15 sync slots, with anything
 * above 32 being unusual even with mods. With 1000 active entities
 * the default-capacity table wastes around 200 KB of always-resident
 * heap.</p>
 *
 * <p>Replacement happens at constructor return rather than redirecting
 * the inline {@code new Int2ObjectOpenHashMap<>()} call to keep the
 * mixin independent of bytecode-instruction order.</p>
 */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallItemsById(final CallbackInfo ci) {
        this.itemsById = new Int2ObjectOpenHashMap<>(8);
    }
}
