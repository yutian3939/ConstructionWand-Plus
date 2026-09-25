package com.xinyihl.constructionwandlegacy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Set;

public final class RenderBlockPreview {
    private final ClientState state;
    private final ClientPreviewController previewController;

    public RenderBlockPreview(ClientState state, ClientPreviewController previewController) {
        this.state = state;
        this.previewController = previewController;
    }

    private static void renderBoxes(EntityPlayer player, Set<BlockPos> blocks, PreviewSnapshot.PreviewColor color, float partialTicks) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);

        for (BlockPos blockPos : blocks) {
            AxisAlignedBB box = new AxisAlignedBB(blockPos).grow(0.002D).offset(-px, -py, -pz);
            RenderGlobal.drawSelectionBoundingBox(box, color.getRed(), color.getGreen(), color.getBlue(), 0.4F);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        RayTraceResult target = event.getTarget();
        EntityPlayer player = event.getPlayer();
        if (target == null || player == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        PreviewSnapshot snapshot = state.getPreview();
        // The undo preview does not belong to the aimed block, it is drawn in the world pass.
        if (snapshot.getMode() != PreviewKey.Mode.BLOCK || snapshot.isEmpty()) {
            return;
        }
        PreviewKey key = snapshot.getKey();
        if (key == null || !key.matchesBlock(target.getBlockPos(), target.sideHit)) {
            return;
        }

        renderBoxes(player, snapshot.getBlocks(), snapshot.getColor(), event.getPartialTicks());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PreviewSnapshot snapshot = state.getPreview();
        if (snapshot.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (snapshot.getMode() == PreviewKey.Mode.UNDO) {
            renderBoxes(player, snapshot.getBlocks(), snapshot.getColor(), event.getPartialTicks());
            return;
        }
        if (snapshot.getMode() != PreviewKey.Mode.AIR) {
            return;
        }
        if (minecraft.objectMouseOver != null && minecraft.objectMouseOver.typeOfHit != RayTraceResult.Type.MISS) {
            return;
        }
        renderBoxes(player, snapshot.getBlocks(), snapshot.getColor(), event.getPartialTicks());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            previewController.resetPreview();
            state.resetWorld();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        previewController.resetPreview();
        state.resetConnection();
    }
}
