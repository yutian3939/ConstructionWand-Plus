package dev.polaris_light.constructionwand.plus.compat.immersiveengineering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.polaris_light.constructionwand.plus.basics.TimeTarget;
import dev.polaris_light.constructionwand.plus.compat.SoftReflection;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Finds the block of an Immersive Engineering multiblock that does the ticking, and the box of the machine
 * around it, for a click on any of its blocks.
 * <p>
 * A multiblock is a grid of {@code MultiblockPartBlock}s. Only the one at the master position is a
 * {@code MultiblockBlockEntityMaster} and is given a ticker; every other block is a
 * {@code MultiblockBlockEntityDummy}, and {@code MultiblockPartBlock#getTicker} answers {@code null} for
 * them. A dummy therefore looks like a block that cannot be accelerated at all, even though the whole
 * machine would speed up if the master were clicked — and the master is usually a block in the middle of
 * the structure, covered by the parts that make up the machine.
 * <p>
 * The way back is the context a dummy borrows from its master:
 * {@code IMultiblockBEHelper#getContext()} → {@code IMultiblockContext#getLevel()} (which knows where the
 * structure stands and how it is turned) → {@code toAbsolute(masterPosInMB)}. Nothing about the
 * orientation is worked out here, which is what keeps this correct for mirrored and rotated machines; the
 * two opposite corners of the template, turned the same way, give the box of the machine.
 */
public final class ImmersiveEngineeringTimeTarget
{
    private static final String PART = "blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE";
    private static final String HELPER = "blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelper";
    private static final String MASTER_HELPER = "blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelperMaster";
    private static final String CONTEXT = "blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext";
    private static final String LEVEL = "blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockLevel";
    private static final String REGISTRATION = "blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration";

    private static final Class<?> PART_BLOCK_ENTITY = SoftReflection.type(PART);
    private static final Class<?> PART_HELPER = SoftReflection.type(HELPER);
    private static final Class<?> MASTER_PART_HELPER = SoftReflection.type(MASTER_HELPER);
    private static final Class<?> MULTIBLOCK_CONTEXT = SoftReflection.type(CONTEXT);
    private static final Class<?> MULTIBLOCK_LEVEL = SoftReflection.type(LEVEL);
    private static final Class<?> MULTIBLOCK_REGISTRATION = SoftReflection.type(REGISTRATION);

    private static final Method GET_HELPER = SoftReflection.method(PART_BLOCK_ENTITY, "getHelper");
    private static final Method GET_CONTEXT = SoftReflection.method(PART_HELPER, "getContext");
    private static final Method GET_MULTIBLOCK = SoftReflection.method(PART_HELPER, "getMultiblock");
    private static final Method GET_LEVEL = SoftReflection.method(MULTIBLOCK_CONTEXT, "getLevel");
    private static final Method TO_ABSOLUTE = SoftReflection.method(MULTIBLOCK_LEVEL, "toAbsolute", BlockPos.class);
    private static final Method MASTER_POS = SoftReflection.method(MULTIBLOCK_REGISTRATION, "masterPosInMB");
    private static final Method SIZE = SoftReflection.method(MULTIBLOCK_REGISTRATION, "size",
            net.minecraft.world.level.Level.class);

    /**
     * Every class and method this reader needs has to be there, or the API is not the one it was written
     * against.
     */
    private static final boolean SUPPORTED = PART_BLOCK_ENTITY != null && PART_HELPER != null
            && MASTER_PART_HELPER != null && MULTIBLOCK_CONTEXT != null && MULTIBLOCK_LEVEL != null
            && MULTIBLOCK_REGISTRATION != null && GET_HELPER != null && GET_CONTEXT != null
            && GET_MULTIBLOCK != null && GET_LEVEL != null && TO_ABSOLUTE != null && MASTER_POS != null
            && SIZE != null;

    private ImmersiveEngineeringTimeTarget() {
    }

    /**
     * @return the block that ticks the multiblock the given block is part of and the box of that machine,
     * or {@code null} when this block is not part of one
     */
    @Nullable
    public static TimeTarget target(BlockEntity blockEntity) {
        if(!SUPPORTED || !PART_BLOCK_ENTITY.isInstance(blockEntity)) return null;

        Object helper = SoftReflection.call(GET_HELPER, blockEntity);
        if(helper == null) return null;
        Object registration = SoftReflection.call(GET_MULTIBLOCK, helper);
        if(registration == null) return null;

        // The master is the block that already ticks, so there is nothing to look up for it.
        BlockPos master = MASTER_PART_HELPER.isInstance(helper)
                ? blockEntity.getBlockPos()
                : masterOf(blockEntity, helper, registration);
        if(master == null) return null;

        TimeTarget box = boxOf(blockEntity, helper, registration, master);
        return box != null ? box : TimeTarget.of(master);
    }

    /**
     * @return where the master of this multiblock stands, or {@code null} when the structure cannot be
     * placed (which is what an unformed machine looks like)
     */
    @Nullable
    private static BlockPos masterOf(BlockEntity blockEntity, Object helper, Object registration) {
        Object level = levelOf(helper);
        BlockPos masterInMultiblock = SoftReflection.call(MASTER_POS, registration);
        if(level == null || masterInMultiblock == null) return null;

        BlockPos master = SoftReflection.call(TO_ABSOLUTE, level, masterInMultiblock);
        // A master that answers with the block that was clicked is no move at all.
        return master == null || master.equals(blockEntity.getBlockPos()) ? null : master;
    }

    /**
     * @return the box of the whole machine, from the two opposite corners of its template turned into the
     * world the same way the master was
     */
    @Nullable
    private static TimeTarget boxOf(BlockEntity blockEntity, Object helper, Object registration, BlockPos master) {
        Object level = levelOf(helper);
        Object size = SoftReflection.call(SIZE, registration, blockEntity.getLevel());
        if(level == null || !(size instanceof Vec3i extents)) return null;

        BlockPos first = SoftReflection.call(TO_ABSOLUTE, level, BlockPos.ZERO);
        BlockPos second = SoftReflection.call(TO_ABSOLUTE, level,
                new BlockPos(extents.getX() - 1, extents.getY() - 1, extents.getZ() - 1));
        return first == null || second == null ? null : TimeTarget.of(master, first, second);
    }

    /**
     * @return the multiblock level of the machine, which is where its position and orientation live
     */
    @Nullable
    private static Object levelOf(Object helper) {
        Object context = SoftReflection.call(GET_CONTEXT, helper);
        return context == null ? null : SoftReflection.call(GET_LEVEL, context);
    }
}
