package com.github.recoleta.mixin.common;

import com.github.recoleta.memory.RecoletaCounters;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-sizes the {@code recipesUsed} tally every furnace, blast furnace
 * and smoker carries.
 *
 * <p>Vanilla initialises {@code recipesUsed} with a no-arg
 * {@link Object2IntOpenHashMap}, whose default backing arrays are sized
 * for 16 entries. The map only records the distinct recipes smelted since
 * the last XP collection, which for the vast majority of furnaces is one
 * or two; the rest of the table is resident slack multiplied by every
 * cooking block entity in the world (storage-tech bases run thousands).</p>
 *
 * <p>The field is replaced at the end of the abstract constructor &mdash;
 * which runs for every subclass &mdash; with a capacity-4 map, mirroring
 * the field-swap approach used by {@code LevelChunkTickersSmallMapMixin}.
 * The map is empty at construction time, so nothing is lost in the swap.</p>
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntitySmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Object2IntOpenHashMap<ResourceLocation> recipesUsed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallRecipesUsed(final CallbackInfo ci) {
        this.recipesUsed = new Object2IntOpenHashMap<>(4);
        RecoletaCounters.FURNACE_RECIPES_SMALL_MAP.increment();
    }
}
