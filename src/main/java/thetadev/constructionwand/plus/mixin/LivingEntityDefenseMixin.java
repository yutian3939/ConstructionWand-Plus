package thetadev.constructionwand.plus.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thetadev.constructionwand.plus.DefenseEvents;

/**
 * The defense upgrade's protection, applied inside the entity instead of at the events.
 * <p>
 * Events only cover damage that walks the normal path. A mod that widens the access of the entity
 * methods (an access transformer, as the sword of the cosmos does) or simply calls them can hand
 * damage, health and death straight to the entity, and nothing would see it. The methods themselves
 * are the only place every caller has to pass through, so the protection is injected into their head
 * and refuses the call there.
 * <p>
 * The injected methods are:
 * <ul>
 * <li>{@code hurt} and {@code actuallyHurt} for damage,</li>
 * <li>{@code setHealth} for health that is written directly, only when it would be lowered,</li>
 * <li>{@code die} for death,</li>
 * <li>{@code dropAllDeathLoot} for the inventory, which a kill carried out by hand still rolls even
 * when the death itself was refused.</li>
 * </ul>
 * {@code remove} is deliberately left to the entity mixin, which refuses only its kill reason.
 * <p>
 * The targets are written as the names the game itself runs on rather than as readable names that are
 * looked up in a refmap. That lookup turned out to be unreliable in a pack with other mods rewriting
 * the same entities: in one and the same config one target resolved and the next did not, and a
 * target that does not resolve is a crash by default. The names below are read from the mapping the
 * toolchain produces for this exact game version, and the same names can be seen in the log where the
 * game lists the methods it merged into. A development run, where the methods carry their readable
 * names, therefore does not get these guards; {@code require = 0} keeps that from crashing and the
 * packaged game, which is what ships, has all of them.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDefenseMixin
{
    @Inject(method = "m_6469_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) {
            callback.setReturnValue(false);
            callback.cancel();
        }
    }

    @Inject(method = "m_6475_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$actuallyHurt(DamageSource source, float amount, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "m_21153_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$setHealth(float health, CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Only a loss is refused, so healing, the protection's own restore and every other mod's
        // healing keep working.
        if(health < self.getHealth() && DefenseEvents.blocksDamage(self)) callback.cancel();
    }

    @Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$die(DamageSource source, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "m_6668_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$dropAllDeathLoot(DamageSource source, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "m_147240_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$knockback(double strength, double xRatio, double zRatio, CallbackInfo callback) {
        // The knockback event only covers the knockback that follows a hit. A boss that throws whoever
        // stands next to it calls this method on its own, so it is refused here as well.
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }
}
