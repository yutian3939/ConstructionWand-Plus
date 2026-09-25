package com.xinyihl.constructionwandlegacy.coremod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

@IFMLLoadingPlugin.Name("ConstructionWandLegacy Lighting Coremod")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("com.xinyihl.constructionwandlegacy.coremod")
public final class LightingCorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"com.xinyihl.constructionwandlegacy.coremod.ChunkLightingTransformer"};
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object minecraftLocation = data.get("mcLocation");
        boolean enabled = minecraftLocation instanceof File && readEnabled((File) minecraftLocation);
        ChunkLightingTransformer.setEnabled(enabled);
        FMLLog.log.info("ConstructionWandLegacy deferred lighting Coremod is {}", enabled ? "enabled" : "disabled; Chunk will not be modified");
    }

    private static boolean readEnabled(File minecraftDirectory) {
        File configFile = new File(new File(minecraftDirectory, "config"), "ConstructionWandLegacy.cfg");
        if (!configFile.isFile()) {
            return false;
        }
        try {
            Configuration configuration = new Configuration(configFile);
            configuration.load();
            return configuration.getBoolean(
                    "deferredLightingUpdates",
                    "general.performance",
                    false,
                    "Defer and coalesce Chunk lighting updates during wand execution"
            );
        } catch (RuntimeException exception) {
            FMLLog.log.error("Unable to read ConstructionWandLegacy Coremod setting; leaving Chunk unchanged", exception);
            return false;
        }
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
