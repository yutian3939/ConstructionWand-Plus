package dev.polaris_light.constructionwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.network.ModMessages;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;
import dev.polaris_light.constructionwand.plus.basics.TimeTargets;
import dev.polaris_light.constructionwand.plus.network.PacketTimeAccelerate;

import java.util.List;

/**
 * Holding ctrl with the time core: the blocks that are attached to the one being pointed at are outlined,
 * and a click accelerates all of them instead of just that one.
 * <p>
 * The key is read on the client and turned into a request, because the server never sees ctrl. The set is
 * walked again on the server, so a client cannot ask for more blocks than the configuration allows.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT)
public final class TimeConnectedEvents
{
    /** How far the outline is drawn: a bright amber that matches the time core's own colour. */
    private static final float[] OUTLINE = {1.0F, 0.78F, 0.2F, 0.85F};

    private TimeConnectedEvents() {
    }

    /**
     * @return whether ctrl is held, which turns the click into "all connected blocks"
     */
    public static boolean controlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /**
     * @return whether this click belongs to the connected mode: ctrl held, shift not held, the time core in
     * hand. Shift is what opens the wand screen, so a player who wants to accelerate never has the screen
     * pop up instead.
     */
    public static boolean takesOverUse(ItemStack stack) {
        if(!controlDown() || net.minecraft.client.gui.screens.Screen.hasShiftDown()) return false;
        return ItemWand.isTimeCore(stack);
    }

    /**
     * The blocks the outline is drawn around, or an empty list when ctrl is not held, the wand does not
     * carry the time core, or the player is not looking at a block.
     */
    private static List<BlockPos> highlighted() {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player == null || minecraft.level == null) return List.of();
        if(!controlDown()) return List.of();
        if(minecraft.screen != null) return List.of();
        if(!(minecraft.hitResult instanceof BlockHitResult hit)) return List.of();
        if(!ItemWand.isTimeCore(minecraft.player.getMainHandItem())) return List.of();

        // A machine is one body already: ctrl has nothing to add to it, and the outline would otherwise cover
        // the casings of every machine that happens to be built from the same block.
        if(!TimeTargets.of(minecraft.level, hit.getBlockPos()).tick().equals(hit.getBlockPos())) return List.of();

        List<BlockPos> blocks = TimeAcceleration.connected(minecraft.level, hit.getBlockPos(),
                Math.max(1, ConfigServer.TIME_CORE_CONNECTED_LIMIT.get()));
        return blocks.size() > 1 ? blocks : List.of();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<BlockPos> blocks = highlighted();
        if(blocks.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for(BlockPos pos : blocks) {
            AABB box = new AABB(pos).inflate(0.002D);
            LevelRenderer.renderLineBox(poseStack, lines, box, OUTLINE[0], OUTLINE[1], OUTLINE[2], OUTLINE[3]);
        }

        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    /**
     * Takes the click away from the block and asks the server for the whole body instead.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getEntity().level().isClientSide()) return;
        ItemStack wand = event.getEntity().getItemInHand(event.getHand());
        if(!takesOverUse(wand)) return;

        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ModMessages.sendToServer(new PacketTimeAccelerate(event.getPos(), true));
    }
}