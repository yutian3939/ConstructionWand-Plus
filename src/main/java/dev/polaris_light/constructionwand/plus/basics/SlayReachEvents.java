package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.wand.ItemWand;

/**
 * Gives the player a longer arm while a wand with the slay core is in hand.
 * <p>
 * The slay core is aimed at creatures, so the reach of a wand is the reach of an arm: without this a player has
 * to stand close enough to be hit back. The modifier is put on the interaction range of the player and taken off
 * again as soon as the wand is not held, so nothing leaks into the rest of the game.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class SlayReachEvents
{
    /** How much further than a bare hand a slay wand reaches, in blocks. */
    public static final double REACH = 8.0D;

    private static final ResourceLocation ID = ConstructionWand.loc("slay_reach");

    private SlayReachEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if(range == null) return;

        boolean wanted = ItemWand.isSlayCore(player.getMainHandItem());
        AttributeModifier existing = range.getModifier(ID);

        if(wanted && existing == null) {
            range.addTransientModifier(new AttributeModifier(ID, REACH, AttributeModifier.Operation.ADD_VALUE));
        }
        else if(!wanted && existing != null) {
            range.removeModifier(ID);
        }
    }
}