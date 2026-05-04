package com.github.recoleta.fabric.config;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.RecoletaBootstrap;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Fabric has no built-in config-spec system; this bridge stores the
 * 20 settings as a flat {@code .properties} file under
 * {@code config/recoleta-memory.properties}. The file is created with
 * defaults on first launch; subsequent edits are read on every load.
 */
public final class FabricConfigBridge {

    private static final String FILE_NAME = "recoleta-memory.properties";

    private FabricConfigBridge() {
        /* utility class - never instantiated */
    }

    public static void load() {
        final Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        final Properties props = new Properties();

        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (final IOException ex) {
                RecoletaBootstrap.LOG.warn("Failed reading {}; using built-in defaults", path, ex);
            }
        }

        MemoryConfig.particlePerTypeCap = parseInt(props, "particlePerTypeCap", MemoryConfig.particlePerTypeCap);
        MemoryConfig.enableSlackTrimmer = parseBoolean(props, "enableSlackTrimmer", MemoryConfig.enableSlackTrimmer);
        MemoryConfig.enablePressureEviction = parseBoolean(props, "enablePressureEviction", MemoryConfig.enablePressureEviction);
        MemoryConfig.pressureRatio = parseDouble(props, "pressureRatio", MemoryConfig.pressureRatio);
        MemoryConfig.referenceDrainBudget = parseInt(props, "referenceDrainBudgetPerTick", MemoryConfig.referenceDrainBudget);
        MemoryConfig.youngPoolCapacity = parseInt(props, "youngPoolCapacity", MemoryConfig.youngPoolCapacity);
        MemoryConfig.oldPoolCapacity = parseInt(props, "oldPoolCapacity", MemoryConfig.oldPoolCapacity);
        MemoryConfig.enablePackedAabbPathCache = parseBoolean(props, "enablePackedAabbPathCache", MemoryConfig.enablePackedAabbPathCache);
        MemoryConfig.enableSpawnerDistanceAllocationPatch = parseBoolean(props, "enableSpawnerDistanceAllocationPatch", MemoryConfig.enableSpawnerDistanceAllocationPatch);
        MemoryConfig.enableCompoundTagSmallMaps = parseBoolean(props, "enableCompoundTagSmallMaps", MemoryConfig.enableCompoundTagSmallMaps);
        MemoryConfig.enableChunkPacketRightSize = parseBoolean(props, "enableChunkPacketRightSize", MemoryConfig.enableChunkPacketRightSize);
        MemoryConfig.enableResourceLocationToStringCache = parseBoolean(props, "enableResourceLocationToStringCache", MemoryConfig.enableResourceLocationToStringCache);
        MemoryConfig.resourceLocationToStringCacheSize = parseInt(props, "resourceLocationToStringCacheSize", MemoryConfig.resourceLocationToStringCacheSize);
        MemoryConfig.enableLiteralContentsCache = parseBoolean(props, "enableLiteralContentsCache", MemoryConfig.enableLiteralContentsCache);
        MemoryConfig.literalContentsCacheSize = parseInt(props, "literalContentsCacheSize", MemoryConfig.literalContentsCacheSize);
        MemoryConfig.enableReloadListenerRightSize = parseBoolean(props, "enableReloadListenerRightSize", MemoryConfig.enableReloadListenerRightSize);
        MemoryConfig.reloadListenerStagingCapacity = parseInt(props, "reloadListenerStagingCapacity", MemoryConfig.reloadListenerStagingCapacity);
        MemoryConfig.modelBakeryStagingCapacity = parseInt(props, "modelBakeryStagingCapacity", MemoryConfig.modelBakeryStagingCapacity);
        MemoryConfig.enableStyleIntern = parseBoolean(props, "enableStyleIntern", MemoryConfig.enableStyleIntern);
        // Fabric has no capabilities at all
        MemoryConfig.enableCapabilityFastCompare = false;

        if (!Files.exists(path)) {
            writeDefaults(path);
        }
    }

    private static void writeDefaults(final Path path) {
        final Properties out = new Properties();
        out.setProperty("particlePerTypeCap", String.valueOf(MemoryConfig.particlePerTypeCap));
        out.setProperty("enableSlackTrimmer", String.valueOf(MemoryConfig.enableSlackTrimmer));
        out.setProperty("enablePressureEviction", String.valueOf(MemoryConfig.enablePressureEviction));
        out.setProperty("pressureRatio", String.valueOf(MemoryConfig.pressureRatio));
        out.setProperty("referenceDrainBudgetPerTick", String.valueOf(MemoryConfig.referenceDrainBudget));
        out.setProperty("youngPoolCapacity", String.valueOf(MemoryConfig.youngPoolCapacity));
        out.setProperty("oldPoolCapacity", String.valueOf(MemoryConfig.oldPoolCapacity));
        out.setProperty("enablePackedAabbPathCache", String.valueOf(MemoryConfig.enablePackedAabbPathCache));
        out.setProperty("enableSpawnerDistanceAllocationPatch", String.valueOf(MemoryConfig.enableSpawnerDistanceAllocationPatch));
        out.setProperty("enableCompoundTagSmallMaps", String.valueOf(MemoryConfig.enableCompoundTagSmallMaps));
        out.setProperty("enableChunkPacketRightSize", String.valueOf(MemoryConfig.enableChunkPacketRightSize));
        out.setProperty("enableResourceLocationToStringCache", String.valueOf(MemoryConfig.enableResourceLocationToStringCache));
        out.setProperty("resourceLocationToStringCacheSize", String.valueOf(MemoryConfig.resourceLocationToStringCacheSize));
        out.setProperty("enableLiteralContentsCache", String.valueOf(MemoryConfig.enableLiteralContentsCache));
        out.setProperty("literalContentsCacheSize", String.valueOf(MemoryConfig.literalContentsCacheSize));
        out.setProperty("enableReloadListenerRightSize", String.valueOf(MemoryConfig.enableReloadListenerRightSize));
        out.setProperty("reloadListenerStagingCapacity", String.valueOf(MemoryConfig.reloadListenerStagingCapacity));
        out.setProperty("modelBakeryStagingCapacity", String.valueOf(MemoryConfig.modelBakeryStagingCapacity));
        out.setProperty("enableStyleIntern", String.valueOf(MemoryConfig.enableStyleIntern));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream s = Files.newOutputStream(path)) {
                out.store(s, "Recoleta - memory reduction settings");
            }
        } catch (final IOException ex) {
            RecoletaBootstrap.LOG.warn("Failed writing default config to {}", path, ex);
        }
    }

    private static int parseInt(final Properties p, final String key, final int fallback) {
        final String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (final NumberFormatException ignored) { return fallback; }
    }

    private static double parseDouble(final Properties p, final String key, final double fallback) {
        final String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (final NumberFormatException ignored) { return fallback; }
    }

    private static boolean parseBoolean(final Properties p, final String key, final boolean fallback) {
        final String v = p.getProperty(key);
        if (v == null) return fallback;
        final String t = v.trim().toLowerCase();
        if ("true".equals(t) || "1".equals(t)) return true;
        if ("false".equals(t) || "0".equals(t)) return false;
        return fallback;
    }
}
