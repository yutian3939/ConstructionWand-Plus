package dev.polaris_light.constructionwand.plus.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.basics.WandData;

import javax.annotation.Nullable;

/**
 * The ME network the wand is bound to, for the AE core.
 * <p>
 * Sneak and right click a controller to bind the wand to its network, and do it again to unbind. Only
 * the position and the dimension are stored, on the wand itself, and every operation resolves that
 * endpoint again: the endpoint is looked up in its own dimension through the running server, so the
 * wand reaches the network from any distance and from any other dimension, which is what the 1.12.2
 * version did and what the terminal in the inventory cannot do. A binding whose chunk is not loaded,
 * whose controller was broken or whose grid is gone resolves to nothing and is skipped silently, so a
 * stale binding can never break the wand.
 * <p>
 * Every AE2 class is touched by the nested provider only, and only after {@link #isLoaded()} checked
 * that the mod is present.
 */
public final class AeGridAccess
{
    private static final String TAG_ROOT = "wand_ae_binding";
    private static final String TAG_POS = "pos";
    private static final String TAG_DIMENSION = "dimension";

    /**
     * Amount a capacity probe asks for, in mB. Far above what one wand operation can clear.
     */
    private static final int CAPACITY_PROBE = 1_000_000;

    private AeGridAccess() {
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
        CompoundTag binding = WandData.readRoot(wand, TAG_ROOT);
        if(binding == null || !binding.contains(TAG_POS, Tag.TAG_LONG)) return null;

        ResourceLocation dimension = ResourceLocation.tryParse(binding.getString(TAG_DIMENSION));
        if(dimension == null) return null;
        return new Bound(BlockPos.of(binding.getLong(TAG_POS)), ResourceKey.create(Registries.DIMENSION, dimension));
    }

    public static boolean hasBinding(ItemStack wand) {
        return readBinding(wand) != null;
    }

    public static void writeBinding(ItemStack wand, BlockPos pos, Level level) {
        if(wand == null || wand.isEmpty() || pos == null || level == null) return;

        CompoundTag binding = new CompoundTag();
        binding.putLong(TAG_POS, pos.asLong());
        binding.putString(TAG_DIMENSION, level.dimension().location().toString());
        WandData.writeRoot(wand, TAG_ROOT, binding);
    }

    public static void clearBinding(ItemStack wand) {
        WandData.removeRoot(wand, TAG_ROOT);
    }

    /**
     * @return whether the wand is bound to exactly that block
     */
    public static boolean isBoundAt(ItemStack wand, BlockPos pos, Level level) {
        Bound bound = readBinding(wand);
        return bound != null && pos != null && level != null
                && bound.pos().equals(pos) && bound.dimension().equals(level.dimension());
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
    public static int count(Player player, ItemStack wand, ItemStack template) {
        if(!isLoaded() || template == null || template.isEmpty()) return 0;
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return 0;
            return saturate(Provider.count(level, player, wand, template));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken out of the bound network
     */
    public static int take(Player player, ItemStack wand, ItemStack template, int amount) {
        if(!isLoaded() || amount <= 0 || template == null || template.isEmpty()) return Math.max(0, amount);
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return amount;
            int taken = saturate(Provider.take(level, player, wand, template, amount));
            return Math.max(0, amount - taken);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    /**
     * @return the part of the stack the bound network did not take
     */
    public static ItemStack insert(Player player, ItemStack wand, ItemStack stack) {
        if(!isLoaded() || stack == null || stack.isEmpty()) return stack;
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return stack;
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

    /**
     * @return how much more of that fluid the bound network can take, in mB
     */
    public static int fluidCapacity(Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return 0;
            return saturate(Provider.fluidCapacity(level, player, wand, fluid, CAPACITY_PROBE));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return how much of that fluid really went into the bound network, in mB
     */
    public static int depositFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return 0;
            return saturate(Provider.depositFluid(level, player, wand, fluid));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return the amount of fluid that could not be taken out of the bound network
     */
    public static int withdrawFluid(Player player, ItemStack wand, FluidStack fluid, int amount) {
        if(!isLoaded() || amount <= 0 || fluid == null || fluid.isEmpty()) return Math.max(0, amount);
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return amount;
            int taken = saturate(Provider.withdrawFluid(level, player, wand, fluid, amount));
            return Math.max(0, amount - taken);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    /**
     * @return how much of that fluid the bound network holds, in mB
     */
    public static int countFluid(Player player, ItemStack wand, FluidStack fluid) {
        if(!isLoaded() || fluid == null || fluid.isEmpty()) return 0;
        try {
            Level level = levelOf(player, readBinding(wand));
            if(level == null) return 0;
            return saturate(Provider.countFluid(level, player, wand, fluid));
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * The dimension a binding lives in.
     * <p>
     * The level the player is in is used when it is already the right one; otherwise the recorded
     * dimension is looked up on the running server. That is what makes the binding reach across
     * dimensions and over any distance, and a dimension that is simply not loaded stays unreachable,
     * which is what the null return stands for.
     */
    @Nullable
    private static Level levelOf(@Nullable Player player, @Nullable Bound bound) {
        if(bound == null) return null;
        Level current = player == null ? null : player.level();
        if(current != null && current.dimension().equals(bound.dimension())) return current;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(bound.dimension());
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
        private static long lastNote;
        private static String lastReason;

        private Provider() {
        }

        /**
         * Says why a bound network could not be used. Debug level, and at most one line per second for the
         * same reason, so a wand that is used every tick cannot flood the log. This is the only trace a
         * silent failure leaves, and a binding is meant to fail silently.
         */
        private static void note(String reason) {
            if(!ConstructionWand.LOGGER.isDebugEnabled()) return;

            long now = System.currentTimeMillis();
            if(reason.equals(lastReason) && now - lastNote < 1000L) return;
            lastNote = now;
            lastReason = reason;
            ConstructionWand.LOGGER.debug("Bound ME network is out of reach: {}", reason);
        }

        /**
         * The grid node of the bound block.
         * <p>
         * Three ways are tried, from the most direct to the most specific:
         * <ol>
         * <li>the main node of the block entity, when it is one of AE2's networked block entities. That is
         * the one to use: {@code IInWorldGridNodeHost#getGridNode(Direction)} only answers with a node
         * that is <em>exposed</em> on that exact side, so a controller with no cable attached to the face
         * that happens to be asked about is refused, and a null side, which is what the 1.12.2 version
         * asked for, is refused by that implementation as well;</li>
         * <li>the node host with a null side, for hosts of other kinds that still answer for it;</li>
         * <li>the node host side by side, which finds the main node of a machine and the centre node of a
         * cable bus alike.</li>
         * </ol>
         */
        @Nullable
        private static appeng.api.networking.IGridNode gridNode(Level level, ItemStack wand) {
            Bound bound = readBinding(wand);
            if(bound == null || !level.dimension().equals(bound.dimension())) {
                note("the binding belongs to another dimension");
                return null;
            }
            if(!level.isLoaded(bound.pos())) {
                note("the chunk of the bound controller is not loaded");
                return null;
            }

            appeng.api.networking.IInWorldGridNodeHost host = nodeHost(level, bound.pos());
            if(host == null) {
                note("the bound block is not an ME network host any more");
                return null;
            }

            if(host instanceof appeng.me.helpers.IGridConnectedBlockEntity connected) {
                appeng.api.networking.IGridNode node = connected.getMainNode().getNode();
                if(node != null) return node;
            }

            try {
                appeng.api.networking.IGridNode node = host.getGridNode(null);
                if(node != null) return node;
            } catch(RuntimeException ignored) {
                // A host that does not answer for a null side; the sides are tried below.
            }

            for(Direction direction : Direction.values()) {
                try {
                    appeng.api.networking.IGridNode node = host.getGridNode(direction);
                    if(node != null) return node;
                } catch(RuntimeException ignored) {
                    // A side this block does not answer for; the next one may work.
                }
            }

            note("the bound block has no grid node yet");
            return null;
        }

        /**
         * @return the node host of that block, through the capability AE2 itself uses, or straight off the
         * block entity when that capability is not registered for its type
         */
        @Nullable
        private static appeng.api.networking.IInWorldGridNodeHost nodeHost(Level level, BlockPos pos) {
            appeng.api.networking.IInWorldGridNodeHost host =
                    appeng.api.networking.GridHelper.getNodeHost(level, pos);
            if(host != null) return host;

            net.minecraft.world.level.block.entity.BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof appeng.api.networking.IInWorldGridNodeHost direct ? direct : null;
        }

        @Nullable
        private static appeng.api.storage.MEStorage storage(Level level, ItemStack wand) {
            appeng.api.networking.IGridNode node = gridNode(level, wand);
            if(node == null) return null;
            appeng.api.networking.IGrid grid = node.getGrid();
            if(grid == null) {
                note("the grid of the bound controller is not up");
                return null;
            }
            return grid.getStorageService().getInventory();
        }

        private static appeng.api.networking.security.IActionSource source(Player player) {
            return player == null ? appeng.api.networking.security.IActionSource.empty()
                    : appeng.api.networking.security.IActionSource.ofPlayer(player);
        }

        static boolean isController(Level level, BlockPos pos) {
            return nodeHost(level, pos) != null;
        }

        static long count(Level level, Player player, ItemStack wand, ItemStack template) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(template);
            if(key == null) return 0;
            return storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, source(player));
        }

        static long take(Level level, Player player, ItemStack wand, ItemStack template, int amount) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

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

        /**
         * {@code MEStorage#insert} answers with the amount it accepted, so a simulate call with a large
         * amount is exactly the room the network still has left.
         */
        static long fluidCapacity(Level level, Player player, ItemStack wand, FluidStack fluid, int probe) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            long accepted = storage.insert(key, droplets(probe), appeng.api.config.Actionable.SIMULATE, source(player));
            return milliBuckets(accepted);
        }

        static long depositFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            long inserted = storage.insert(key, droplets(fluid.getAmount()),
                    appeng.api.config.Actionable.MODULATE, source(player));
            return milliBuckets(inserted);
        }

        static long withdrawFluid(Level level, Player player, ItemStack wand, FluidStack fluid, int amount) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            long taken = storage.extract(key, droplets(amount), appeng.api.config.Actionable.MODULATE, source(player));
            return milliBuckets(taken);
        }

        static long countFluid(Level level, Player player, ItemStack wand, FluidStack fluid) {
            appeng.api.storage.MEStorage storage = storage(level, wand);
            if(storage == null) return 0;

            appeng.api.stacks.AEFluidKey key = appeng.api.stacks.AEFluidKey.of(fluid);
            if(key == null) return 0;
            long droplets = storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, source(player));
            return milliBuckets(droplets);
        }

        /**
         * The network counts fluids in droplets, one bucket being {@code AMOUNT_BUCKET} of them, while a
         * {@link FluidStack} counts in mB, one bucket being {@code FluidType.BUCKET_VOLUME}. Both
         * constants take part in the conversion instead of assuming they happen to match.
         */
        private static long droplets(int milliBuckets) {
            return (long) milliBuckets * appeng.api.stacks.AEFluidKey.AMOUNT_BUCKET
                    / net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME;
        }

        private static long milliBuckets(long droplets) {
            return droplets * net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME
                    / appeng.api.stacks.AEFluidKey.AMOUNT_BUCKET;
        }
    }
}
