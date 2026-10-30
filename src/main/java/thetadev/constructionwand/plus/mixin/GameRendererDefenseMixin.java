package thetadev.constructionwand.plus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thetadev.constructionwand.plus.DefenseEvents;

/**
 * The camera shake of a hit, for a player who is not being hurt.
 * <p>
 * The tilt is drawn by the game renderer from the hurt state of the local player, and an attack that
 * was refused still leaves that state behind (the hurt animation travels as its own packet), so the
 * view is knocked about although no damage was taken. This is the last point the two are still joined,
 * so it is where they are taken apart.
 * <p>
 * Client only, the server has no renderer; the config therefore lists this mixin under "client".
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererDefenseMixin
{
    @Inject(method = "m_109117_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void constructionwand$bobHurt(PoseStack poseStack, float partialTick, CallbackInfo callback) {
        if(DefenseEvents.blocksDamage(Minecraft.getInstance().player)) callback.cancel();
    }
}
