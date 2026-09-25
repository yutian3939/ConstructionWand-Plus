package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import com.xinyihl.constructionwandlegacy.wand.WandPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class ClientPreviewController {
    private final ClientState clientState;
    private final WandPlanner planner;
    private final PreviewCache cache = new PreviewCache();
    private final Map<Integer, PreviewSnapshot.PreviewColor> colors = new HashMap<>();
    private long clientTick;

    public ClientPreviewController(ClientState clientState, WandPlanner planner) {
        this.clientState = clientState;
        this.planner = planner;
    }

    public void tick(Minecraft minecraft, boolean undoMode) {
        long currentClientTick = ++clientTick;
        EntityPlayer player = minecraft.player;
        if (player == null || player.world == null) {
            resetPreview();
            return;
        }
        WandTarget target = WandTarget.locate(player);
        if (target == null) {
            resetPreview();
            return;
        }
        ItemStack wand = target.resolve(player);
        if (wand.isEmpty()) {
            resetPreview();
            return;
        }

        ConfigRuntime.Snapshot rules = clientState.getServerRules();
        if (rules == null) {
            resetPreview();
            return;
        }
        RayTraceResult hit = minecraft.objectMouseOver;
        WandState wandState = WandDataCodec.read(wand);
        Vec3d look = player.getLookVec();
        PreviewKey.Mode mode;
        RayTraceResult planningHit;
        BlockPos targetPos;
        EnumFacing targetSide;

        if (undoMode) {
            // The undo is a key of its own now, it is not aimed at a block, so the preview is shown
            // wherever the player looks instead of following the crosshair.
            mode = PreviewKey.Mode.UNDO;
            targetPos = player.getPosition();
            targetSide = EnumFacing.getFacingFromVector((float) look.x, (float) look.y, (float) look.z);
            planningHit = new RayTraceResult(RayTraceResult.Type.MISS, player.getPositionEyes(1.0F), targetSide, targetPos);
        } else {
            if (hit == null || hit.typeOfHit == RayTraceResult.Type.ENTITY) {
                resetPreview();
                return;
            }
            if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                mode = PreviewKey.Mode.BLOCK;
                planningHit = hit;
                targetPos = hit.getBlockPos();
                targetSide = hit.sideHit;
            } else {
                mode = PreviewKey.Mode.AIR;
                targetPos = player.getPosition();
                targetSide = EnumFacing.getFacingFromVector((float) look.x, (float) look.y, (float) look.z);
                planningHit = new RayTraceResult(RayTraceResult.Type.MISS, player.getPositionEyes(1.0F), targetSide, targetPos);
            }
        }

        PreviewKey key = PreviewKey.create(mode, player.world.provider.getDimension(), player.world.getTotalWorldTime(), player.posX, player.posY, player.posZ, look, targetPos, targetSide, target, wand, wandState, rules.getRevision());
        PreviewSnapshot next = cache.update(currentClientTick, key, () -> {
            if (mode == PreviewKey.Mode.UNDO) {
                return PreviewSnapshot.create(key, clientState.getUndoBlocks(), PreviewSnapshot.PreviewColor.GREEN);
            }
            WandContext context = WandContext.create(player, player.world, planningHit, wand, rules);
            WandPlan plan = planner.plan(context);
            try {
                int packedColor = wandState.getSelectedCore().getColor();
                PreviewSnapshot.PreviewColor color = colors.computeIfAbsent(packedColor, PreviewSnapshot.PreviewColor::fromPacked);
                return PreviewSnapshot.create(key, plan.getBlockPositions(), color);
            } finally {
                plan.discard();
            }
        });
        clientState.setPreview(next);
    }

    public void resetPreview() {
        cache.reset();
        clientState.setPreview(PreviewSnapshot.empty());
    }
}
