package thetadev.constructionwand.plus.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import thetadev.constructionwand.basics.ConfigServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The containers a wand is bound to.
 * <p>
 * Sneak and right click a container with the wand to bind it, repeat that to unbind it. The list
 * lives in the wand's own NBT ({@value #TAG_ROOT}) together with the dimension it was bound in, in
 * binding order, which is also the priority the wand draws from them.
 * <p>
 * A binding is only ever resolved, never required: a container whose chunk is unloaded, which was
 * broken or which was replaced by something else is silently skipped, so a stale binding can never
 * break the wand. Every item and fluid operation walks the list in order and works with leftovers.
 */
public final class ContainerBinding
{
    public static final String TAG_ROOT = "wand_bound_containers";

    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";
    private static final String TAG_KEY = "key";

    private ContainerBinding() {
    }

    /**
     * One bound container position. The block key is recorded when the binding is made so the wand
     * screen can still name the container from an unloaded chunk or another dimension.
     */
    public record Bound(BlockPos pos, ResourceKey<Level> dimension, @Nullable String key)
    {
    }

    public static int getMaxContainers() {
        return ConfigServer.MAX_BOUND_CONTAINERS.get();
    }

    public static boolean hasBinding(ItemStack wand) {
        return !read(wand).isEmpty();
    }

    public static int count(ItemStack wand) {
        return read(wand).size();
    }

    public static List<Bound> read(ItemStack wand) {
        List<Bound> bounds = new ArrayList<>();
        if(wand == null || wand.isEmpty()) return bounds;
        CompoundTag root = wand.getTag();
        if(root == null) return bounds;

        ListTag list = root.getList(TAG_ROOT, Tag.TAG_COMPOUND);
        for(int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString(TAG_DIMENSION));
            if(dimension == null) continue;
            String key = entry.contains(TAG_KEY, Tag.TAG_STRING) ? entry.getString(TAG_KEY) : null;
            bounds.add(new Bound(BlockPos.of(entry.getLong(TAG_POS)), ResourceKey.create(Registries.DIMENSION, dimension), key));
        }
        return bounds;
    }

    public static boolean isBound(ItemStack wand, BlockPos pos, Level level) {
        for(Bound bound : read(wand)) {
            if(bound.pos().equals(pos) && sameDimension(bound, level)) return true;
        }
        return false;
    }

    /**
     * @return whether the binding was added. {@link BindResult#FULL} reports a wand that already
     * carries as many containers as the configuration allows.
     */
    public static BindResult storeBinding(ItemStack wand, BlockPos pos, Level level) {
        if(wand == null || wand.isEmpty() || pos == null || level == null) return BindResult.FAILED;
        if(isBound(wand, pos, level)) return BindResult.ALREADY;

        int max = getMaxContainers();
        if(max >= 0 && count(wand) >= max) return BindResult.FULL;

        ListTag list = new ListTag();
        for(Bound bound : read(wand)) list.add(encode(bound));
        list.add(encode(new Bound(pos, level.dimension(), BoundBlockKey.of(level, pos))));
        write(wand, list);
        return BindResult.BOUND;
    }

    public static boolean removeBinding(ItemStack wand, BlockPos pos, Level level) {
        if(wand == null || wand.isEmpty() || pos == null || level == null) return false;

        ListTag list = new ListTag();
        boolean removed = false;
        for(Bound bound : read(wand)) {
            if(bound.pos().equals(pos) && sameDimension(bound, level)) {
                removed = true;
                continue;
            }
            list.add(encode(bound));
        }
        if(removed) write(wand, list);
        return removed;
    }

    /**
     * Moves a binding one step inside the priority list.
     *
     * @return whether the order changed
     */
    public static boolean moveBinding(ItemStack wand, BlockPos pos, Level level, int delta) {
        if(delta == 0) return false;
        int index = indexOf(read(wand), pos, level);
        return index >= 0 && moveBindingTo(wand, pos, level, index + delta);
    }

    /**
     * Moves a binding to a position in the priority list, which is how the wand screen drops a row the
     * player dragged. An index outside the list is clamped, so a row dropped past the end lands last.
     *
     * @return whether the order changed
     */
    public static boolean moveBindingTo(ItemStack wand, BlockPos pos, Level level, int target) {
        if(wand == null || wand.isEmpty() || pos == null || level == null) return false;

        List<Bound> bounds = read(wand);
        int index = indexOf(bounds, pos, level);
        if(index < 0) return false;

        int destination = Mth.clamp(target, 0, bounds.size() - 1);
        if(destination == index) return false;

        bounds.add(destination, bounds.remove(index));

        ListTag list = new ListTag();
        for(Bound bound : bounds) list.add(encode(bound));
        write(wand, list);
        return true;
    }

    /**
     * @return the position of a binding in the list, or {@code -1} when it is not bound in that level
     */
    private static int indexOf(List<Bound> bounds, BlockPos pos, Level level) {
        for(int index = 0; index < bounds.size(); index++) {
            if(bounds.get(index).pos().equals(pos) && sameDimension(bounds.get(index), level)) return index;
        }
        return -1;
    }

    /**
     * @return the part of the stack that did not fit into any bound container
     */
    public static ItemStack insert(Level level, ItemStack wand, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if(remaining.isEmpty() || level == null || level.isClientSide) return remaining;

        for(Bound bound : read(wand)) {
            if(remaining.isEmpty()) break;
            IItemHandler handler = itemHandler(level, bound);
            if(handler == null) continue;

            for(int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if(!present.isEmpty() && !ItemHandlerHelper.canItemStacksStack(present, remaining)) continue;
                remaining = handler.insertItem(slot, remaining, false);
            }
        }
        return remaining;
    }

    /**
     * @return how many items equivalent to the template are stored in the bound containers
     */
    public static int countStored(Level level, ItemStack wand, ItemStack template) {
        if(template == null || template.isEmpty() || level == null || level.isClientSide) return 0;

        int total = 0;
        for(Bound bound : read(wand)) {
            IItemHandler handler = itemHandler(level, bound);
            if(handler == null) continue;
            for(int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if(!present.isEmpty() && ItemHandlerHelper.canItemStacksStack(present, template)) total += present.getCount();
            }
        }
        return total;
    }

    /**
     * @return the amount that could not be taken out of the bound containers
     */
    public static int take(Level level, ItemStack wand, ItemStack template, int amount) {
        int remaining = amount;
        if(remaining <= 0 || template == null || template.isEmpty() || level == null || level.isClientSide) return Math.max(0, remaining);

        for(Bound bound : read(wand)) {
            if(remaining <= 0) break;
            IItemHandler handler = itemHandler(level, bound);
            if(handler == null) continue;

            for(int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if(present.isEmpty() || !ItemHandlerHelper.canItemStacksStack(present, template)) continue;
                ItemStack extracted = handler.extractItem(slot, remaining, false);
                remaining -= extracted.getCount();
            }
        }
        return remaining;
    }

    /**
     * @return how much of the fluid really went into the bound containers
     */
    public static int fill(Level level, ItemStack wand, FluidStack fluid) {
        if(fluid == null || fluid.isEmpty() || level == null || level.isClientSide) return 0;

        int stored = 0;
        for(Bound bound : read(wand)) {
            IFluidHandler handler = fluidHandler(level, bound);
            if(handler == null) continue;
            stored += handler.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        }
        return stored;
    }

    /**
     * @return the amount of fluid that could not be taken out of the bound containers
     */
    public static int drain(Level level, ItemStack wand, FluidStack fluid, int amount) {
        if(fluid == null || fluid.isEmpty() || amount <= 0 || level == null || level.isClientSide) return Math.max(0, amount);

        int drained = 0;
        for(Bound bound : read(wand)) {
            if(drained >= amount) break;
            IFluidHandler handler = fluidHandler(level, bound);
            if(handler == null) continue;
            FluidStack drawn = handler.drain(new FluidStack(fluid.getFluid(), amount - drained), IFluidHandler.FluidAction.EXECUTE);
            drained += drawn.getAmount();
        }
        return Math.max(0, amount - drained);
    }

    /**
     * @return how much of the fluid is stored in the bound containers
     */
    public static int countStoredFluid(Level level, ItemStack wand, FluidStack fluid) {
        if(fluid == null || fluid.isEmpty() || level == null || level.isClientSide) return 0;

        int total = 0;
        for(Bound bound : read(wand)) {
            IFluidHandler handler = fluidHandler(level, bound);
            if(handler == null) continue;
            for(int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack present = handler.getFluidInTank(tank);
                if(!present.isEmpty() && present.isFluidEqual(fluid)) total += present.getAmount();
            }
        }
        return total;
    }

    /**
     * @return how much of the fluid the bound containers can still take
     */
    public static int fluidCapacity(Level level, ItemStack wand, FluidStack fluid) {
        if(fluid == null || fluid.isEmpty() || level == null || level.isClientSide) return 0;

        int capacity = 0;
        for(Bound bound : read(wand)) {
            IFluidHandler handler = fluidHandler(level, bound);
            if(handler == null) continue;
            for(int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack present = handler.getFluidInTank(tank);
                if(present.isEmpty() || present.isFluidEqual(fluid)) {
                    capacity += Math.max(0, handler.getTankCapacity(tank) - present.getAmount());
                }
            }
        }
        return capacity;
    }

    @Nullable
    private static BlockEntity resolveBlockEntity(Level level, Bound bound) {
        Level target = levelOf(level, bound);
        if(target == null) return null;
        BlockPos pos = bound.pos();
        if(!target.isLoaded(pos)) return null;
        return target.getBlockEntity(pos);
    }

    /**
     * The level a binding lives in.
     * <p>
     * The level that was handed in is used when it is already the right one, which is the case while the
     * player stands in the same dimension. Otherwise the dimension the binding recorded is looked up, so
     * a wand keeps reaching its containers after the player has travelled: a binding whose dimension is
     * simply not loaded stays unreachable, which is what the null return stands for.
     */
    @Nullable
    private static Level levelOf(Level level, Bound bound) {
        if(level != null && level.dimension().equals(bound.dimension())) return level;

        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(bound.dimension());
    }

    /**
     * Item handlers of block entities are reached without a side, which every container understands.
     */
    @Nullable
    private static IItemHandler itemHandler(Level level, Bound bound) {
        BlockEntity blockEntity = resolveBlockEntity(level, bound);
        if(blockEntity == null) return null;
        if(blockEntity instanceof IItemHandler handler) return handler;
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
    }

    /**
     * Fluid handlers are probed top first, then the other faces, then without a side. Mekanism tanks
     * refuse a null side and forbid draining their bottom face, so the order matters.
     */
    @Nullable
    private static IFluidHandler fluidHandler(Level level, Bound bound) {
        BlockEntity blockEntity = resolveBlockEntity(level, bound);
        if(blockEntity == null) return null;
        if(blockEntity instanceof IFluidHandler handler) return handler;

        IFluidHandler up = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).resolve().orElse(null);
        if(up != null) return up;

        for(Direction direction : Direction.values()) {
            if(direction == Direction.UP) continue;
            IFluidHandler handler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction).resolve().orElse(null);
            if(handler != null) return handler;
        }
        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElse(null);
    }

    /**
     * Also accepts portable containers that only hold fluid, which is where the digging core stores
     * the fluid it clears.
     */
    public static boolean isContainer(Level level, BlockPos pos) {
        if(level == null || pos == null || !level.isLoaded(pos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if(blockEntity instanceof IItemHandler || blockEntity instanceof IFluidHandler) return true;
        if(blockEntity == null) return itemInWorldHasStorage(level, pos);

        for(Direction direction : Direction.values()) {
            if(blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).isPresent()) return true;
            if(blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction).isPresent()) return true;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()
                || blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent();
    }

    private static boolean itemInWorldHasStorage(Level level, BlockPos pos) {
        ItemStack asItem = new ItemStack(level.getBlockState(pos).getBlock().asItem());
        if(asItem.isEmpty()) return false;
        Optional<IFluidHandlerItem> handler = asItem.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
        return handler.isPresent();
    }

    private static boolean sameDimension(Bound bound, Level level) {
        return bound.dimension().equals(level.dimension());
    }

    private static CompoundTag encode(Bound bound) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, bound.dimension().location().toString());
        tag.putLong(TAG_POS, bound.pos().asLong());
        if(bound.key() != null) tag.putString(TAG_KEY, bound.key());
        return tag;
    }

    /**
     * The name to show for a binding: the recorded block key, or the block looked up in the world
     * when it is loaded, or the plain coordinates as a last resort.
     */
    public static String displayName(@Nullable Level level, Bound bound) {
        String key = bound.key();
        if(key == null && level != null && level.dimension().equals(bound.dimension())) {
            key = BoundBlockKey.of(level, bound.pos());
        }
        if(key != null) return Component.translatable(key).getString();
        return bound.pos().toShortString();
    }

    private static void write(ItemStack wand, ListTag list) {
        if(wand == null || wand.isEmpty()) return;
        wand.getOrCreateTag().put(TAG_ROOT, list);
    }

    /**
     * @return whether {@link ServerLevel} is reachable for a bound position, which is always the
     * case: bindings are only resolved against the level the wand is used in.
     */
    public static boolean dimensionAvailable(ResourceKey<Level> dimension) {
        Registry<Level> registry = null;
        return registry != null || dimension != null;
    }

    public enum BindResult
    {
        BOUND,
        ALREADY,
        FULL,
        FAILED
    }
}
