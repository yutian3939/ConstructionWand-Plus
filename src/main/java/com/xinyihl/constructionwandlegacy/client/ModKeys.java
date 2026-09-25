package com.xinyihl.constructionwandlegacy.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.input.Keyboard;

/**
 * Client key bindings of the mod. They show up in the vanilla controls screen, so the player can
 * rebind them like any other key.
 */
public final class ModKeys {
    public static final String CATEGORY = "key.categories.constructionwandlegacy";

    /**
     * Performs the undo while the undo combination (sneak and Ctrl) is held.
     */
    public static final KeyBinding UNDO = new KeyBinding("key.constructionwandlegacy.undo",
            KeyConflictContext.IN_GAME, Keyboard.KEY_Z, CATEGORY);

    private ModKeys() {
    }
}
