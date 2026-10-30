package dev.polaris_light.constructionwand.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandSupplier;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.ModStats;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.items.ModItems;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.basics.FluidAwareTrace;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;
import dev.polaris_light.constructionwand.plus.basics.WandLimit;
import dev.polaris_light.constructionwand.plus.compat.PlusCompat;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;
import dev.polaris_light.constructionwand.wand.supplier.SupplierInventory;
import dev.polaris_light.constructionwand.wand.supplier.SupplierRandom;
import dev.polaris_light.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WandJob
{
    public final Player player;
    public final Level world;
    public BlockHitResult rayTraceResult;
    /**
     * The hit the job started from, before a fluid was resolved. The preview compares against this so
     * its cache is not invalidated every frame while a fluid is targeted.
     */
    public final BlockHitResult originalRayTraceResult;
    public final WandOptions options;
    public final ItemStack wand;
    public final ItemWand wandItem;

    private final IWandAction wandAction;
    private final IWandSupplier wandSupplier;

    private List<ISnapshot> placeSnapshots;

    public WandJob(Player player, Level world, BlockHitResult rayTraceResult, ItemStack wand) {
        this.player = player;
        this.world = world;
        this.rayTraceResult = rayTraceResult;
        this.originalRayTraceResult = rayTraceResult;
        this.placeSnapshots = new ArrayList<>();

        // Get wand
        this.wand = wand;
        this.wandItem = (ItemWand) wand.getItem();
        options = new WandOptions(wand);

        // Select wand action and supplier based on options
        wandAction = options.cores.get().getWandAction();

        // When fluids count as blocks they have to be targetable as well; the vanilla ray trace walks
        // past them, so the line is traced again to get the fluid itself.
        if(PlusWandUtil.fluidRemovalOn(wand) && PlusWandUtil.removesBlocks(wandAction)) {
            this.rayTraceResult = FluidAwareTrace.resolveTarget(world, player, this.rayTraceResult);
        }

        IMaterialCore materialCore = options.getMaterialCore();
        if(materialCore != null) {
            // A material core decides where the blocks come from, so it brings its own supplier.
            wandSupplier = materialCore.createSupplier(player, options, wand);
        }
        else if(options.random.get()) {
            wandSupplier = new SupplierRandom(player, options);
        }
        else if(PlusCompat.usesExtendedInventory(wand)) {
            // Bound containers come first, with the inventory as the fallback.
            wandSupplier = PlusCompat.inventorySupplier(player, options, wand, true);
        }
        else {
            wandSupplier = new SupplierInventory(player, options);
        }

        wandSupplier.getSupply(getTargetItem(world, rayTraceResult));
    }

    @Nullable
    private static BlockItem getTargetItem(Level world, BlockHitResult rayTraceResult) {
        // Get target item
        Item tgitem = world.getBlockState(rayTraceResult.getBlockPos()).getBlock().asItem();
        if(!(tgitem instanceof BlockItem)) return null;
        return (BlockItem) tgitem;
    }

    public void getSnapshots() {
        int limit;
        // Infinity wand gets enhanced limit in creative mode
        if(player.isCreative() && wandItem == ModItems.WAND_INFINITY.get()) limit = ConfigServer.LIMIT_CREATIVE.get();
        else limit = Math.min(wandItem.remainingDurability(wand), wandAction.getLimit(wand));

        // A wand can lower its own limit from the wand screen, never above what the server allows.
        limit = WandLimit.resolve(wand, limit);

        if(rayTraceResult.getType() == HitResult.Type.BLOCK)
            placeSnapshots = wandAction.getSnapshots(world, player, rayTraceResult, wand, options, wandSupplier, limit);
        else
            placeSnapshots = wandAction.getSnapshotsFromAir(world, player, rayTraceResult, wand, options, wandSupplier, limit);
    }

    public Set<BlockPos> getBlockPositions() {
        return placeSnapshots.stream().map(ISnapshot::getPos).collect(Collectors.toSet());
    }

    public int blockCount() {
        return placeSnapshots.size();
    }

    public boolean doIt() {
        ArrayList<ISnapshot> executed = new ArrayList<>();

        for(ISnapshot snapshot : placeSnapshots) {
            if(wand.isEmpty() || wandItem.remainingDurability(wand) == 0) break;

            if(snapshot.execute(world, player, rayTraceResult)) {
                if(player.isCreative()) executed.add(snapshot);
                else {
                    // If the item cant be taken, undo the placement
                    if(wandSupplier.takeItemStack(snapshot.getRequiredItems()) == 0) {
                        executed.add(snapshot);
                        wand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    }
                    else {
                        ConstructionWand.LOGGER.info("Item could not be taken. Remove block: " +
                                snapshot.getBlockState().getBlock().toString());
                        snapshot.forceRestore(world);
                    }
                }
                player.awardStat(ModStats.USE_WAND);
            }
        }
        placeSnapshots = executed;

        // Play place sound
        if(!placeSnapshots.isEmpty()) {
            SoundType sound = placeSnapshots.get(0).getBlockState().getSoundType();
            world.playSound(null, player.blockPosition(), sound.getPlaceSound(), SoundSource.BLOCKS, sound.volume, sound.pitch);

            // Add to job history for undo
            ConstructionWand.undoHistory.add(player, world, placeSnapshots);
        }

        return !placeSnapshots.isEmpty();
    }
}
