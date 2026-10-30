package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.polaris_light.constructionwand.ConstructionWand;

import javax.annotation.Nullable;

/**
 * Catches the experience orbs that blocks spawn while the wand digs them.
 * <p>
 * Vanilla reports the experience of a block through {@code getExpDrop}, which the digging core hands
 * to the player directly. Modded blocks often spawn orbs from their own break hooks instead, which
 * the wand cannot know about, so while one block is being dug the orbs that appear are turned into
 * experience for the digging player instead of reaching the world.
 * <p>
 * The window is a single, synchronous block removal on the server thread, so an orb that shows up in
 * it belongs to the block that is being dug.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class ExperienceIntake
{
    @Nullable
    private static Player player;
    private static int absorbed;

    private ExperienceIntake() {
    }

    /**
     * Starts collecting the orbs of one dug block, on the server thread.
     */
    public static void begin(Player owner) {
        player = owner;
        absorbed = 0;
    }

    /**
     * @return the experience collected since {@link #begin}, to be taken over by the caller
     */
    public static int end() {
        int collected = absorbed;
        player = null;
        absorbed = 0;
        return collected;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if(player == null || event.getLevel().isClientSide || !(event.getEntity() instanceof ExperienceOrb orb)) return;
        absorbed += orb.getValue();
        event.setCanceled(true);
    }
}
