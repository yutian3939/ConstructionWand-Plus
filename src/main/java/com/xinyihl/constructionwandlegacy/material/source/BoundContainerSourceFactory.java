package com.xinyihl.constructionwandlegacy.material.source;

import com.xinyihl.constructionwandlegacy.basics.BoundBlockKey;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import com.xinyihl.constructionwandlegacy.material.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The containers a wand is bound to, in the order they are used.
 * <p>
 * The list lives on the wand as {@code bound_containers}, first entry first: a placement takes its
 * blocks from the first container that has them, a harvest fills the first one that still has room,
 * and an undo asks for what it handed over in the same order. Sneak and right click a container
 * appends it to the end of the list, the wand screen reorders entries and removes them again.
 * <p>
 * Wands bound before the list existed carry a single {@code bound_container_pos} /
 * {@code bound_container_dim}, which is read as a one entry list. The stock construction core and the
 * storage core share this one binding.
 */
public final class BoundContainerSourceFactory implements MaterialSourceFactory {
    /**
     * How many containers one wand may bind. The configuration value, with -1 or 0 meaning unlimited.
     */
    public static int getMaxContainers() {
        int configured = ModConfig.storage.maxBoundContainers;
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }

    private static final String TAG_LIST = "bound_containers";
    private static final String TAG_ENTRY_POS = "pos";
    private static final String TAG_ENTRY_DIM = "dim";
    private static final String TAG_ENTRY_KEY = "key";

    /**
     * The single binding of older wands, still read so those keep working.
     */
    private static final String TAG_LEGACY_POS = "bound_container_pos";
    private static final String TAG_LEGACY_DIM = "bound_container_dim";
    private static final String TAG_LEGACY_KEY = "bound_container_key";

    /**
     * What adding a binding did.
     */
    public enum BindResult {
        /**
         * The container was not bound yet and was appended, which makes it the lowest priority.
         */
        ADDED,
        /**
         * The container was already bound; the name recorded for it was refreshed, the order is
         * unchanged.
         */
        UPDATED,
        /**
         * The list is full, nothing was written.
         */
        FULL,
        /**
         * The wand could not be written.
         */
        FAILED
    }

    /**
     * One bound container.
     */
    public static final class Bound {
        private final BlockPos pos;
        private final int dimension;
        @Nullable
        private final String blockKey;

        private Bound(BlockPos pos, int dimension, @Nullable String blockKey) {
            this.pos = pos;
            this.dimension = dimension;
            this.blockKey = blockKey;
        }

        public BlockPos getPos() {
            return pos;
        }

        public int getDimension() {
            return dimension;
        }

        /**
         * @return the translation key of this block as recorded when it was bound, or {@code null} for
         * a binding made before that was stored
         */
        @Nullable
        public String getBlockKey() {
            return blockKey;
        }
    }

    /**
     * The bound containers in the order they are used, the first one first.
     */
    public static List<Bound> readBindings(ItemStack wand) {
        NBTTagCompound data = WandDataCodec.readData(wand);
        List<Bound> bindings = readList(data);
        if (bindings.isEmpty()) {
            Bound legacy = readLegacy(data);
            if (legacy != null) {
                bindings.add(legacy);
            }
        }
        return bindings;
    }

    public static boolean hasBinding(ItemStack wand) {
        return !readBindings(wand).isEmpty();
    }

    /**
     * @return whether that container is currently bound to the wand
     */
    public static boolean isBound(ItemStack wand, BlockPos pos, int dimension) {
        return pos != null && indexOf(readBindings(wand), pos, dimension) >= 0;
    }

    /**
     * Binds {@code pos}, or refreshes it when the wand already points at that container. A new
     * container is appended, so the containers bound earlier stay ahead of it.
     */
    public static BindResult storeBinding(ItemStack wand, BlockPos pos, int dimension) {
        if (pos == null) {
            return BindResult.FAILED;
        }
        List<Bound> bindings = readBindings(wand);
        // The block is loaded right here, so its name is recorded while it can still be read. The wand
        // screen falls back to it for a container in an unloaded chunk or another dimension.
        String key = BoundBlockKey.of(DimensionManager.getWorld(dimension), pos);
        int existing = indexOf(bindings, pos, dimension);
        if (existing >= 0) {
            bindings.set(existing, new Bound(pos, dimension, key));
            return writeBindings(wand, bindings) ? BindResult.UPDATED : BindResult.FAILED;
        }
        if (bindings.size() >= getMaxContainers()) {
            return BindResult.FULL;
        }
        bindings.add(new Bound(pos, dimension, key));
        return writeBindings(wand, bindings) ? BindResult.ADDED : BindResult.FAILED;
    }

    /**
     * Moves one container a step towards the front ({@code delta < 0}) or the back of the list, that is
     * up or down in the priority order the wand screen shows.
     */
    public static boolean moveBinding(ItemStack wand, BlockPos pos, int dimension, int delta) {
        if (pos == null || delta == 0) {
            return false;
        }
        List<Bound> bindings = readBindings(wand);
        int index = indexOf(bindings, pos, dimension);
        if (index < 0) {
            return false;
        }
        int target = index + (delta < 0 ? -1 : 1);
        if (target < 0 || target >= bindings.size()) {
            return false;
        }
        bindings.add(target, bindings.remove(index));
        return writeBindings(wand, bindings);
    }

    public static boolean removeBinding(ItemStack wand, BlockPos pos, int dimension) {
        if (pos == null) {
            return false;
        }
        List<Bound> bindings = readBindings(wand);
        int index = indexOf(bindings, pos, dimension);
        if (index < 0) {
            return false;
        }
        bindings.remove(index);
        return writeBindings(wand, bindings);
    }

    /**
     * @return the position of that container in the list, or {@code -1} when it is not bound
     */
    private static int indexOf(List<Bound> bindings, BlockPos pos, int dimension) {
        for (int index = 0; index < bindings.size(); index++) {
            Bound bound = bindings.get(index);
            if (bound.getDimension() == dimension && bound.getPos().equals(pos)) {
                return index;
            }
        }
        return -1;
    }

    private static List<Bound> readList(NBTTagCompound data) {
        List<Bound> bindings = new ArrayList<>();
        NBTTagList list = data.getTagList(TAG_LIST, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount() && bindings.size() < getMaxContainers(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            int[] position = entry.getIntArray(TAG_ENTRY_POS);
            if (position.length < 3 || !entry.hasKey(TAG_ENTRY_DIM)) {
                continue;
            }
            String key = entry.hasKey(TAG_ENTRY_KEY, Constants.NBT.TAG_STRING) ? entry.getString(TAG_ENTRY_KEY) : null;
            bindings.add(new Bound(new BlockPos(position[0], position[1], position[2]), entry.getInteger(TAG_ENTRY_DIM), key));
        }
        return bindings;
    }

    @Nullable
    private static Bound readLegacy(NBTTagCompound data) {
        int[] position = data.getIntArray(TAG_LEGACY_POS);
        if (position.length < 3 || !data.hasKey(TAG_LEGACY_DIM)) {
            return null;
        }
        String key = data.hasKey(TAG_LEGACY_KEY, Constants.NBT.TAG_STRING) ? data.getString(TAG_LEGACY_KEY) : null;
        return new Bound(new BlockPos(position[0], position[1], position[2]), data.getInteger(TAG_LEGACY_DIM), key);
    }

    private static boolean writeBindings(ItemStack wand, List<Bound> bindings) {
        return WandDataCodec.update(wand, data -> {
            NBTTagList list = new NBTTagList();
            for (Bound bound : bindings) {
                NBTTagCompound entry = new NBTTagCompound();
                BlockPos pos = bound.getPos();
                entry.setIntArray(TAG_ENTRY_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
                entry.setInteger(TAG_ENTRY_DIM, bound.getDimension());
                if (bound.getBlockKey() != null) {
                    entry.setString(TAG_ENTRY_KEY, bound.getBlockKey());
                }
                list.appendTag(entry);
            }
            data.setTag(TAG_LIST, list);
            // The list replaced the single binding of older wands, so the old keys must not stay behind
            // and be read again should the list ever be empty.
            data.removeTag(TAG_LEGACY_POS);
            data.removeTag(TAG_LEGACY_DIM);
            data.removeTag(TAG_LEGACY_KEY);
        });
    }

    @Nullable
    @Override
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        List<MaterialSource> sources = new ArrayList<>();
        for (Bound bound : readBindings(wand)) {
            MaterialSource source = createSource(player, bound);
            if (source != null) {
                sources.add(source);
            }
        }
        if (sources.isEmpty()) {
            return null;
        }
        return sources.size() == 1 ? sources.get(0) : new ChainedSource(sources);
    }

    @Nullable
    private static MaterialSource createSource(EntityPlayer player, Bound bound) {
        World world = DimensionManager.getWorld(bound.getDimension());
        if (world == null || !world.isBlockLoaded(bound.getPos())) {
            return null;
        }
        TileEntity tile = world.getTileEntity(bound.getPos());
        if (tile == null || !tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            return null;
        }
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return handler == null ? null : new BoundContainerSource(player, world, bound.getPos(), tile, handler);
    }

    /**
     * The bound containers of one wand as a single pool. Extraction walks them in the stored order, so
     * the first container is emptied before the second one is touched.
     */
    private static final class ChainedSource implements MaterialSource {
        private static final String ID = "bound_containers";

        private final List<MaterialSource> sources;

        private ChainedSource(List<MaterialSource> sources) {
            this.sources = sources;
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        public void enumerate(MaterialCollector collector) {
            for (MaterialSource source : sources) {
                source.enumerate(collector);
            }
        }

        @Override
        public MaterialReceipt extract(MaterialKey key, int count) {
            if (count <= 0) {
                return MaterialReceipt.empty();
            }
            int remaining = count;
            List<MaterialReceipt> receipts = new ArrayList<>();
            for (MaterialSource source : sources) {
                if (remaining <= 0) {
                    break;
                }
                MaterialReceipt receipt = source.extract(key, remaining);
                int taken = receipt.getCount();
                if (taken <= 0) {
                    continue;
                }
                remaining -= taken;
                receipts.add(receipt);
            }
            return MaterialReceipt.combine(receipts);
        }
    }

    private static final class BoundContainerSource implements MaterialSource {
        private static final String ID = "bound_container";

        private final EntityPlayer player;
        private final World world;
        private final BlockPos pos;
        private final TileEntity tile;
        private final IItemHandler handler;
        private final String id;
        private final Map<MaterialKey, List<Integer>> slotsByKey = new LinkedHashMap<>();

        private BoundContainerSource(EntityPlayer player, World world, BlockPos pos, TileEntity tile, IItemHandler handler) {
            this.player = player;
            this.world = world;
            this.pos = pos;
            this.tile = tile;
            this.handler = handler;
            this.id = ID + "@" + world.provider.getDimension() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void enumerate(MaterialCollector collector) {
            slotsByKey.clear();
            if (!isValidEndpoint()) {
                return;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || stack.getCount() <= 0) {
                    continue;
                }
                MaterialKey key = MaterialKey.of(stack);
                slotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
                collector.accept(key, stack.getCount());
            }
        }

        @Override
        public MaterialReceipt extract(MaterialKey key, int count) {
            List<Integer> slots = slotsByKey.get(key);
            if (count <= 0 || slots == null || !isValidEndpoint()) {
                return MaterialReceipt.empty();
            }
            int remaining = count;
            List<MaterialReceipt> receipts = new ArrayList<>();
            for (Integer slot : slots) {
                ItemStack extracted;
                try {
                    if (!isValidEndpoint() || !key.matches(handler.getStackInSlot(slot))) {
                        continue;
                    }
                    extracted = handler.extractItem(slot, remaining, false);
                } catch (RuntimeException exception) {
                    break;
                }
                if (extracted != null && !extracted.isEmpty()) {
                    int extractedCount = Math.min(remaining, extracted.getCount());
                    remaining -= extractedCount;
                    receipts.add(MaterialReceipt.of(id, key, extractedCount, (refundKey, refundCount) -> refund(refundKey, refundCount)));
                }
                if (remaining == 0) {
                    break;
                }
            }
            return MaterialReceipt.combine(receipts);
        }

        private int refund(MaterialKey key, int count) {
            if (!isValidEndpoint()) {
                return InventoryRefunds.refund(player, key, count);
            }
            ItemStack remaining = key.createStack(count);
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                if (!isValidEndpoint()) {
                    break;
                }
                try {
                    ItemStack next = handler.insertItem(slot, remaining, false);
                    remaining = next == null ? remaining : next;
                } catch (RuntimeException exception) {
                    break;
                }
            }
            return remaining.isEmpty() ? 0 : InventoryRefunds.refund(player, key, remaining.getCount());
        }

        private boolean isValidEndpoint() {
            try {
                if (world == null || world.isRemote || !world.isBlockLoaded(pos) || world.getTileEntity(pos) != tile || tile.isInvalid() || !tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                    return false;
                }
                return tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) == handler;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }
}
