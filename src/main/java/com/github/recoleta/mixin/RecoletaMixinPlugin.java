package com.github.recoleta.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class RecoletaMixinPlugin implements IMixinConfigPlugin {
    private static final String LIST_TAG_SMALL_LIST_MIXIN = "com.github.recoleta.mixin.common.ListTagSmallListMixin";
    private static final Set<String> KUBEJS_UNSAFE_COMPONENT_MIXINS = Set.of(
            "com.github.recoleta.mixin.common.ComponentContentsCacheMixin",
            "com.github.recoleta.mixin.common.MutableComponentStyleInternMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (LIST_TAG_SMALL_LIST_MIXIN.equals(mixinClassName)) {
            return !isC2meLoaded();
        }
        if (KUBEJS_UNSAFE_COMPONENT_MIXINS.contains(mixinClassName)) {
            return !isModLoaded("kubejs");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isC2meLoaded() {
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return false;
        }
        for (ModInfo mod : loadingModList.getMods()) {
            if (mod.getModId().startsWith("c2me")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModLoaded(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return false;
        }
        for (ModInfo mod : loadingModList.getMods()) {
            if (modId.equals(mod.getModId())) {
                return true;
            }
        }
        return false;
    }
}
