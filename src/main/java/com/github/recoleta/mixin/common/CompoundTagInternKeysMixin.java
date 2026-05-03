package com.github.recoleta.mixin.common;

import com.github.recoleta.memory.cache.RecoletaInterns;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * De-duplicates the {@link String} keys stored in every
 * {@link CompoundTag}.
 *
 * <p>NBT keys are extremely repetitive in Minecraft: every chunk save,
 * every entity sync packet and every item-stack metadata round-trip
 * produces fresh {@code String} instances for the same handful of names
 * ({@code "id"}, {@code "Pos"}, {@code "Health"}, {@code "Tags"}, ...).
 * The default {@code HashMap<String, Tag>} backing a {@code CompoundTag}
 * therefore retains thousands of duplicate strings whose only purpose
 * is to be {@code .equals()} of an already-existing key.</p>
 *
 * <p>This mixin canonicalises the key argument of {@link CompoundTag#put(String, Tag)}
 * via {@link RecoletaInterns#STRINGS} before it is stored. {@code put}
 * is the single common entry point used by NBT deserialisation
 * ({@code CompoundTag.TYPE.load(...)} routes through it), which is
 * where the duplicate-string flood originates &mdash; so a one-method
 * mixin captures essentially the entire saving.</p>
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagInternKeysMixin {

    /**
     * Canonicalises the key before the {@code tags.put(key, value)}
     * call inside {@link CompoundTag#put(String, Tag)}.
     *
     * @param key the original key
     * @return the interned, canonical instance
     */
    @ModifyVariable(
            method = "put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String recoleta$internKey(final String key) {
        return key == null ? null : RecoletaInterns.STRINGS.intern(key);
    }
}

