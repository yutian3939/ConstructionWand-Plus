package thetadev.constructionwand.plus.wand.undo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thetadev.constructionwand.plus.basics.DropDelivery;
import thetadev.constructionwand.plus.basics.DropDestination;
import thetadev.constructionwand.plus.basics.DropReclaim;
import thetadev.constructionwand.plus.basics.ExperienceHelper;
import thetadev.constructionwand.plus.basics.ExperienceIntake;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;
import thetadev.constructionwand.plus.basics.PlusWandUtil;
import thetadev.constructionwand.plus.basics.SilkTouchDrops;
import thetadev.constructionwand.plus.material.IMaterialCore;
import thetadev.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Breaks a single block and hands out its drops.
 * <p>
 * Unlike the destruction core this snapshot keeps the vanilla drop table, optionally replaced by the
 * silk touch drop, and routes the result through the wand's material core or the player inventory,
 * following the wand settings. The experience the block is worth goes straight to the player, and is
 * taken back when the digging is undone.
 */
public class DigSnapshot implements ISnapshot
{
    private final BlockPos pos;
    private final BlockState block;
    private final boolean silkTouch;
    private final boolean allowUnbreakable;
    private final DropDestination destination;
    @Nullable
    private final IMaterialCore materialCore;
    @Nullable
    private final ItemStack wand;

    private List<ItemStack> delivered = Collections.emptyList();
    private int experience;
    private boolean dropsReclaimed;

    public DigSnapshot(BlockPos pos, BlockState block, boolean silkTouch, boolean allowUnbreakable,
                       DropDestination destination, @Nullable IMaterialCore materialCore, @Nullable ItemStack wand) {
        this.pos = pos;
        this.block = block;
        this.silkTouch = silkTouch;
        this.allowUnbreakable = allowUnbreakable;
        this.destination = destination;
        this.materialCore = materialCore;
        this.wand = wand == null ? null : wand.copy();
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public BlockState getBlockState() {
        return block;
    }

    @Override
    public ItemStack getRequiredItems() {
        // The wand hands nothing out to place a dug block; the harvest happens in execute.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean execute(Level level, Player player, BlockHitResult rayTraceResult) {
        if(!(level instanceof ServerLevel serverLevel)) return false;
        if(!PlusWandUtil.matchesPlannedState(level.getBlockState(pos), block)) return false;

        boolean grantsExperience = grantsExperience(level, player);
        int fortune = fortuneOf(player);
        boolean autoSmelt = PassiveUpgrades.hasAutoSmelt(wand);
        // The loot table rolls the fortune itself, exactly where vanilla does it: ores, gravel, leaves.
        // The smelting runs afterwards on whatever came out of it, so one iron ore is rolled first and
        // each of the raw pieces is smelted, which turns it into several ingots without a second bonus.
        List<ItemStack> drops = collectDrops(serverLevel, player, fortune);
        int gained = grantsExperience ? collectExperience(serverLevel, player, fortune) : 0;

        // Modded blocks report their experience by spawning orbs from their break hooks, those are
        // caught here and added to what the player receives.
        ExperienceIntake.begin(player);
        int absorbed;
        boolean removed;
        try {
            removed = PlusWandUtil.breakBlock(level, player, pos, block, allowUnbreakable);
            if(removed && autoSmelt) gained += smelt(serverLevel, drops);
        } finally {
            absorbed = ExperienceIntake.end();
        }
        if(grantsExperience) gained += absorbed;

        if(!removed) {
            if(!PlusWandUtil.matchesPlannedState(level.getBlockState(pos), block)) {
                // The break event or another mod mutated the world: put the block back to keep the undo
                // history consistent.
                level.setBlockAndUpdate(pos, block);
            }
            return false;
        }

        List<ItemStack> handedOut = new ArrayList<>(drops.size());
        for(ItemStack drop : drops) {
            DropDelivery.deliver(level, player, pos, drop, destination, materialCore, wand, handedOut);
        }
        if(gained > 0) player.giveExperiencePoints(gained);

        delivered = Collections.unmodifiableList(handedOut);
        experience = Math.max(0, gained);
        return true;
    }

    @Override
    public boolean canRestore(Level level, Player player) {
        // Whether the drops can be taken back is decided for the whole operation at once, see
        // UndoHistory: the snapshots share the player inventory and the material core, so reporting the
        // shortage per block would list the same missing items once per block.
        return DropReclaim.missingReclaim(player, materialCore, wand, delivered) == null;
    }

    @Override
    public ISnapshot.Reclaim getReclaim() {
        return delivered.isEmpty() ? null : new ISnapshot.Reclaim(materialCore, wand, delivered);
    }

    @Override
    public boolean restore(Level level, Player player) {
        if(!takeDrops(player)) return false;

        if(PlusWandUtil.matchesPlannedState(level.getBlockState(pos), block)) return true;
        if(!PlusWandUtil.isPositionModifiable(level, player, pos)) return false;
        if(!level.isEmptyBlock(pos) && !PlusWandUtil.clearIntrudingFluid(level, pos)) return false;

        return level.setBlockAndUpdate(pos, block);
    }

    @Override
    public void forceRestore(Level level) {
        // No player at hand: only the material core can give the drops back.
        if(!delivered.isEmpty() && !dropsReclaimed) {
            dropsReclaimed = DropReclaim.reclaim(null, materialCore, wand, destination, delivered) == null;
        }
        if(!level.isEmptyBlock(pos)) PlusWandUtil.clearIntrudingFluid(level, pos);
        if(level.getBlockState(pos).isAir()) level.setBlockAndUpdate(pos, block);
    }

    /**
     * Takes the delivered items back, all or nothing. Nothing is removed when the items are not
     * available, so the undo can be refused and retried after they found their way back. The flag
     * keeps the removal idempotent so a retried recovery cannot consume the same items twice.
     */
    private boolean takeDrops(@Nullable Player player) {
        if(dropsReclaimed) return true;
        if(!delivered.isEmpty()) {
            ItemStack missing = DropReclaim.reclaim(player, materialCore, wand, destination, delivered);
            if(missing != null) return false;
        }
        dropsReclaimed = true;
        // The experience was handed out together with the drops, so undoing takes it back too.
        ExperienceHelper.reclaim(player, experience);
        return true;
    }

    /**
     * Follows the vanilla rule for experience: creative players and a disabled block drop game rule
     * get nothing.
     */
    private static boolean grantsExperience(Level level, Player player) {
        return level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !player.isCreative();
    }

    private int collectExperience(ServerLevel level, Player player, int fortune) {
        try {
            return Math.max(0, block.getBlock().getExpDrop(block, level, level.random, pos, fortune, 0));
        } catch(RuntimeException exception) {
            return 0;
        }
    }

    private List<ItemStack> collectDrops(ServerLevel level, Player player, int fortune) {
        List<ItemStack> drops = new ArrayList<>();
        if(!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return drops;

        if(PlusWandUtil.isBlockUnbreakable(level, pos, block)) {
            // Unbreakable blocks have no drop table; hand out the block itself when configured to, so a
            // dug bedrock is not simply lost.
            if(allowUnbreakable && thetadev.constructionwand.basics.ConfigServer.DIGGING_DROP_UNBREAKABLE.get()) {
                ItemStack drop = SilkTouchDrops.drop(level, pos, block);
                if(!drop.isEmpty()) drops.add(drop);
            }
            return drops;
        }

        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ItemStack tool = toolFor(fortune);
            for(ItemStack drop : Block.getDrops(block, level, pos, blockEntity, player, tool)) {
                if(drop != null && !drop.isEmpty()) drops.add(drop.copy());
            }
        } catch(RuntimeException exception) {
            // A broken loot table must not take the whole wand action down.
        }
        return drops;
    }

    /**
     * The tool the loot table sees: a plain diamond pickaxe carrying exactly the enchantments the
     * wand is supposed to act with. Vanilla resolves silk touch, fortune and the harvest rules from
     * the tool, so this reproduces them without reimplementing the loot system.
     */
    private ItemStack toolFor(int fortune) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if(silkTouch) tool.enchant(Enchantments.SILK_TOUCH, 1);
        else if(fortune > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        return tool;
    }

    /**
     * The fortune level the drops and the experience are rolled with: the enchantment the wand
     * carries, or the configured level of the fortune component when that is higher.
     */
    private int fortuneOf(Player player) {
        ItemStack tool = wand == null || wand.isEmpty() ? player.getMainHandItem() : wand;
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        if(PassiveUpgrades.hasFortune(wand)) fortune = Math.max(fortune, PassiveUpgrades.fortuneLevel());
        return Math.max(0, fortune);
    }

    /**
     * Replaces the drops with their furnace results, the way an auto-smelting wand should. The fortune
     * was already rolled over the loot table before this, so the results are not multiplied again.
     *
     * @return the smelting experience the results are worth
     */
    private static int smelt(ServerLevel level, List<ItemStack> drops) {
        int experience = 0;
        for(int index = 0; index < drops.size(); index++) {
            ItemStack drop = drops.get(index);
            try {
                var recipe = level.getRecipeManager()
                        .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                                new net.minecraft.world.SimpleContainer(drop), level)
                        .orElse(null);
                if(recipe == null) continue;

                var cooking = recipe;
                ItemStack result = cooking.getResultItem(level.registryAccess());
                if(result.isEmpty()) continue;

                ItemStack smelted = result.copy();
                smelted.setCount(result.getCount() * drop.getCount());
                float perItem = cooking.getExperience();
                if(perItem > 0.0F) experience += Math.round(perItem * smelted.getCount());
                drops.set(index, smelted);
            } catch(RuntimeException exception) {
                // Keep the unsmelted drop rather than losing it.
            }
        }
        return experience;
    }
}
