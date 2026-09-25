package com.xinyihl.constructionwandlegacy.basics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Block ray trace that treats every fluid as a block the line can hit.
 * <p>
 * This is the vanilla algorithm ({@code World#rayTraceBlocks} with {@code stopOnLiquid} false,
 * {@code ignoreBlockWithoutBoundingBox} false and {@code returnLastUncollidableBlock} false) with a
 * single rule changed: a fluid counts as collidable whatever its level is, where vanilla only accepts
 * a source because {@code BlockLiquid#canCollideCheck} asks for level zero.
 * <p>
 * Everything else - the stepping, the order of the visited blocks and the hit face - comes from the
 * same code the crosshair uses, so a fluid is aimed at exactly like a solid block: looking down at a
 * sea surface reports its top face, looking at it from the side reports that side.
 */
public final class FluidAwareTrace {
    /**
     * Step limit of the vanilla loop, kept so a degenerate line cannot spin forever.
     */
    private static final int MAX_STEPS = 200;

    private FluidAwareTrace() {
    }

    /**
     * @return the first block the line hits, fluid or solid, or {@code null} when it hits nothing
     */
    @Nullable
    public static RayTraceResult trace(World world, Vec3d start, Vec3d end) {
        if (!isFinite(start) || !isFinite(end)) {
            return null;
        }

        int endX = MathHelper.floor(end.x);
        int endY = MathHelper.floor(end.y);
        int endZ = MathHelper.floor(end.z);
        int posX = MathHelper.floor(start.x);
        int posY = MathHelper.floor(start.y);
        int posZ = MathHelper.floor(start.z);

        RayTraceResult hit = collide(world, new BlockPos(posX, posY, posZ), start, end);
        if (hit != null) {
            return hit;
        }

        for (int step = 0; step < MAX_STEPS; step++) {
            if (!isFinite(start) || (posX == endX && posY == endY && posZ == endZ)) {
                return null;
            }

            boolean movesX = true;
            boolean movesY = true;
            boolean movesZ = true;
            double planeX = 999.0D;
            double planeY = 999.0D;
            double planeZ = 999.0D;

            if (endX > posX) {
                planeX = (double) posX + 1.0D;
            } else if (endX < posX) {
                planeX = (double) posX;
            } else {
                movesX = false;
            }

            if (endY > posY) {
                planeY = (double) posY + 1.0D;
            } else if (endY < posY) {
                planeY = (double) posY;
            } else {
                movesY = false;
            }

            if (endZ > posZ) {
                planeZ = (double) posZ + 1.0D;
            } else if (endZ < posZ) {
                planeZ = (double) posZ;
            } else {
                movesZ = false;
            }

            double spanX = end.x - start.x;
            double spanY = end.y - start.y;
            double spanZ = end.z - start.z;
            double distanceX = movesX ? (planeX - start.x) / spanX : 999.0D;
            double distanceY = movesY ? (planeY - start.y) / spanY : 999.0D;
            double distanceZ = movesZ ? (planeZ - start.z) / spanZ : 999.0D;

            // Vanilla turns a negative zero into a tiny negative step to keep the arithmetic honest.
            if (distanceX == -0.0D) {
                distanceX = -1.0E-4D;
            }
            if (distanceY == -0.0D) {
                distanceY = -1.0E-4D;
            }
            if (distanceZ == -0.0D) {
                distanceZ = -1.0E-4D;
            }

            EnumFacing entering;
            if (distanceX < distanceY && distanceX < distanceZ) {
                entering = endX > posX ? EnumFacing.WEST : EnumFacing.EAST;
                start = new Vec3d(planeX, start.y + spanY * distanceX, start.z + spanZ * distanceX);
            } else if (distanceY < distanceZ) {
                entering = endY > posY ? EnumFacing.DOWN : EnumFacing.UP;
                start = new Vec3d(start.x + spanX * distanceY, planeY, start.z + spanZ * distanceY);
            } else {
                entering = endZ > posZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                start = new Vec3d(start.x + spanX * distanceZ, start.y + spanY * distanceZ, planeZ);
            }

            posX = MathHelper.floor(start.x) - (entering == EnumFacing.EAST ? 1 : 0);
            posY = MathHelper.floor(start.y) - (entering == EnumFacing.UP ? 1 : 0);
            posZ = MathHelper.floor(start.z) - (entering == EnumFacing.SOUTH ? 1 : 0);

            hit = collide(world, new BlockPos(posX, posY, posZ), start, end);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * The one rule that differs from vanilla: every fluid is collidable, not only a level zero one.
     */
    @Nullable
    private static RayTraceResult collide(World world, BlockPos pos, Vec3d start, Vec3d end) {
        IBlockState state = world.getBlockState(pos);
        boolean collidable = WandUtil.isFluid(state) || state.getBlock().canCollideCheck(state, false);
        return collidable ? state.collisionRayTrace(world, pos, start, end) : null;
    }

    private static boolean isFinite(Vec3d vector) {
        return !Double.isNaN(vector.x) && !Double.isNaN(vector.y) && !Double.isNaN(vector.z);
    }
}
