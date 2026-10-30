package dev.polaris_light.constructionwand.plus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.polaris_light.constructionwand.plus.FlightEvents;

/**
 * <b>Not registered in {@code constructionwand.mixins.json}.</b> It targets a class of the Goety mod,
 * and a mixin whose target does not exist is a startup crash. Register it there only in a pack that
 * actually ships Goety, and adjust the target and the injected names to that build.
 * <p>
 * Answers "may fly" to the one question that decides it in another mod's falling effect.
 * <p>
 * The revelation fight carries an effect that turns the flight of everyone under it off, and the single
 * line doing so is:
 * <pre>player.getAbilities().flying &amp;= player.isCreative();</pre>
 * That is why creative players are untouched by it. Instead of fighting over the flight flag - which
 * ends up either with the flight switched off again every tick, or with a flight that cannot be stopped
 * any more - the question itself is answered with yes for the players this upgrade flies.
 * <p>
 * Only this one call inside this one method is answered that way. The player's game mode, their
 * inventory, block breaking and everything else stay exactly as they are: {@code isCreative} is a
 * question about a player, and it is only lied to where the answer decides a flight that this upgrade
 * handed out anyway.
 * <p>
 * The target is another mod's class, written as a name so a pack without it only logs a warning. The
 * effect method overrides a Minecraft method and therefore carries its runtime name; the mod's own code
 * keeps its names, which is why only the Minecraft names appear here.
 */
@Mixin(targets = "com.Polarice3.Goety.common.effects.GoetyBaseEffect", remap = false)
public abstract class GoetyFlightGuardMixin
{
    @Redirect(method = "m_6742_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_7500_()Z"),
            require = 0)
    private boolean constructionwand$mayFly(Player player, LivingEntity livingEntity, int amplify) {
        if(FlightEvents.guardsFlight(livingEntity)) return true;
        return player.isCreative();
    }
}
