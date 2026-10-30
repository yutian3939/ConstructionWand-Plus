package thetadev.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;

/**
 * Lets the wand aim at a fluid surface.
 * <p>
 * The vanilla crosshair trace walks past fluids, so aiming at a lake hits whatever lies behind it or
 * nothing at all. When the wand treats fluids as blocks, the same line is walked here with the fluid
 * treated as an <b>ordinary full block</b>: the hit face is the face the line really enters through,
 * exactly like the vanilla trace reports it for a solid block. Looking down at a water surface
 * therefore reports its top face (the wand extends along the surface) and looking at it from the side
 * reports that side, with no special casing for the fluid's own, lower shape.
 */
public final class FluidAwareTrace
{
    /**
     * Step of the walk, in blocks. Small enough not to skip a cell, and only a reach worth of steps.
     */
    private static final double STEP = 0.05D;

    private static final double EPSILON = 1.0E-7D;

    private FluidAwareTrace() {
    }

    /**
     * @return the fluid the player is looking at, or the original hit when no fluid is in the way
     */
    @Nullable
    public static BlockHitResult resolveTarget(Level level, Player player, @Nullable BlockHitResult plainHit) {
        BlockHitResult fluidHit = fluidTarget(level, player);
        return fluidHit != null ? fluidHit : plainHit;
    }

    /**
     * @return the first fluid the player's line of sight hits within reach, or {@code null}
     */
    @Nullable
    public static BlockHitResult fluidTarget(Level level, Player player) {
        if(level == null || player == null) return null;

        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        if(!(reach > 0.0D)) reach = 5.0D;

        Vec3 eyes = player.getEyePosition(1.0F);
        Vec3 end = eyes.add(player.getViewVector(1.0F).scale(reach));
        return walk(level, eyes, end);
    }

    /**
     * Walks the line cell by cell and returns the first fluid cell, stopping at the first solid block:
     * a wall in front of the fluid means the player is aiming at the wall, not at the fluid.
     */
    @Nullable
    private static BlockHitResult walk(Level level, Vec3 eyes, Vec3 end) {
        Vec3 delta = end.subtract(eyes);
        double length = delta.length();
        if(length <= EPSILON) return null;

        int steps = (int) Math.ceil(length / STEP);
        // The cell the eyes are in is skipped, so a player standing in water does not target the
        // water they are inside (its shape contains the ray's start, which has no entry face).
        BlockPos startPos = BlockPos.containing(eyes);
        BlockPos previous = null;

        for(int index = 0; index <= steps; index++) {
            double travelled = Math.min(index * STEP, length);
            Vec3 stepPoint = eyes.add(delta.scale(travelled / length));
            BlockPos pos = BlockPos.containing(stepPoint);
            if(pos.equals(previous)) continue;
            previous = pos;

            if(pos.equals(startPos)) continue;
            if(!level.isLoaded(pos)) return null;

            BlockState state = level.getBlockState(pos);
            if(PlusWandUtil.isFluid(state)) {
                // Treated as a full block, so the entered face is the real one for that block and
                // not guessed from the look direction.
                BlockHitResult hit = clipCell(pos, eyes, end);

                Direction face = hit != null ? hit.getDirection() : Direction.getNearest(-delta.x, -delta.y, -delta.z);
                Vec3 point = hit != null ? hit.getLocation() : stepPoint;

                // Looking down at the top layer of a fluid is a hit on its surface, exactly like
                // looking down at the top of a block. Without this the low, quiet edge of a water
                // surface is entered through its side at shallow angles and the wand would extend
                // down a vertical wall instead of along the surface.
                if(delta.y < 0.0D && !PlusWandUtil.isFluid(level.getBlockState(pos.above()))) {
                    face = Direction.UP;
                }
                return new BlockHitResult(point, face, pos, false);
            }
            if(!state.getCollisionShape(level, pos).isEmpty()) return null;
        }
        return null;
    }

    /**
     * Intersects the line with the single, full cell, the same way the vanilla trace intersects a
     * solid block: the first slab the line crosses is the face that was entered.
     *
     * @return the hit, or {@code null} when the line does not really enter the cell
     */
    @Nullable
    private static BlockHitResult clipCell(BlockPos pos, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double enter = 0.0D;
        double exit = 1.0D;
        Direction face = null;

        // X slab: entering the lower plane is the west face, the upper one the east face.
        if(Math.abs(dx) < EPSILON) {
            if(from.x < pos.getX() || from.x > pos.getX() + 1.0D) return null;
        }
        else {
            double near = (pos.getX() - from.x) / dx;
            double far = (pos.getX() + 1.0D - from.x) / dx;
            Direction direction = Direction.WEST;
            if(near > far) {
                double swap = near;
                near = far;
                far = swap;
                direction = Direction.EAST;
            }
            if(near > enter) {
                enter = near;
                face = direction;
            }
            exit = Math.min(exit, far);
            if(enter > exit) return null;
        }

        // Y slab
        if(Math.abs(dy) < EPSILON) {
            if(from.y < pos.getY() || from.y > pos.getY() + 1.0D) return null;
        }
        else {
            double near = (pos.getY() - from.y) / dy;
            double far = (pos.getY() + 1.0D - from.y) / dy;
            Direction direction = Direction.DOWN;
            if(near > far) {
                double swap = near;
                near = far;
                far = swap;
                direction = Direction.UP;
            }
            if(near > enter) {
                enter = near;
                face = direction;
            }
            exit = Math.min(exit, far);
            if(enter > exit) return null;
        }

        // Z slab
        if(Math.abs(dz) < EPSILON) {
            if(from.z < pos.getZ() || from.z > pos.getZ() + 1.0D) return null;
        }
        else {
            double near = (pos.getZ() - from.z) / dz;
            double far = (pos.getZ() + 1.0D - from.z) / dz;
            Direction direction = Direction.NORTH;
            if(near > far) {
                double swap = near;
                near = far;
                far = swap;
                direction = Direction.SOUTH;
            }
            if(near > enter) {
                enter = near;
                face = direction;
            }
            exit = Math.min(exit, far);
            if(enter > exit) return null;
        }

        // No entry face means the start is inside the cell, which is not a usable target.
        if(face == null) return null;

        Vec3 point = new Vec3(from.x + dx * enter, from.y + dy * enter, from.z + dz * enter);
        return new BlockHitResult(point, face, pos, false);
    }
}
