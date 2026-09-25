package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ExperienceIntake {
    @Nullable
    private static EntityPlayer player;
    private static int absorbed;

    private ExperienceIntake() {
    }

    /**
     * Starts collecting the orbs of one dug block, on the server thread.
     */
    public static void begin(EntityPlayer owner) {
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
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (player == null || event.getWorld().isRemote || !(event.getEntity() instanceof EntityXPOrb)) {
            return;
        }
        absorbed += ((EntityXPOrb) event.getEntity()).getXpValue();
        event.setCanceled(true);
    }
}
