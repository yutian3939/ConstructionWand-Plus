package com.xinyihl.morewandcore.material;

import com.xinyihl.constructionwandlegacy.material.MaterialCollector;
import com.xinyihl.constructionwandlegacy.material.MaterialKey;
import com.xinyihl.constructionwandlegacy.material.MaterialReceipt;
import com.xinyihl.constructionwandlegacy.material.MaterialSource;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import com.xinyihl.constructionwandlegacy.material.source.PlayerInventorySourceFactory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Material source of the storage core: the bound containers are used first, in the order the
 * configuration screen shows them, and the player inventory is the fallback. Without a binding it
 * degrades to a plain inventory wand.
 */
public final class StorageMaterialSourceFactory implements MaterialSourceFactory {
    public static final StorageMaterialSourceFactory INSTANCE = new StorageMaterialSourceFactory();

    private final MaterialSourceFactory container = new BoundContainerSourceFactory();
    private final MaterialSourceFactory inventory = new PlayerInventorySourceFactory();

    private StorageMaterialSourceFactory() {
    }

    @Nullable
    @Override
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        MaterialSource bound = container.create(player, wand);
        MaterialSource carried = inventory.create(player, wand);
        if (bound == null) {
            return carried;
        }
        if (carried == null) {
            return bound;
        }
        return new PrioritizedSource(bound, carried);
    }

    /**
     * Splits one extraction across the primary and the fallback store while staying a single
     * {@link MaterialSource}, so the planner keeps treating the pair as one pool.
     */
    private static final class PrioritizedSource implements MaterialSource {
        private static final String ID = "storage_bound_container";

        private final MaterialSource primary;
        private final MaterialSource fallback;

        private PrioritizedSource(MaterialSource primary, MaterialSource fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        public void enumerate(MaterialCollector collector) {
            primary.enumerate(collector);
            fallback.enumerate(collector);
        }

        @Override
        public MaterialReceipt extract(MaterialKey key, int count) {
            MaterialReceipt first = primary.extract(key, count);
            int taken = first.getCount();
            if (taken >= count) {
                return first;
            }
            MaterialReceipt second = fallback.extract(key, count - taken);
            if (taken <= 0) {
                return second;
            }
            if (second.getCount() <= 0) {
                return first;
            }
            return MaterialReceipt.combine(Arrays.asList(first, second));
        }
    }
}
