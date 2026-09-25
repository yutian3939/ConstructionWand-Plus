package com.xinyihl.morewandcore.wand;

import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.morewandcore.basics.FluidSources;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * How much of every fluid the wand's store can still take while one digging operation is planned.
 * <p>
 * A source block that no longer fits is left in the world instead of being cleared, so the wand never
 * destroys fluid it cannot keep. Flowing fluid is not stored and therefore not limited. The capacity
 * is asked once per fluid: it does not change while planning, nothing is filled before execution.
 */
final class FluidBudget {
    private final WandContext context;
    @Nullable
    private final IWandCore materialCore;
    private final ItemStack wand;
    private final Map<Fluid, Integer> remaining = new HashMap<>();

    FluidBudget(WandContext context, @Nullable IWandCore materialCore, ItemStack wand) {
        this.context = context;
        this.materialCore = materialCore;
        this.wand = wand;
    }

    /**
     * @return the operation that clears and stores the fluid at that position, or {@code null} when
     * the position holds a source there is no room for
     */
    @Nullable
    WandOperation create(WandContext context, BlockPos pos) {
        WandOperation operation = StoredFluidOperation.create(context, pos, materialCore, wand);
        if (operation == null) {
            return null;
        }
        FluidStack source = FluidSources.sourceFluid(context.getWorld(), pos);
        return source == null || reserve(source) ? operation : null;
    }

    private boolean reserve(FluidStack source) {
        Fluid key = source.getFluid();
        Integer left = remaining.get(key);
        if (left == null) {
            left = capacity(source);
        }
        int amount = Math.max(0, source.amount);
        if (left < amount) {
            return false;
        }
        remaining.put(key, left - amount);
        return true;
    }

    private int capacity(FluidStack source) {
        if (materialCore == null) {
            // No material core means nowhere to put the fluid, so no source is dug.
            return 0;
        }
        try {
            return Math.max(0, materialCore.fluidCapacity(context.getPlayer(), wand, source.copy()));
        } catch (RuntimeException exception) {
            return 0;
        }
    }
}
