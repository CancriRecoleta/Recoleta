package com.github.recoleta.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;

/**
 * Right-sizes the per-chunk {@link HashSet} that
 * {@link ModelDataManager#requestRefresh} creates inside
 * {@code Collections.synchronizedSet(new HashSet<>())}.
 *
 * <p>Vanilla Forge's {@code requestRefresh} does:</p>
 *
 * <pre>
 *   needModelDataRefresh.computeIfAbsent(
 *       new ChunkPos(blockEntity.getBlockPos()),
 *       $ -&gt; Collections.synchronizedSet(new HashSet&lt;&gt;()))
 *       .add(blockEntity.getBlockPos());
 * </pre>
 *
 * <p>The default 16-slot {@link HashSet} is allocated per chunk on
 * first refresh, but most chunks hold zero or one block-entity that
 * actually requests model-data refreshes. With render-distance 12 and
 * mod-heavy packs (mechanical mods, pipes, cabling) the
 * {@code needModelDataRefresh} map can pin hundreds of these
 * 16-bucket {@link HashSet}s of which the vast majority hold a single
 * entry. Pre-sizing to two slots cuts the per-chunk overhead roughly
 * in half without forcing an early resize for the typical workload.</p>
 *
 * <p>Client-only mixin: {@link ModelDataManager} is constructed only on
 * the logical client; the {@code Dist.CLIENT} guard plus the
 * {@code client} block in {@code recoleta.mixins.json} prevent any
 * server-side load attempt.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = ModelDataManager.class, remap = false)
public abstract class ModelDataManagerSmallSetsMixin {

    /**
     * Catches the {@code new HashSet<>()} inside the
     * {@code computeIfAbsent} lambda and supplies a 2-slot variant.
     *
     * <p>The {@code method = "*"} wildcard targets the synthetic
     * lambda body that {@code javac} emits for the
     * {@code computeIfAbsent} argument; targeting it by exact synthetic
     * name would break across Forge minor releases.</p>
     *
     * @return a small {@link HashSet}
     */
    @Redirect(
            method = "*",
            at = @At(value = "NEW", target = "java/util/HashSet"),
            require = 0, expect = 1
    )
    private static HashSet<BlockPos> recoleta$smallRefreshSet() {
        return new HashSet<>(2);
    }
}
