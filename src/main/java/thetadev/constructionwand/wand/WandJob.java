package thetadev.constructionwand.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.ModStats;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.basics.FluidAwareTrace;
import thetadev.constructionwand.plus.basics.PlusWandUtil;
import thetadev.constructionwand.plus.basics.WandLimit;
import thetadev.constructionwand.plus.compat.PlusCompat;
import thetadev.constructionwand.plus.material.IMaterialCore;
import thetadev.constructionwand.wand.supplier.SupplierInventory;
import thetadev.constructionwand.wand.supplier.SupplierRandom;
import thetadev.constructionwand.wand.undo.ISnapshot;

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
            // Bound containers and trinket slots come first, with the inventory as the fallback.
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
                        wand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
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
            ConstructionWand.instance.undoHistory.add(player, world, placeSnapshots);
        }

        return !placeSnapshots.isEmpty();
    }
}