package thetadev.constructionwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thetadev.constructionwand.ConstructionWand;

/**
 * Client key bindings of the mod. They show up in the vanilla controls screen, so the player can
 * rebind them like any other key.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModKeys
{
    public static final String CATEGORY = "key.categories." + ConstructionWand.MODID;

    /**
     * Performs the undo while the undo combination (sneak and the opt key) is held.
     */
    public static final KeyMapping UNDO = new KeyMapping("key." + ConstructionWand.MODID + ".undo",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private ModKeys() {
    }

    @SubscribeEvent
    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(UNDO);
    }
}
