package com.xinyihl.constructionwandlegacy.wand;

import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.config.PlacementRules;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.registry.BlockEquivalenceIndex;
import com.xinyihl.morewandcore.basics.WandLimit;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Read-only inputs captured for one planning and execution attempt.
 */
public final class WandContext {
    private final EntityPlayer player;
    private final World world;
    @Nullable
    private final RayTraceResult rayTraceResult;
    private final ItemStack wand;
    private final ItemWand wandItem;
    private final WandSpec spec;
    private final WandState state;
    private final int placementLimit;
    private final int slayRadius;
    private final int dimension;
    private final PlacementRules placementRules;
    private final BlockEquivalenceIndex blockEquivalenceIndex;
    private final ConfigRuntime.Snapshot rules;

    private WandContext(EntityPlayer player, World world, @Nullable RayTraceResult rayTraceResult, ItemStack wand, ItemWand wandItem, WandState state, ConfigRuntime.Snapshot rules) {
        this.player = Objects.requireNonNull(player, "player");
        this.world = Objects.requireNonNull(world, "world");
        this.rayTraceResult = rayTraceResult;
        this.wand = Objects.requireNonNull(wand, "wand");
        this.wandItem = Objects.requireNonNull(wandItem, "wandItem");
        this.spec = wandItem.getSpec();
        this.state = Objects.requireNonNull(state, "state");
        // The configured maximum of the tier stays the cap, a wand may lower it for itself.
        this.placementLimit = WandLimit.resolve(wand, rules.getPlacementLimit(wandItem.getTier()));
        this.slayRadius = WandLimit.resolve(wand, wandItem.getTier().getConfiguredSlayRadius());
        this.dimension = world.provider.getDimension();
        this.placementRules = rules.getPlacementRules();
        this.blockEquivalenceIndex = rules.getBlockEquivalenceIndex();
        this.rules = rules;
    }

    /**
     * @return the same context aimed at another block, used when the wand resolves a target the
     * vanilla ray trace skipped, like the fluid surface it treats as an ordinary block
     */
    WandContext withRayTraceResult(@Nullable RayTraceResult rayTraceResult) {
        return new WandContext(player, world, rayTraceResult, wand, wandItem, state, rules);
    }

    public static WandContext create(EntityPlayer player, World world, @Nullable RayTraceResult rayTraceResult, ItemStack wand) {
        return create(player, world, rayTraceResult, wand, ConfigRuntime.getSnapshot());
    }

    public static WandContext create(EntityPlayer player, World world, @Nullable RayTraceResult rayTraceResult, ItemStack wand, ConfigRuntime.Snapshot rules) {
        if (wand == null || wand.isEmpty() || !(wand.getItem() instanceof ItemWand)) {
            throw new IllegalArgumentException("Wand context requires a non-empty wand stack");
        }
        return new WandContext(player, world, rayTraceResult, wand, (ItemWand) wand.getItem(), WandDataCodec.read(wand), Objects.requireNonNull(rules, "rules"));
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }

    @Nullable
    public RayTraceResult getRayTraceResult() {
        return rayTraceResult;
    }

    public ItemStack getWand() {
        return wand.copy();
    }

    /**
     * Server-only execution access; callers outside the wand package receive a copy.
     */
    ItemStack getMutableWand() {
        return wand;
    }

    public ItemWand getWandItem() {
        return wandItem;
    }

    public WandSpec getSpec() {
        return spec;
    }

    public WandState getState() {
        return state;
    }

    public int getPlacementLimit() {
        return placementLimit;
    }

    public int getSlayRadius() {
        return slayRadius;
    }

    public int getDimension() {
        return dimension;
    }

    public PlacementRules getPlacementRules() {
        return placementRules;
    }

    public BlockEquivalenceIndex getBlockEquivalenceIndex() {
        return blockEquivalenceIndex;
    }

    public boolean isBlockHit() {
        return rayTraceResult != null && rayTraceResult.typeOfHit == RayTraceResult.Type.BLOCK;
    }

    public boolean matchesBlocks(IBlockState first, IBlockState second) {
        if (first == null || second == null) {
            return false;
        }
        int firstMeta = first.getBlock().getMetaFromState(first);
        int secondMeta = second.getBlock().getMetaFromState(second);
        switch (state.getMatch()) {
            case EXACT:
                return first.getBlock() == second.getBlock() && firstMeta == secondMeta;
            case SIMILAR:
                return blockEquivalenceIndex.matchBlocks(first.getBlock(), second.getBlock());
            case ANY:
                return first.getBlock() != Blocks.AIR && second.getBlock() != Blocks.AIR;
            default:
                return false;
        }
    }
}
