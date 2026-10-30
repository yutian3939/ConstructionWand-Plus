package dev.polaris_light.constructionwand.containers.handlers;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Fluid access to an ME network, shared by the terminal and the portable cell handler.
 * <p>
 * The network holds fluids in the same storage as items, so the same powered calls apply; only the
 * amount unit differs. An {@link AEFluidKey} counts in droplets where one bucket is
 * {@link AEFluidKey#AMOUNT_BUCKET} of them, while a {@link FluidStack} counts in mB where one bucket is
 * {@link FluidType#BUCKET_VOLUME}. Both are converted here instead of assuming they happen to match.
 */
final class AeFluidStorage
{
    /**
     * Amount a capacity probe asks for, in mB. Far above what one wand operation can clear (the largest
     * block limit of any wand is a few thousand), so the answer is the room the network really has.
     */
    private static final int CAPACITY_PROBE = 1_000_000;

    private AeFluidStorage() {
    }

    static int count(MEStorage storage, FluidStack fluid) {
        AEFluidKey key = AEFluidKey.of(fluid);
        if(key == null) return 0;

        long droplets = 0;
        for(var entry : storage.getAvailableStacks()) {
            if(entry.getKey().equals(key)) {
                droplets = entry.getLongValue();
                break;
            }
        }
        return milliBuckets(droplets);
    }

    static int capacity(IEnergySource host, MEStorage storage, IActionSource source, FluidStack fluid) {
        AEFluidKey key = AEFluidKey.of(fluid);
        if(key == null) return 0;

        long accepted = StorageHelper.poweredInsert(host, storage, key, droplets(CAPACITY_PROBE), source, Actionable.SIMULATE);
        return milliBuckets(accepted);
    }

    static int insert(IEnergySource host, MEStorage storage, IActionSource source, FluidStack fluid) {
        AEFluidKey key = AEFluidKey.of(fluid);
        if(key == null) return 0;

        long inserted = StorageHelper.poweredInsert(host, storage, key, droplets(fluid.getAmount()), source, Actionable.MODULATE);
        return milliBuckets(inserted);
    }

    static int extract(IEnergySource host, MEStorage storage, IActionSource source, FluidStack fluid, int amount) {
        AEFluidKey key = AEFluidKey.of(fluid);
        if(key == null) return 0;

        long taken = StorageHelper.poweredExtraction(host, storage, key, droplets(amount), source, Actionable.MODULATE);
        return milliBuckets(taken);
    }

    private static long droplets(int milliBuckets) {
        return (long) milliBuckets * AEFluidKey.AMOUNT_BUCKET / FluidType.BUCKET_VOLUME;
    }

    private static int milliBuckets(long droplets) {
        long converted = droplets * FluidType.BUCKET_VOLUME / AEFluidKey.AMOUNT_BUCKET;
        if(converted <= 0) return 0;
        return converted > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) converted;
    }
}
