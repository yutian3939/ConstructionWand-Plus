package thetadev.constructionwand.client;

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
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.basics.FluidAwareTrace;
import thetadev.constructionwand.plus.basics.PlusWandUtil;
import thetadev.constructionwand.wand.WandJob;

import java.util.Set;

public class RenderBlockPreview
{
    private WandJob wandJob;
    public Set<BlockPos> undoBlocks = Set.of();

    @SubscribeEvent
    public void renderBlockHighlight(RenderHighlightEvent.Block event) {
        if(event.getTarget().getType() != HitResult.Type.BLOCK) return;

        Entity entity = event.getCamera().getEntity();
        if(!(entity instanceof Player player)) return;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        // The undo preview is drawn in the level stage pass, so it shows up even when nothing is
        // aimed at; all that is left here is hiding the vanilla outline.
        if(isUndoMode(player)) {
            event.setCanceled(true);
            return;
        }

        BlockHitResult rtr = event.getTarget();
        // Use cached wandJob for previews of the same target pos/dir
        // Exception: always update if blockCount < 2 to prevent 1-block previews when block updates
        // from the last placement are lagging
        if(wandJob == null || !compareRTR(wandJob.originalRayTraceResult, rtr) || !(wandJob.wand.equals(wand))
            || wandJob.blockCount() < 2) {
            wandJob = ItemWand.getWandJob(player, player.level(), rtr, wand);
        }

        Set<BlockPos> blocks = wandJob.getBlockPositions();
        if(blocks.isEmpty()) return;

        float[] color = previewColor(wand);
        drawBoxes(event.getPoseStack(), event.getMultiBufferSource(), player, event.getPartialTick(), blocks, color[0], color[1], color[2]);
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
        double partialTicks = event.getPartialTick();

        // Undo: the highlight event only fires while a block is aimed at, this pass shows the last
        // operation no matter what is looked at.
        if(isUndoMode(player)) {
            Set<BlockPos> blocks = undoBlocks;
            if(blocks == null || blocks.isEmpty()) return;
            drawBoxes(ms, buffer, player, partialTicks, blocks, 0, 1, 0);
            return;
        }

        // Fluid preview: the vanilla trace walks past fluids, so looking at open water reports a miss
        // and the highlight event never fires.
        if(minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) return;
        if(!PlusWandUtil.fluidRemovalOn(wand)) return;
        if(!PlusWandUtil.removesBlocks(new WandOptions(wand).cores.get().getWandAction())) return;

        Set<BlockPos> blocks;
        try {
            BlockHitResult fluidHit = FluidAwareTrace.fluidTarget(player.level(), player);
            if(fluidHit == null) return;
            blocks = ItemWand.getWandJob(player, player.level(), fluidHit, wand).getBlockPositions();
        }
        catch(RuntimeException exception) {
            wandJob = null;
            return;
        }
        if(blocks.isEmpty()) return;

        float[] color = previewColor(wand);
        drawBoxes(ms, buffer, player, partialTicks, blocks, color[0], color[1], color[2]);
    }

    private static boolean isUndoMode(Player player) {
        return player.isCrouching() && ClientEvents.isOptKeyDown();
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

    private static void drawBoxes(PoseStack ms, MultiBufferSource buffer, Player player, double partialTicks,
                                  Set<BlockPos> blocks, float colorR, float colorG, float colorB) {
        VertexConsumer lineBuilder = buffer.getBuffer(RenderType.LINES);

        double d0 = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double d1 = player.yOld + player.getEyeHeight() + (player.getY() - player.yOld) * partialTicks;
        double d2 = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        for(BlockPos block : blocks) {
            AABB aabb = new AABB(block).move(-d0, -d1, -d2);
            LevelRenderer.renderLineBox(ms, lineBuilder, aabb, colorR, colorG, colorB, 0.4F);
        }
    }

    public void reset() {
        wandJob = null;
    }

    private static boolean compareRTR(BlockHitResult rtr1, BlockHitResult rtr2) {
        return rtr1.getBlockPos().equals(rtr2.getBlockPos()) && rtr1.getDirection().equals(rtr2.getDirection());
    }
}
