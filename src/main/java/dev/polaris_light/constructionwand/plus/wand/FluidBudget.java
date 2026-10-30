package dev.polaris_light.constructionwand.plus.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.polaris_light.constructionwand.plus.basics.FluidSources;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.plus.wand.undo.FluidSnapshot;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * How much of every fluid the wand's store can still take while one digging operation is planned.
 * <p>
 * A source block that no longer fits is left in the world instead of being cleared, so the wand never
 * destroys fluid it cannot keep. Flowing fluid is not stored and therefore not limited. The capacity
 * is asked once per fluid: it does not change while planning, nothing is filled before execution.
 * <p>
 * The capacity is only asked while the operation is planned on the server. The client draws the
 * preview from the same plan, and the stores of a wand only answer on the server, so a preview that
 * checked the capacity would never show a fluid block at all. Nothing is lost by that: the execution
 * asks again and leaves a source the store cannot take where it is.
 */
final class FluidBudget
{
    private final Player player;
    @Nullable
    private final IMaterialCore materialCore;
    private final ItemStack wand;
    private final boolean enforceCapacity;
    private final Map<Fluid, Integer> remaining = new HashMap<>();

    FluidBudget(Player player, @Nullable IMaterialCore materialCore, ItemStack wand, boolean enforceCapacity) {
        this.player = player;
        this.materialCore = materialCore;
        this.wand = wand;
        this.enforceCapacity = enforceCapacity;
    }

    /**
     * @return the snapshot that clears and stores the fluid at that position, or {@code null} when the
     * position holds a source there is no room for
     */
    @Nullable
    ISnapshot create(net.minecraft.world.level.Level level, BlockPos pos) {
        FluidSnapshot snapshot = FluidSnapshot.stored(level, player, pos, materialCore, wand);
        if(snapshot == null) return null;
        if(!enforceCapacity) return snapshot;

        FluidStack source = FluidSources.sourceFluid(level, pos);
        return source == null || reserve(source) ? snapshot : null;
    }

    private boolean reserve(FluidStack source) {
        Fluid key = source.getFluid();
        Integer left = remaining.get(key);
        if(left == null) left = capacity(source);

        int amount = Math.max(0, source.getAmount());
        if(left < amount) return false;

        remaining.put(key, left - amount);
        return true;
    }

    private int capacity(FluidStack source) {
        // No material core means nowhere to put the fluid, so no source is dug.
        if(materialCore == null) return 0;
        try {
            return Math.max(0, materialCore.fluidCapacity(player, wand, source.copy()));
        } catch(RuntimeException exception) {
            return 0;
        }
    }
}
