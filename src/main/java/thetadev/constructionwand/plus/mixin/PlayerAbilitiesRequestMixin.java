package thetadev.constructionwand.plus.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thetadev.constructionwand.plus.FlightEvents;

/**
 * Notes which flight state the player asked for themselves.
 * <p>
 * A flight that is switched off can come from the player, by double tapping to land, or from the fight,
 * which switches it off again on every tick. From the flight flag alone the two are the same thing, and
 * with only that to go on the upgrade could either be flown at all or be stopped, never both.
 * <p>
 * The player's own request arrives here, so it is recorded: the flight is only put back when the player
 * did not ask for it to be off. This is a Minecraft method on a Minecraft class, so it carries its
 * runtime name; a miss is not fatal, the upgrade then keeps its older behaviour.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerAbilitiesRequestMixin
{
    @Inject(method = "m_6828_", at = @At("HEAD"), remap = false, require = 0)
    private void constructionwand$rememberRequest(ServerboundPlayerAbilitiesPacket packet, CallbackInfo callback) {
        FlightEvents.noteRequest(((ServerGamePacketListenerImpl) (Object) this).player, packet.isFlying());
    }
}
