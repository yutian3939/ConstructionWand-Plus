package com.xinyihl.constructionwandlegacy.basics;

import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class WandUtil {
    private WandUtil() {
    }

    public static List<ItemStack> getHotbarWithOffhand(EntityPlayer player) {
        ArrayList<ItemStack> hotbar = new ArrayList<>(10);
        for (int i = 0; i < 9; i++) {
            hotbar.add(player.inventory.getStackInSlot(i));
        }
        hotbar.add(player.getHeldItemOffhand());
        return hotbar;
    }

    public static ItemStack holdingWand(EntityPlayer player) {
        ItemStack mainhand = player.getHeldItem(EnumHand.MAIN_HAND);
        if (!mainhand.isEmpty() && mainhand.getItem() instanceof ItemWand) {
            return mainhand;
        }

        ItemStack offhand = player.getHeldItem(EnumHand.OFF_HAND);
        if (!offhand.isEmpty() && offhand.getItem() instanceof ItemWand) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }

    public static boolean isPositionPlaceable(World world, EntityPlayer player, BlockPos pos, boolean replace) {
        IBlockState state = world.getBlockState(pos);
        if (replace) {
            return state.getMaterial().isReplaceable();
        }
        return world.isAirBlock(pos);
    }

    public static boolean placeBlock(World world, EntityPlayer player, IBlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (!world.mayPlace(block, pos, false, null, player)) {
            return false;
        }
        return world.setBlockState(pos, state, 3);
    }

    public static boolean placeBlockAt(World world, EntityPlayer player, BlockPos pos, ItemStack placeStack, IBlockState state, @Nullable RayTraceResult rayTraceResult) {
        if (placeStack.isEmpty() || !(placeStack.getItem() instanceof ItemBlock)) {
            return false;
        }
        ItemBlock item = (ItemBlock) placeStack.getItem();

        EnumFacing facing = rayTraceResult != null && rayTraceResult.sideHit != null ? rayTraceResult.sideHit : EnumFacing.UP;
        float hitX = 0.5F;
        float hitY = 0.5F;
        float hitZ = 0.5F;

        if (rayTraceResult != null && rayTraceResult.hitVec != null) {
            Vec3d hit = rayTraceResult.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
            hitX = (float) hit.x;
            hitY = (float) hit.y;
            hitZ = (float) hit.z;
        }

        ItemStack stack = placeStack.copy();
        stack.setCount(1);

        BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(BlockSnapshot.getBlockSnapshot(world, pos), world.getBlockState(pos.offset(facing.getOpposite())), player);
        MinecraftForge.EVENT_BUS.post(placeEvent);

        if (placeEvent.isCanceled()) {
            return false;
        }

        if (!item.placeBlockAt(stack, player, world, pos, facing, hitX, hitY, hitZ, state)) {
            return false;
        }

        return true;
    }

    public static boolean removeBlock(World world, @Nullable EntityPlayer player, @Nullable IBlockState expectedBlock, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos)) {
            return false;
        }

        if (expectedBlock != null && !matchesPlannedState(state, expectedBlock)) {
            return false;
        }

        if (player == null) {
            return world.setBlockToAir(pos);
        }

        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(world, pos, state, player);
        MinecraftForge.EVENT_BUS.post(breakEvent);
        if (breakEvent.isCanceled()) {
            return false;
        }

        Block block = state.getBlock();
        if (!block.removedByPlayer(state, world, pos, player, false)) {
            return false;
        }
        block.onPlayerDestroy(world, pos, state);
        return true;
    }

    public static boolean isBlockRemovable(World world, EntityPlayer player, BlockPos pos) {
        if (world.isAirBlock(pos)) {
            return false;
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity != null) {
            return false;
        }

        return world.isBlockModifiable(player, pos) && world.getWorldBorder().contains(pos);
    }

    /**
     * Whether the block is unbreakable, which is how vanilla and mods flag blocks like bedrock
     * (negative block hardness). The destruction core ignores this and removes anything; the digging
     * core refuses such blocks because it would hand them out as items.
     */
    public static boolean isBlockUnbreakable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlockHardness(world, pos) < 0.0F;
    }

    public static boolean isBlockPermeable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getMaterial().isReplaceable() || state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB;
    }

    /**
     * Whether the position still holds what an operation planned.
     * <p>
     * Fluids are the one exception: water and lava re-evaluate their level as soon as a neighbouring
     * block disappears, so a planned fluid can legitimately be another level, or have swapped its
     * source block for the flowing one, by the time the operation runs.
     */
    public static boolean matchesPlannedState(IBlockState current, IBlockState planned) {
        return current.equals(planned) || isSameFluid(current, planned);
    }

    /**
     * Whether both states are the same fluid. Vanilla water and lava switch between their source
     * block and the flowing one, a modded fluid keeps its block.
     */
    public static boolean isSameFluid(IBlockState first, IBlockState second) {
        if (!isFluid(first) || !isFluid(second)) {
            return false;
        }
        if (first.getBlock() == second.getBlock()) {
            return true;
        }
        return first.getMaterial() == second.getMaterial()
                && first.getBlock() instanceof BlockLiquid
                && second.getBlock() instanceof BlockLiquid;
    }

    /**
     * Whether the state is a fluid, including the modded ones that do not use a vanilla liquid
     * material.
     */
    public static boolean isFluid(IBlockState state) {
        return state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock;
    }

    /**
     * The block the player is really looking at, fluids included.
     * <p>
     * The vanilla ray trace deliberately walks past fluids ({@code stopOnLiquid} is false), so a
     * water surface is not a block target at all: the trace either ends up at whatever lies behind
     * the water or reports the fluid as a {@link RayTraceResult.Type#MISS}. The same line is therefore
     * traced a second time with fluids counted as blocks, which aims at them exactly like at a solid
     * block; the plain hit stays the target for everything that is not a fluid.
     */
    @Nullable
    public static RayTraceResult resolveTarget(World world, EntityPlayer player, @Nullable RayTraceResult plainHit) {
        IAttributeInstance reachAttribute = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
        double reach = reachAttribute == null ? 5.0D : reachAttribute.getAttributeValue();
        Vec3d eyes = player.getPositionEyes(1.0F);
        RayTraceResult hit = FluidAwareTrace.trace(world, eyes, eyes.add(player.getLookVec().scale(reach)));
        return hit != null && isFluid(world.getBlockState(hit.getBlockPos())) ? hit : plainHit;
    }

}
