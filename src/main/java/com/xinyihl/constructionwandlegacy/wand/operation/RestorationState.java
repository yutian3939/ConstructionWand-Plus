package com.xinyihl.constructionwandlegacy.wand.operation;

import com.xinyihl.constructionwandlegacy.wand.WandOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * World-independent block state and tile data captured for later recovery.
 */
final class RestorationState {
    private final IBlockState state;
    @Nullable
    private final NBTTagCompound tileData;

    private RestorationState(IBlockState state, @Nullable NBTTagCompound tileData) {
        this.state = state;
        this.tileData = tileData == null ? null : tileData.copy();
    }

    static RestorationState capture(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        NBTTagCompound data = null;
        if (tile != null) {
            data = new NBTTagCompound();
            tile.writeToNBT(data);
        }
        return new RestorationState(world.getBlockState(pos), data);
    }

    IBlockState getState() {
        return state;
    }

    WandOperation.RollbackResult restore(World world, BlockPos pos) {
        try {
            boolean changed;
            if (state.getBlock().isAir(state, world, pos)) {
                changed = world.setBlockToAir(pos) || world.isAirBlock(pos);
            } else {
                changed = world.setBlockState(pos, state, 3);
            }
            if (!changed) {
                return WandOperation.RollbackResult.notRestored("world rejected state restoration");
            }
            if (tileData != null) {
                TileEntity tile = world.getTileEntity(pos);
                if (tile == null) {
                    return WandOperation.RollbackResult.notRestored("restored block has no tile entity");
                }
                tile.readFromNBT(tileData.copy());
                tile.markDirty();
            }
            return WandOperation.RollbackResult.restored();
        } catch (RuntimeException exception) {
            return WandOperation.RollbackResult.failed("exception restoring block state", exception);
        }
    }
}
