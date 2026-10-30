package thetadev.constructionwand.plus.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * ME network supply for the AE core.
 * <p>
 * The wand stores the controller it was bound to (position and dimension) in its own NBT. Every
 * operation resolves that endpoint again and silently does nothing when the controller is gone, its
 * chunk is unloaded or the grid is not reachable, so a stale binding can never break the wand.
 * <p>
 * All AE2 classes are touched by the nested provider only, and only after {@link #isLoaded()} checked
 * that the mod is present.
 */
public final class AE2Compat
{
    private static final String TAG_ROOT = "wand_ae_binding";
    private static final String TAG_POS = "pos";
    private static final String TAG_DIMENSION = "dimension";

    private AE2Compat() {
    }

    public static boolean isLoaded() {
        if(ModList.get() == null) return false;
        return ModList.get().isLoaded("ae2") || ModList.get().isLoaded("appliedenergistics2");
    }

    public record Bound(BlockPos pos, ResourceKey<Level> dimension)
    {
    }

    @Nullable
    public static Bound readBinding(ItemStack wand) {
        if(wand == null || wand.isEmpty()) return null;
        CompoundTag root = wand.getTag();
        if(root == null || !root.contains(TAG_ROOT, Tag.TAG_COMPOUND)) return null;

        CompoundTag binding = root.getCompound(TAG_ROOT);
        ResourceLocation dimension = ResourceLocation.tryParse(binding.getString(TAG_DIMENSION));
        if(dimension == null || !binding.contains(TAG_POS, Tag.TAG_LONG)) return null;
        return new Bound(BlockPos.of(binding.getLong(TAG_POS)), ResourceKey.create(Registries.DIMENSION, dimension));
    }

    public static boolean hasBinding(ItemStack wand) {
        return readBinding(wand) != null;
    }

    public static void writeBinding(ItemStack wand, BlockPos pos, Level level) {
        CompoundTag binding = new CompoundTag();
        binding.putLong(TAG_POS, pos.asLong());
        binding.putString(TAG_DIMENSION, level.dimension().location().toString());
        wand.getOrCreateTag().put(TAG_ROOT, binding);
    }

    /**
     * @return whether the block at that position offers an ME network, so it can be bound
     */
    public static boolean isController(Level level, BlockPos pos) {
        if(!isLoaded() || level == null || pos == null || !level.isLoaded(pos)) return false;
        try {
            return Provider.isController(level, pos);
        } catch(RuntimeException | LinkageError error) {
            return false;
        }
    }

    /**
     * @return how many items equivalent to the template the bound network holds
     */
    public static int count(Level level, Player player, ItemStack wand, ItemStack template) {
        if(!isLoaded()) return 0;
        try {
            return saturate(Provider.count(level, player, wand, template));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken out of the network
     */
    public static int take(Level level, Player player, ItemStack wand, ItemStack template, int amount) {
        if(!isLoaded() || amount <= 0) return Math.max(0, amount);
        try {
            int taken = saturate(Provider.take(level, player, wand, template, amount));
            return Math.max(0, amount - taken);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    /**
     * @return the part of the stack that did not fit into the network
     */
    public static ItemStack insert(Level level, Player player, ItemStack wand, ItemStack stack) {
        if(!isLoaded() || stack.isEmpty()) return stack;
        try {
            long inserted = Provider.insert(level, player, wand, stack);
            if(inserted <= 0) return stack;
            if(inserted >= stack.getCount()) return ItemStack.EMPTY;

            ItemStack leftover = stack.copy();
            leftover.setCount(stack.getCount() - (int) inserted);
            return leftover;
        } catch(RuntimeException | LinkageError error) {
            return stack;
        }
    }

    public static int fluidCapacity(Level level, Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            return saturate(Provider.fluidCapacity(level, player, wand, fluid));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    public static int depositFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            return saturate(Provider.depositFluid(level, player, wand, fluid));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return the amount of fluid that could not be taken out of the network
     */
    public static int withdrawFluid(Level level, Player player, ItemStack wand, FluidStack fluid, int amount) {
        if(!isLoaded() || amount <= 0 || fluid == null || fluid.isEmpty()) return Math.max(0, amount);
        try {
            int taken = saturate(Provider.withdrawFluid(level, player, wand, fluid, amount));
            return Math.max(0, amount - taken);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    public static int countFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            return saturate(Provider.countFluid(level, player, wand, fluid));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * The dimension of a wand binding is only reachable while the wand is used in it.
     */
    static boolean sameDimension(Bound bound, Level level) {
        return bound != null && level != null && bound.dimension().equals(level.dimension());
    }

    private static int saturate(long value) {
        if(value <= 0) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * Everything that touches the AE2 API. Loaded lazily, so the wand works without AE2 installed.
     */
    private static final class Provider
    {
        private Provider() {
        }

        /**
         * The grid node of the bound block.
         * <p>
         * The node can not be asked for with a null side: the machines of the network only answer for
         * the sides their node is exposed on and reject a null, which made every request to a bound
         * drive or chest fail while a bound cable or controller kept working. The sides are tried one
         * after another instead, which finds the main node of a machine and the centre node of a cable
         * bus (a cable, a controller) alike.
         */
        @Nullable
        private static appeng.api.networking.IGridNode gridNode(Level level, ItemStack wand) {
            Bound bound = readBinding(wand);
            if(!sameDimension(bound, level)) return null;
            if(!level.isLoaded(bound.pos())) return null;

            appeng.api.networking.IInWorldGridNodeHost host = appeng.api.networking.GridHelper.getNodeHost(level, bound.pos());
            if(host == null) return null;

            for(Direction direction : Direction.values()) {
                try {
                    appeng.api.networking.IGridNode node = host.getGridNode(direction);
                    if(node != null) return node;
                } catch(RuntimeException ignored) {
                    // A side this block does not answer for, the next one may work.
                }
            }
            return null;
        }

        @Nullable
        private static appeng.api.storage.MEStorage storage(Level level, ItemStack wand) {
            appeng.api.networking.IGridNode node = gridNode(level, wand);
            if(node == null) return null;
            appeng.api.networking.IGrid grid = node.getGrid();
            if(grid == null) return null;
            return grid.getStorageService().getInventory();
        }

        private static appeng.api.networking.security.IActionSource source(Player player) {
            return player == null ? appeng.api.networking.security.IActionSource.empty()
                    : appeng.api.networking.security.IActionSource.ofPlayer(player);
        }

        static boolean isController(Level level, BlockPos pos) {
            return appeng.api.networking.GridHelper.getNodeHost(level, pos) != null;
        }

        static long count(Level level, Player player, ItemStack wand, ItemStack template) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null || template.isEmpty()) return 0;

            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(template);
            if(key == null) return 0;
            return storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, source(player));
        }

        static long take(Level level, Player player, ItemStack wand, ItemStack template, int amount) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null || template.isEmpty()) return 0;

            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(template);
            if(key == null) return 0;
            return storage.extract(key, amount, appeng.api.config.Actionable.MODULATE, source(player));
        }

        static long insert(Level level, Player player, ItemStack wand, ItemStack stack) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(stack);
            if(key == null) return 0;
            return storage.insert(key, stack.getCount(), appeng.api.config.Actionable.MODULATE, source(player));
        }

        static long fluidCapacity(Level level, Player player, ItemStack wand, FluidStack fluid) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            long rejected = storage.insert(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, source(player));
            return Long.MAX_VALUE - rejected;
        }

        static long depositFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            return storage.insert(key, fluid.getAmount(), appeng.api.config.Actionable.MODULATE, source(player));
        }

        static long withdrawFluid(Level level, Player player, ItemStack wand, FluidStack fluid, int amount) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            return storage.extract(key, amount, appeng.api.config.Actionable.MODULATE, source(player));
        }

        static long countFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            return storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, source(player));
        }
    }
}
