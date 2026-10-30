package dev.polaris_light.constructionwand.basics;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigClient
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHIFTOPT_MODE;
    public static final ModConfigSpec.BooleanValue LIGHT_THEME;
    public static final ModConfigSpec.IntValue LAST_TAB;

    static {
        BUILDER.comment("This is the Client config for ConstructionWand.",
                "If you're not familiar with Forge's new split client/server config, let me explain:",
                "Client config is stored in the /config folder and only contains client specific settings like graphics and keybinds.",
                "Mod behavior is configured in the Server config, which is world-specific and thus located",
                "in the /saves/myworld/serverconfig folder. If you want to change the serverconfig for all",
                "new worlds, copy the config files in the /defaultconfigs folder.");

        BUILDER.push("keys");
        BUILDER.comment("Press SNEAK+OPTKEY instead of OPTKEY for changing wand mode/direction lock");
        SHIFTOPT_MODE = BUILDER.define("ShiftOpt", false);
        BUILDER.comment("Press SNEAK+OPTKEY instead of OPTKEY for opening wand GUI");
        BUILDER.pop();

        BUILDER.push("screen");
        BUILDER.comment("Draw the wand screen with the light theme instead of the dark one.",
                "The button in the lower right corner of the screen switches it and writes this value.");
        LIGHT_THEME = BUILDER.define("LightTheme", false);
        BUILDER.comment("The tab the wand screen was last left on, so it opens where it was.");
        LAST_TAB = BUILDER.defineInRange("LastTab", 0, 0, 3);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
