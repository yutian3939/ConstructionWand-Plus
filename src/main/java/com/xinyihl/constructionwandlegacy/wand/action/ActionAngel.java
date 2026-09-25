package com.xinyihl.constructionwandlegacy.wand.action;

import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class ActionAngel implements WandAction {
    public static final ActionAngel INSTANCE = new ActionAngel();

    private ActionAngel() {
    }

    @Override
    public int getLimit(ItemStack wand) {
        return ((ItemWand) wand.getItem()).getSpec().getAngelRange();
    }

    @Override
    public List<WandOperation> plan(WandContext context, OperationResolver resolver, int limit) {
        RayTraceResult hit = context.getRayTraceResult();
        if (hit == null || hit.sideHit == null) {
            return Collections.emptyList();
        }

        BlockPos current = hit.getBlockPos();
        EnumFacing direction = hit.sideHit.getOpposite();
        for (int distance = 0; distance < limit; distance++) {
            current = current.offset(direction);
            WandOperation operation = resolver.createPlacement(current, context.getWorld().getBlockState(hit.getBlockPos()));
            if (operation != null) {
                return Collections.singletonList(operation);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<WandOperation> planFromAir(WandContext context, OperationResolver resolver, int limit) {
        Vec3d placeVector = context.getPlayer().getPositionVector().add(context.getPlayer().getLookVec().scale(2));
        WandOperation operation = resolver.createPlacement(new BlockPos(placeVector), null);
        return operation == null ? Collections.emptyList() : Collections.singletonList(operation);
    }
}
