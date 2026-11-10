package dev.polaris_light.constructionwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.network.ModMessages;
import dev.polaris_light.constructionwand.network.PacketRequestPreview;
import dev.polaris_light.constructionwand.plus.basics.FluidAwareTrace;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;

import java.util.Set;

public class RenderBlockPreview {
    private BlockHitResult lastRayTraceResult = null;
    private ItemStack lastWand = ItemStack.EMPTY;
    public Set<BlockPos> undoBlocks;
    public Set<BlockPos> previewBlocks;

    @SubscribeEvent
    public void renderBlockHighlight(RenderHighlightEvent.Block event) {
        if(event.getTarget().getType() != HitResult.Type.BLOCK) return;

        BlockHitResult rtr = event.getTarget();
        Entity entity = event.getCamera().getEntity();
        if(!(entity instanceof Player player)) return;
        Set<BlockPos> blocks;
        float colorR = 0, colorG = 0, colorB = 0;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        if (KeybindHandler.undoKeyCombDown()) {
            blocks = undoBlocks;
            colorG = 1;
        }
        else {
            // Use cached previews of the same target pos/dir
            // Exception: always update if blockCount < 2 to prevent 1-block previews when block updates
            // from the last placement are lagging
            if(lastRayTraceResult == null || !compareRTR(lastRayTraceResult, rtr) || !lastWand.equals(wand)
                || previewBlocks == null || previewBlocks.size() < 2) {
                lastRayTraceResult = rtr;
                lastWand = wand;
                ModMessages.sendToServer(new PacketRequestPreview(rtr, wand));
            }
            blocks = previewBlocks;
            float[] color = previewColor(wand);
            colorR = color[0];
            colorG = color[1];
            colorB = color[2];
        }

        if(blocks == null || blocks.isEmpty()) return;

        PoseStack ms = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        drawBoxes(ms, buffer, event.getCamera().getPosition(), blocks, colorR, colorG, colorB);

        event.setCanceled(true);
    }

    /**
     * Draws the previews that do not belong to a block highlight: the last operation while the undo
     * combination is held, and the fluid preview of a crosshair that misses every block.
     * <p>
     * This runs before the translucent blocks are drawn. Water writes depth, so a preview drawn after
     * it would be cut off wherever the boxes are under the water surface, which is exactly where the
     * boxes of a fluid preview sit.
     */
    @SubscribeEvent
    public void renderStage(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if(player == null) return;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        PoseStack ms = event.getPoseStack();
        MultiBufferSource buffer = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        // Undo: the highlight event only fires while a block is aimed at, this pass shows the last
        // operation no matter what is looked at while the undo key combination is held.
        if(KeybindHandler.undoKeyCombDown()) {
            Set<BlockPos> blocks = undoBlocks;
            if(blocks == null || blocks.isEmpty()) return;
            drawBoxes(ms, buffer, cameraPos, blocks, 0, 1, 0);
            return;
        }

        // Fluid preview: the vanilla trace walks past fluids, so looking at open water reports a miss
        // and the highlight event never fires. The blocks are planned by the server, so it is asked for
        // them the same way the aimed preview is.
        if(minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) return;
        if(!PlusWandUtil.fluidRemovalOn(wand)) return;
        if(!PlusWandUtil.removesBlocks(new WandOptions(wand).cores.get().getWandAction())) return;

        BlockHitResult fluidHit;
        try {
            fluidHit = FluidAwareTrace.fluidTarget(player.level(), player);
        }
        catch(RuntimeException exception) {
            return;
        }
        if(fluidHit == null) return;

        if(lastRayTraceResult == null || !compareRTR(lastRayTraceResult, fluidHit) || !lastWand.equals(wand)) {
            lastRayTraceResult = fluidHit;
            lastWand = wand;
            ModMessages.sendToServer(new PacketRequestPreview(fluidHit, wand));
        }

        Set<BlockPos> blocks = previewBlocks;
        if(blocks == null || blocks.isEmpty()) return;

        float[] color = previewColor(wand);
        drawBoxes(ms, buffer, cameraPos, blocks, color[0], color[1], color[2]);
    }

    /**
     * The colour of the selected core, like the 1.12.2 version drew it. The plain construction core
     * has no colour of its own and is shown in white.
     */
    private static float[] previewColor(ItemStack wand) {
        int color = new WandOptions(wand).cores.get().getColor();
        if(color < 0) return new float[]{1.0F, 1.0F, 1.0F};
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }

    private static void drawBoxes(PoseStack ms, MultiBufferSource buffer, Vec3 cameraPos, Set<BlockPos> blocks,
                                  float colorR, float colorG, float colorB) {
        VertexConsumer lineBuilder = buffer.getBuffer(RenderType.LINES);

        double d0 = cameraPos.x;
        double d1 = cameraPos.y;
        double d2 = cameraPos.z;

        for(BlockPos block : blocks) {
            AABB aabb = new AABB(block).move(-d0, -d1, -d2);
            LevelRenderer.renderLineBox(ms, lineBuilder, aabb, colorR, colorG, colorB, 0.4F);
        }
    }

    public void reset() {
        lastRayTraceResult = null;
        lastWand = ItemStack.EMPTY;
        previewBlocks = null;
    }

    private static boolean compareRTR(BlockHitResult rtr1, BlockHitResult rtr2) {
        return rtr1.getBlockPos().equals(rtr2.getBlockPos()) && rtr1.getDirection().equals(rtr2.getDirection());
    }
}
