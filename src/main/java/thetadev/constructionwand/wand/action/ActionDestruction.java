package thetadev.constructionwand.wand.action;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.plus.basics.ExtraWandOption;
import thetadev.constructionwand.plus.basics.FluidRemovalMode;
import thetadev.constructionwand.plus.basics.PlusWandUtil;
import thetadev.constructionwand.plus.wand.ActionDigging;
import thetadev.constructionwand.plus.wand.FluidCleanup;
import thetadev.constructionwand.plus.wand.undo.FluidSnapshot;
import thetadev.constructionwand.wand.undo.DestroySnapshot;
import thetadev.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ActionDestruction implements IWandAction
{
    @Override
    public int getLimit(ItemStack wand) {
        ConfigServer.WandProperties properties = ConfigServer.getWandProperties(wand.getItem());
        // Same limit as construction and digging. The per wand destruction entry only still turns
        // the core off for a tier when it is 0.
        return properties.isDestructionDisabled() ? 0 : properties.getLimit();
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshots(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                        WandOptions options, IWandSupplier supplier, int limit) {
        if(rayTraceResult == null || rayTraceResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return new ArrayList<>();

        Direction breakFace = rayTraceResult.getDirection();
        BlockPos startingPoint = rayTraceResult.getBlockPos();
        BlockState targetBlock = level.getBlockState(startingPoint);

        FluidRemovalMode fluidMode = ExtraWandOption.getFluidRemoval(wand);
        // In "on" fluids are candidates like any block, the matching mode then decides whether a
        // source and the flowing block are the same kind of block. In "smart" they are not aimed at at
        // all, they are only cleared where the removed blocks enclose them.
        boolean fluidsAsBlocks = fluidMode == FluidRemovalMode.ON;

        List<ISnapshot> snapshots = ActionDigging.traverse(startingPoint, breakFace, options, limit, candidate -> {
            // Only break blocks facing the player, with no collidable blocks in between.
            if(!WandUtil.isBlockPermeable(level, candidate.relative(breakFace))) return null;

            BlockState candidateBlock = level.getBlockState(candidate);
            boolean fluid = PlusWandUtil.isFluid(candidateBlock);
            if(fluid && !fluidsAsBlocks) return null;
            if(!options.matchBlocks(targetBlock.getBlock(), candidateBlock.getBlock())) return null;
            if(fluid) return FluidSnapshot.plain(level, player, candidate, true);

            return DestroySnapshot.get(level, player, candidate);
        });

        // Destruction only touches the world, so its undo puts the enclosed fluid back. It harvests
        // nothing, so the fluid is never stored and no budget is passed in.
        return fluidMode == FluidRemovalMode.SMART
                ? FluidCleanup.include(level, player, snapshots, breakFace, true, null)
                : snapshots;
    }

    @Nonnull
    @Override
    public List<ISnapshot> getSnapshotsFromAir(Level level, Player player, BlockHitResult rayTraceResult, ItemStack wand,
                                               WandOptions options, IWandSupplier supplier, int limit) {
        return new ArrayList<>();
    }
}
