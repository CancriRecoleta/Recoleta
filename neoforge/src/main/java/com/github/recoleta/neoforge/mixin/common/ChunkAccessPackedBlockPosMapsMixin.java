package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.memory.header.PackedBlockPosMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Stores chunk-local block-entity maps with packed {@link BlockPos} keys.
 *
 * <p>Vanilla exposes these fields as {@code Map<BlockPos, ...>}, so the
 * optimization uses a compatible map facade and swaps it in after the
 * constructor has initialized the final fields.</p>
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessPackedBlockPosMapsMixin {

    @Mutable
    @Final
    @Shadow
    protected Map<BlockPos, CompoundTag> pendingBlockEntities;

    @Mutable
    @Final
    @Shadow
    protected Map<BlockPos, BlockEntity> blockEntities;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$usePackedBlockEntityMaps(final CallbackInfo ci) {
        this.pendingBlockEntities = new PackedBlockPosMap<>(2);
        this.blockEntities = new PackedBlockPosMap<>(2);
    }
}
