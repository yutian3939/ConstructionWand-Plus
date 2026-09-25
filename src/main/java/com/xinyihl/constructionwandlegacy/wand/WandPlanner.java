package com.xinyihl.constructionwandlegacy.wand;

import com.xinyihl.constructionwandlegacy.basics.WandUtil;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.basics.pool.IPool;
import com.xinyihl.constructionwandlegacy.basics.pool.OrderedPool;
import com.xinyihl.constructionwandlegacy.basics.pool.RandomPool;
import com.xinyihl.constructionwandlegacy.items.core.CoreDefault;
import com.xinyihl.constructionwandlegacy.material.*;
import com.xinyihl.constructionwandlegacy.wand.action.WandAction;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.wand.operation.DestroyOperation;
import com.xinyihl.constructionwandlegacy.wand.operation.PlaceOperation;
import com.xinyihl.constructionwandlegacy.wand.operation.PlantOperation;
import com.xinyihl.morewandcore.basics.ExtraWandOption;
import com.xinyihl.morewandcore.basics.FluidRemoval;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public final class WandPlanner {
    private final MaterialSourceRegistry materialSources;
    private final Logger logger;

    public WandPlanner(MaterialSourceRegistry materialSources, Logger logger) {
        this.materialSources = materialSources;
        this.logger = logger;
    }

    private static int operationLimit(WandContext context, WandAction action) {
        // One limit for every action and every game mode; the creative special case is gone, so a
        // creative infinity wand is bound by the configuration like everything else.
        return Math.min(context.getWandItem().remainingDurability(context.getWand()), action.getLimit(context));
    }

    private static boolean isPlantingMode(WandContext context, ItemStack offhand) {
        RayTraceResult hit = context.getRayTraceResult();
        if (!(context.getState().getSelectedCore() instanceof CoreDefault) || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit != EnumFacing.UP || offhand.isEmpty() || !(offhand.getItem() instanceof IPlantable)) {
            return false;
        }

        World world = context.getWorld();
        EntityPlayer player = context.getPlayer();
        BlockPos farmlandPos = hit.getBlockPos();
        BlockPos cropPos = farmlandPos.up();
        IPlantable plantable = (IPlantable) offhand.getItem();
        IBlockState farmland = world.getBlockState(farmlandPos);
        return world.isAirBlock(cropPos) && world.isBlockModifiable(player, cropPos) && player.canPlayerEdit(cropPos, EnumFacing.UP, offhand) && farmland.getBlock().canSustainPlant(farmland, world, farmlandPos, EnumFacing.UP, plantable);
    }

    @Nullable
    private static ItemStack getTargetItem(WandContext context) {
        RayTraceResult hit = context.getRayTraceResult();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        IBlockState state = context.getWorld().getBlockState(hit.getBlockPos());
        Block block = state.getBlock();
        Item blockItem = Item.getItemFromBlock(block);
        return blockItem instanceof ItemBlock ? new ItemStack(blockItem, 1, block.damageDropped(state)) : null;
    }

    public WandPlan plan(WandContext context) {
        // The action always comes from the behaviour core, the items always from the material core.
        WandAction action = context.getState().getSelectedCore().getWandAction();
        if (action.removesBlocks() && ExtraWandOption.getFluidRemoval(context.getWand()) == FluidRemoval.ON) {
            // The vanilla ray trace walks past fluids, so aiming at a water surface is a miss. With the
            // wand set to treat fluids as blocks, the target is resolved again to get the fluid itself.
            context = context.withRayTraceResult(WandUtil.resolveTarget(context.getWorld(), context.getPlayer(), context.getRayTraceResult()));
        }
        int limit = operationLimit(context, action);
        if (limit <= 0) {
            return new WandPlan(Collections.emptyList());
        }

        IWandCore materialCore = context.getState().getSelectedMaterialCore();
        MaterialSourceFactory coreFactory = materialCore == null ? null : materialCore.getMaterialSourceFactory();
        MaterialSession session = coreFactory == null ? materialSources.createInventorySession(context.getPlayer(), context.getWand()) : materialSources.createSession(context.getPlayer(), context.getWand(), coreFactory);

        Resolver resolver;
        ItemStack offhand = context.getPlayer().getHeldItemOffhand();
        if (isPlantingMode(context, offhand)) {
            resolver = new PlantingResolver(context, session, offhand);
        } else {
            boolean random = context.getState().isRandom() && coreFactory == null;
            resolver = new PlacementResolver(context, session, random, getTargetItem(context));
        }

        try {
            List<WandOperation> operations = context.isBlockHit() ? action.plan(context, resolver, limit) : action.planFromAir(context, resolver, limit);
            return new WandPlan(operations, resolver.reservations);
        } catch (RuntimeException exception) {
            resolver.cancelReservations();
            logger.warn("Failed to plan wand operation", exception);
            return new WandPlan(Collections.emptyList());
        }
    }

    private abstract class Resolver implements WandAction.OperationResolver {
        final WandContext context;
        final MaterialSession session;
        final Map<WandOperation, MaterialReservation> reservations = new IdentityHashMap<>();

        private Resolver(WandContext context, MaterialSession session) {
            this.context = context;
            this.session = session;
        }

        void remember(WandOperation operation, MaterialReservation reservation) {
            reservations.put(operation, reservation);
        }

        void cancelReservations() {
            for (MaterialReservation reservation : reservations.values()) {
                reservation.cancel();
            }
        }

        @Nullable
        @Override
        public final WandOperation createPlacement(BlockPos pos, @Nullable IBlockState supportingBlock) {
            try {
                return planPlacement(pos, supportingBlock);
            } catch (RuntimeException exception) {
                logger.warn("Failed to plan wand placement at {}", pos, exception);
                return null;
            }
        }

        @Nullable
        protected abstract WandOperation planPlacement(BlockPos pos, @Nullable IBlockState supportingBlock);

        @Nullable
        @Override
        public final WandOperation createDestruction(BlockPos pos) {
            try {
                return DestroyOperation.create(context, pos);
            } catch (RuntimeException exception) {
                logger.warn("Failed to plan wand destruction at {}", pos, exception);
                return null;
            }
        }
    }

    private final class PlacementResolver extends Resolver {
        private final Set<MaterialKey> trackedKeys = new HashSet<>();
        private final IPool<MaterialKey> itemPool;

        private PlacementResolver(WandContext context, MaterialSession session, boolean random, @Nullable ItemStack target) {
            super(context, session);
            itemPool = random ? new RandomPool<>(new Random()) : new OrderedPool<>();
            if (random) {
                for (ItemStack stack : WandUtil.getHotbarWithOffhand(context.getPlayer())) {
                    if (stack.getItem() instanceof ItemBlock) {
                        addStack(stack);
                    }
                }
            } else {
                ItemStack offhand = context.getPlayer().getHeldItemOffhand();
                if (!offhand.isEmpty() && offhand.getItem() instanceof ItemBlock) {
                    addStack(offhand);
                } else if (target != null && !target.isEmpty() && target.getItem() instanceof ItemBlock) {
                    addStack(target);
                    if (context.getState().getMatch() != WandState.Match.EXACT) {
                        ItemBlock targetItem = (ItemBlock) target.getItem();
                        for (Item item : context.getBlockEquivalenceIndex().matchingItems(targetItem)) {
                            if (item instanceof ItemBlock) {
                                for (MaterialKey key : session.keysForItem(item)) {
                                    addKey(key);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void addStack(ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                addKey(MaterialKey.of(stack));
            }
        }

        private void addKey(MaterialKey key) {
            if (session.available(key) > 0 && trackedKeys.add(key)) {
                itemPool.add(key);
            }
        }

        @Nullable
        @Override
        protected WandOperation planPlacement(BlockPos pos, @Nullable IBlockState supportingBlock) {
            if (!WandUtil.isPositionPlaceable(context.getWorld(), context.getPlayer(), pos, context.getState().isReplace())) {
                return null;
            }

            itemPool.reset();
            while (true) {
                MaterialKey key = itemPool.draw();
                if (key == null || !(key.getItem() instanceof ItemBlock)) {
                    return null;
                }
                if (session.available(key) <= 0) {
                    continue;
                }
                PlaceOperation draft = PlaceOperation.create(context, pos, key.createStack(1), supportingBlock);
                if (draft == null) {
                    continue;
                }
                MaterialReservation reservation = session.reserve(key, 1);
                if (reservation == null) {
                    itemPool.remove(key);
                    continue;
                }
                remember(draft, reservation);
                return draft;
            }
        }
    }

    private final class PlantingResolver extends Resolver {
        private final ItemStack seedStack;
        private final MaterialKey seedKey;

        private PlantingResolver(WandContext context, MaterialSession session, ItemStack seedStack) {
            super(context, session);
            this.seedStack = seedStack.copy();
            this.seedStack.setCount(1);
            this.seedKey = MaterialKey.of(this.seedStack);
        }

        @Nullable
        @Override
        protected WandOperation planPlacement(BlockPos pos, @Nullable IBlockState supportingBlock) {
            if (session.available(seedKey) <= 0) {
                return null;
            }
            PlantOperation draft = PlantOperation.create(context, pos, seedStack);
            if (draft == null) {
                return null;
            }
            MaterialReservation reservation = session.reserve(seedKey, 1);
            if (reservation == null) {
                return null;
            }
            remember(draft, reservation);
            return draft;
        }
    }
}
