package thetadev.constructionwand.plus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thetadev.constructionwand.plus.DefenseEvents;

/**
 * The two entity level calls the protection has to refuse on top of the health calls.
 * <p>
 * They are what a "fake death" is made of: an attack that cannot get through the health still manages
 * to put the dying pose onto the player, and the client then draws the red tilt and the twitching of
 * a corpse even though nothing actually died. {@code remove(KILLED)} is the other half, it is what
 * makes the client drop the player and show the death screen.
 * <p>
 * Only these two exact cases are refused. {@code remove} with any other reason is how entities are
 * unloaded, moved between dimensions and discarded, and {@code setPose} is used for swimming,
 * crouching, sleeping and everything else, so both keep working normally.
 * <p>
 * The targets are written as the names the game itself runs on, see the note on the living entity
 * mixin for why.
 */
@Mixin(Entity.class)
public abstract class EntityDefenseMixin
{
    @Inject(method = "m_20124_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$setPose(Pose pose, CallbackInfo callback) {
        if(pose == Pose.DYING && DefenseEvents.blocksDamage((Entity) (Object) this)) callback.cancel();
    }

    @Inject(method = "m_142687_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$remove(Entity.RemovalReason reason, CallbackInfo callback) {
        if(reason == Entity.RemovalReason.KILLED && DefenseEvents.blocksDamage((Entity) (Object) this)) {
            callback.cancel();
        }
    }
}
