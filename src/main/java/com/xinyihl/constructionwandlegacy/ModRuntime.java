package com.xinyihl.constructionwandlegacy;

import com.xinyihl.constructionwandlegacy.material.MaterialSourceRegistry;
import com.xinyihl.constructionwandlegacy.wand.WandExecutor;
import com.xinyihl.constructionwandlegacy.wand.WandPlanner;
import com.xinyihl.constructionwandlegacy.wand.undo.UndoService;
import org.apache.logging.log4j.Logger;

public final class ModRuntime {
    private static boolean initialized;
    private final MaterialSourceRegistry materialSourceRegistry;
    private final UndoService undoService;
    private final WandPlanner wandPlanner;
    private final WandExecutor wandExecutor;

    public ModRuntime(Logger logger) {
        synchronized (ModRuntime.class) {
            if (initialized) {
                throw new IllegalStateException("ModRuntime may only be initialized once per JVM");
            }
            initialized = true;
        }
        materialSourceRegistry = new MaterialSourceRegistry();
        undoService = new UndoService(logger);
        wandPlanner = new WandPlanner(materialSourceRegistry, logger);
        wandExecutor = new WandExecutor(undoService, logger);
    }

    public MaterialSourceRegistry getMaterialSourceRegistry() {
        return materialSourceRegistry;
    }

    public UndoService getUndoService() {
        return undoService;
    }

    public WandPlanner getWandPlanner() {
        return wandPlanner;
    }

    public WandExecutor getWandExecutor() {
        return wandExecutor;
    }
}
