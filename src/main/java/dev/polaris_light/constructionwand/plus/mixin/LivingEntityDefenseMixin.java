package dev.polaris_light.constructionwand.plus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.polaris_light.constructionwand.plus.DefenseEvents;

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
 * NeoForge runs the game on Mojang's official names, so the names below are the readable ones and the
 * mixin never remaps. Development and the packaged game therefore see exactly the same methods.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDefenseMixin
{
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) {
            callback.setReturnValue(false);
            callback.cancel();
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$actuallyHurt(DamageSource source, float amount, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$setHealth(float health, CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Only a loss is refused, so healing, the protection's own restore and every other mod's
        // healing keep working.
        if(health < self.getHealth() && DefenseEvents.blocksDamage(self)) callback.cancel();
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$die(DamageSource source, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$dropAllDeathLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true, remap = false)
    private void constructionwand$knockback(double strength, double xRatio, double zRatio, CallbackInfo callback) {
        // The knockback event only covers the knockback that follows a hit. A boss that throws whoever
        // stands next to it calls this method on its own, so it is refused here as well.
        if(DefenseEvents.blocksDamage((LivingEntity) (Object) this)) callback.cancel();
    }
}
