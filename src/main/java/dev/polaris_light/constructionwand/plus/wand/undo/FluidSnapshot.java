package dev.polaris_light.constructionwand.plus.wand.undo;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.polaris_light.constructionwand.plus.basics.FluidSources;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;
import dev.polaris_light.constructionwand.plus.basics.UndoFeedback;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;

/**
 * Removes one fluid block, so a hole the wand dug does not stay flooded.
 * <p>
 * Two flavours exist. Without storage the fluid is simply cleared and, depending on the action, put
 * back by the undo. With storage the source block is handed to the wand's material core before it is
 * removed, one bucket each; flowing fluid is worth nothing. A store that cannot take the fluid leaves
 * it where it is instead of destroying it, and the undo refuses while the fluid is not back in the
 * store.
 */
public class FluidSnapshot implements ISnapshot
{
    private final BlockPos pos;
    private final BlockState fluid;
    private final boolean restorable;
    private final boolean storeFluid;
    @Nullable
    private final IMaterialCore materialCore;
    @Nullable
    private final ItemStack wand;

    /**
     * What really went into the store, {@code null} when nothing was stored.
     */
    @Nullable
    private FluidStack storedFluid;
    private boolean fluidReclaimed;

    private FluidSnapshot(BlockPos pos, BlockState fluid, boolean restorable, boolean storeFluid,
                          @Nullable IMaterialCore materialCore, @Nullable ItemStack wand) {
        this.pos = pos;
        this.fluid = fluid;
        this.restorable = restorable;
        this.storeFluid = storeFluid;
        this.materialCore = materialCore;
        this.wand = wand == null ? null : wand.copy();
    }

    /**
     * @return the snapshot, or {@code null} when the position does not hold a removable fluid
     */
    @Nullable
    public static FluidSnapshot plain(Level level, Player player, BlockPos pos, boolean restorable) {
        return create(level, player, pos, restorable, false, null, null);
    }

    /**
     * @return the snapshot that stores the fluid in the wand's material core
     */
    @Nullable
    public static FluidSnapshot stored(Level level, Player player, BlockPos pos, @Nullable IMaterialCore materialCore, ItemStack wand) {
        return create(level, player, pos, true, true, materialCore, wand);
    }

    @Nullable
    private static FluidSnapshot create(Level level, Player player, BlockPos pos, boolean restorable, boolean storeFluid,
                                        @Nullable IMaterialCore materialCore, @Nullable ItemStack wand) {
        BlockState state = level.getBlockState(pos);
        if(!PlusWandUtil.isFluid(state)) return null;
        if(!PlusWandUtil.isPositionModifiable(level, player, pos)) return null;
        return new FluidSnapshot(pos, state, restorable, storeFluid, materialCore, wand);
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public BlockState getBlockState() {
        return fluid;
    }

    @Override
    public ItemStack getRequiredItems() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean execute(Level level, Player player, BlockHitResult rayTraceResult) {
        // Water and lava re-evaluate their flow the moment a neighbouring block disappears, so the
        // fluid can already have changed its level since planning. Any fluid left there is removed and
        // the state that is really there is the one the undo puts back.
        BlockState current = level.getBlockState(pos);
        if(!PlusWandUtil.isFluid(current)) return false;

        FluidStack source = null;
        if(storeFluid) {
            // The fluid is looked at again here, for the same reason.
            source = FluidSources.sourceFluid(level, pos);
            if(source != null) {
                if(capacity(player, source) < source.getAmount()) return false;
                int deposited = deposit(player, source, source.getAmount());
                if(deposited < source.getAmount()) {
                    // The store shrank since planning: give back what went in and leave the fluid alone.
                    release(player, source, deposited);
                    return false;
                }
                storedFluid = source.copy();
            }
        }

        if(!PlusWandUtil.breakBlock(level, player, pos, current, false)) {
            release(player, source, source == null ? 0 : source.getAmount());
            storedFluid = null;
            return false;
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }

    @Override
    public boolean canRestore(Level level, Player player) {
        if(storedFluid == null) return true;
        if(storedFluidAmount(player) >= storedFluid.getAmount()) return true;
        UndoFeedback.reportFluid(player, storedFluid);
        return false;
    }

    @Override
    public boolean restore(Level level, Player player) {
        if(!restorable && storedFluid == null) {
            // The digging core counts the cleared fluid as part of the harvest, so its undo gives the
            // blocks back but not the fluid that ran into the hole.
            return true;
        }
        if(!reclaim(player)) {
            if(storedFluid != null) UndoFeedback.reportFluid(player, storedFluid);
            return false;
        }
        if(storedFluid == null) return true;
        return putBack(level, player);
    }

    @Override
    public void forceRestore(Level level) {
        if(storedFluid != null && !fluidReclaimed) {
            fluidReclaimed = withdraw(null, storedFluid, storedFluid.getAmount()) <= 0;
        }
        putBack(level, null);
    }

    /**
     * Takes the fluid stored for this block back out of the store, all or nothing: when the store no
     * longer holds all of it, what was taken is put back so the undo can be retried later.
     *
     * @return whether the store no longer holds the fluid
     */
    private boolean reclaim(@Nullable Player player) {
        if(fluidReclaimed || storedFluid == null) return true;
        int missing = withdraw(player, storedFluid, storedFluid.getAmount());
        if(missing <= 0) {
            fluidReclaimed = true;
            return true;
        }
        int taken = storedFluid.getAmount() - missing;
        if(taken > 0) deposit(player, storedFluid, taken);
        return false;
    }

    private boolean putBack(Level level, @Nullable Player player) {
        if(PlusWandUtil.matchesPlannedState(level.getBlockState(pos), fluid)) return true;
        if(player != null && !PlusWandUtil.isPositionModifiable(level, player, pos)) return false;
        if(!level.isEmptyBlock(pos) && !PlusWandUtil.clearIntrudingFluid(level, pos)) return false;
        return level.setBlockAndUpdate(pos, fluid);
    }

    /**
     * @return how much of that fluid is still stored
     */
    private int storedFluidAmount(@Nullable Player player) {
        if(materialCore == null || wand == null || storedFluid == null) return 0;
        try {
            return Math.max(0, materialCore.countStoredFluid(player, wand, storedFluid));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    private int capacity(@Nullable Player player, FluidStack fluidStack) {
        if(materialCore == null || wand == null) return 0;
        try {
            return Math.max(0, materialCore.fluidCapacity(player, wand, fluidStack.copy()));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    private int deposit(@Nullable Player player, FluidStack fluidStack, int amount) {
        if(materialCore == null || wand == null || amount <= 0) return 0;
        try {
            int stored = materialCore.depositFluid(player, wand, new FluidStack(fluidStack.getFluid(), amount));
            return Math.max(0, Math.min(amount, stored));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken back out of the store
     */
    private int withdraw(@Nullable Player player, FluidStack fluidStack, int amount) {
        if(amount <= 0) return 0;
        if(materialCore == null || wand == null) return amount;
        try {
            int remaining = materialCore.withdrawFluid(player, wand, new FluidStack(fluidStack.getFluid(), amount), amount);
            return remaining < 0 || remaining > amount ? amount : remaining;
        } catch(RuntimeException exception) {
            return amount;
        }
    }

    /**
     * Takes fluid back out of the store without using it, for a fluid that stayed in the world.
     */
    private void release(@Nullable Player player, @Nullable FluidStack source, int amount) {
        if(source == null || amount <= 0) return;
        withdraw(player, source, amount);
    }
}
