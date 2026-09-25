package com.xinyihl.constructionwandlegacy.config;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ConfigWarnings {
    private ConfigWarnings() {
    }

    public static void warn(String message) {
        Logger logger = ConstructionWandLegacy.LOGGER;
        if (logger == null) {
            logger = LogManager.getLogger(Tags.MOD_NAME);
        }
        logger.warn(message);
    }
}
