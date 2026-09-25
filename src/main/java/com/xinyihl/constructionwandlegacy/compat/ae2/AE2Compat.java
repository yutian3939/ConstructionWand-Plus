package com.xinyihl.constructionwandlegacy.compat.ae2;

import appeng.tile.networking.TileController;
import com.xinyihl.constructionwandlegacy.basics.BoundBlockKey;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;

public final class AE2Compat {
    public static final String AE2_MOD_ID = "appliedenergistics2";

    private static final String TAG_BOUND_POS = "ae_bound_pos";
    private static final String TAG_BOUND_DIM = "ae_bound_dim";
    private static final String TAG_BOUND_KEY = "ae_bound_key";

    private AE2Compat() {
    }

    public static MaterialSourceFactory materialSourceFactory() {
        return AE2Provider.MATERIAL_SOURCE_FACTORY;
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static boolean tryBind(ItemStack wand, EntityPlayer player, World world, BlockPos pos) {
        if (wand == null || wand.isEmpty() || player == null || world == null || pos == null) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileController)) {
            return false;
        }
        // The controller is loaded right here, so its name is recorded while it can still be read.
        String key = BoundBlockKey.of(world, pos);
        WandDataCodec.update(wand, data -> {
            data.setIntArray(TAG_BOUND_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            data.setInteger(TAG_BOUND_DIM, world.provider.getDimension());
            if (key == null) {
                data.removeTag(TAG_BOUND_KEY);
            } else {
                data.setString(TAG_BOUND_KEY, key);
            }
        });
        return true;
    }

    public static boolean hasBinding(ItemStack wand) {
        NBTTagCompound data = WandDataCodec.readData(wand);
        return data.hasKey(TAG_BOUND_POS) && data.hasKey(TAG_BOUND_DIM);
    }

    /**
     * @return the part that did not fit into the bound ME network
     */
    public static ItemStack deposit(ItemStack wand, EntityPlayer player, ItemStack stack) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.deposit(wand, player, stack) : stack;
    }

    public static int countStored(ItemStack wand, EntityPlayer player, ItemStack template) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.count(wand, player, template) : 0;
    }

    /**
     * @return the amount that could not be taken from the bound ME network
     */
    public static int withdraw(ItemStack wand, EntityPlayer player, ItemStack template, int amount) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.withdraw(wand, player, template, amount) : amount;
    }

    /**
     * How much of that fluid the bound ME network can still take, asked of its fluid storage cells.
     */
    public static int fluidCapacity(ItemStack wand, EntityPlayer player, FluidStack fluid) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.fluidCapacity(wand, player, fluid) : 0;
    }

    /**
     * @return the amount that was stored, in mB
     */
    public static int depositFluid(ItemStack wand, EntityPlayer player, FluidStack fluid) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.depositFluid(wand, player, fluid) : 0;
    }

    /**
     * @return the amount that could not be taken, in mB
     */
    public static int withdrawFluid(ItemStack wand, EntityPlayer player, FluidStack fluid, int amount) {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.withdrawFluid(wand, player, fluid, amount) : amount;
    }

    @Nullable
    public static Binding readBinding(ItemStack wand) {
        NBTTagCompound data = WandDataCodec.readData(wand);
        int[] position = data.getIntArray(TAG_BOUND_POS);
        if (position.length < 3 || !data.hasKey(TAG_BOUND_DIM)) {
            return null;
        }
        String blockKey = data.hasKey(TAG_BOUND_KEY, Constants.NBT.TAG_STRING) ? data.getString(TAG_BOUND_KEY) : null;
        return new Binding(new BlockPos(position[0], position[1], position[2]), data.getInteger(TAG_BOUND_DIM), blockKey);
    }

    public static final class Binding {
        private final BlockPos position;
        private final int dimension;
        @Nullable
        private final String blockKey;

        private Binding(BlockPos position, int dimension, @Nullable String blockKey) {
            this.position = position;
            this.dimension = dimension;
            this.blockKey = blockKey;
        }

        public BlockPos getPosition() {
            return position;
        }

        public int getDimension() {
            return dimension;
        }

        /**
         * @return the translation key of the bound controller as recorded when it was bound, or
         * {@code null} for a binding made before that was stored
         */
        @Nullable
        public String getBlockKey() {
            return blockKey;
        }
    }

    private static final class AE2Provider {
        private static final MaterialSourceFactory MATERIAL_SOURCE_FACTORY = new AE2MaterialSourceFactory();

        private static ItemStack deposit(ItemStack wand, EntityPlayer player, ItemStack stack) {
            return AE2MaterialSourceFactory.deposit(wand, player, stack);
        }

        private static int count(ItemStack wand, EntityPlayer player, ItemStack template) {
            return AE2MaterialSourceFactory.countStored(wand, player, template);
        }

        private static int withdraw(ItemStack wand, EntityPlayer player, ItemStack template, int amount) {
            return AE2MaterialSourceFactory.withdraw(wand, player, template, amount);
        }

        private static int fluidCapacity(ItemStack wand, EntityPlayer player, FluidStack fluid) {
            return AE2MaterialSourceFactory.fluidCapacity(wand, player, fluid);
        }

        private static int depositFluid(ItemStack wand, EntityPlayer player, FluidStack fluid) {
            return AE2MaterialSourceFactory.depositFluid(wand, player, fluid);
        }

        private static int withdrawFluid(ItemStack wand, EntityPlayer player, FluidStack fluid, int amount) {
            return AE2MaterialSourceFactory.withdrawFluid(wand, player, fluid, amount);
        }
    }
}
