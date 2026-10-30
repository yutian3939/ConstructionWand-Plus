package dev.polaris_light.constructionwand.items.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.api.IWandUpgrade;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.IOption;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.data.ICustomItemModel;
import dev.polaris_light.constructionwand.data.ItemModelGenerator;
import dev.polaris_light.constructionwand.plus.basics.ExtraWandOption;
import dev.polaris_light.constructionwand.plus.basics.FluidAwareTrace;
import dev.polaris_light.constructionwand.plus.basics.PassiveUpgrades;
import dev.polaris_light.constructionwand.plus.basics.PlusWandUtil;
import dev.polaris_light.constructionwand.plus.basics.SlayHandler;
import dev.polaris_light.constructionwand.plus.basics.WandLimit;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreLevels;
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;
import dev.polaris_light.constructionwand.plus.items.ItemCoreAE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreSlay;
import dev.polaris_light.constructionwand.plus.items.ItemCoreTime;
import dev.polaris_light.constructionwand.plus.items.ItemCoreStorage;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;
import dev.polaris_light.constructionwand.wand.WandJob;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class ItemWand extends Item implements ICustomItemModel
{
    public ItemWand(Properties properties) {
        super(properties);
    }

    @Nonnull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        Level world = context.getLevel();

        if(world.isClientSide || player == null) return InteractionResult.FAIL;

        ItemStack stack = player.getItemInHand(hand);

        // The wand screen and the area kill are both opened with a sneaking right click, so the screen
        // wins while the config key is held: the key state is what the client sends, and it is the same
        // state the undo rides on. Without this a player opening the screen would wipe out everything
        // around them.
        if(ConstructionWand.undoHistory.isUndoActive(player)) {
            return ConstructionWand.undoHistory.undo(player, world, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        // The slay core kills everything around the player instead of touching the block.
        if(player.isCrouching() && isSlayCore(stack)) {
            SlayHandler.slayRadius(player, stack, new WandOptions(stack), slayRadius(stack));
            return InteractionResult.SUCCESS;
        }
        else {
            WandJob job = getWandJob(player, world, new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false), stack);
            return job.doIt() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level world, Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // The config key check has to run before the area kill for the same reason as in useOn: while
        // the wand screen is being opened, a sneaking right click is not a kill.
        if(ConstructionWand.undoHistory.isUndoActive(player)) return InteractionResultHolder.fail(stack);

        // Sneaking right click in mid air: the slay core's area kill.
        if(player.isCrouching() && isSlayCore(stack)) {
            if(!world.isClientSide) SlayHandler.slayRadius(player, stack, new WandOptions(stack), slayRadius(stack));
            return InteractionResultHolder.success(stack);
        }

        // Aiming straight at a fluid surface only makes sense while fluids count as blocks: the
        // vanilla trace walks past them, so the target is resolved again here.
        if(fluidAsBlocks(stack)) {
            BlockHitResult fluidHit = FluidAwareTrace.fluidTarget(world, player);
            if(fluidHit != null) {
                if(world.isClientSide) return InteractionResultHolder.success(stack);
                WandJob fluidJob = getWandJob(player, world, fluidHit, stack);
                return fluidJob.doIt() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
            }
        }

        if(world.isClientSide) return InteractionResultHolder.fail(stack);

        // Some moving-structure mods handle projected blocks through custom raycasts instead
        // of vanilla block interaction. Try a direct raycast before falling back to angel mode.
        BlockHitResult target = getPlayerTarget(world, player);
        if(target == null) {
            target = BlockHitResult.miss(player.getLookAngle(),
                    WandUtil.fromVector(player.getLookAngle()), player.blockPosition());
        }

        WandJob job = getWandJob(player, world, target, stack);
        return job.doIt() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean onLeftClickEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull net.minecraft.world.entity.Entity entity) {
        if(player.level().isClientSide) return false;
        // Left clicking a creature with the slay core kills it instantly.
        if(isSlayCore(stack)) {
            SlayHandler.slaySingle(player, entity, stack, new WandOptions(stack));
            return true;
        }
        return false;
    }

    public static boolean isSlayCore(ItemStack stack) {
        return WandOptions.isWand(stack) && new WandOptions(stack).cores.get() instanceof ItemCoreSlay;
    }

    /**
     * @return whether the wand's behaviour core is the time core, which accelerates the block it is
     * pointed at instead of placing anything
     */
    public static boolean isTimeCore(ItemStack stack) {
        return WandOptions.isWand(stack) && new WandOptions(stack).cores.get() instanceof ItemCoreTime;
    }

    public static int slayRadius(ItemStack stack) {
        return WandLimit.resolve(stack, ConfigServer.getSlayRadius(stack.getItem()));
    }

    /**
     * @return whether the wand removes blocks and treats fluids as blocks, which is when a fluid
     * surface can be aimed at directly
     */
    private static boolean fluidAsBlocks(ItemStack wand) {
        if(!PlusWandUtil.fluidRemovalOn(wand)) return false;
        return PlusWandUtil.removesBlocks(new WandOptions(wand).cores.get().getWandAction());
    }

    @Nullable
    private static BlockHitResult getPlayerTarget(Level world, Player player) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));

        BlockHitResult hit = world.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    public static WandJob getWandJob(Player player, Level world, @Nullable BlockHitResult rayTraceResult, ItemStack wand) {
        WandJob wandJob = new WandJob(player, world, rayTraceResult, wand);
        wandJob.getSnapshots();

        return wandJob;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState blockIn) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(@Nonnull ItemStack toRepair, @Nonnull ItemStack repair) {
        return false;
    }

    public int remainingDurability(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack itemstack, TooltipContext context, @Nonnull List<Component> lines, @Nonnull TooltipFlag extraInfo) {
        WandOptions options = new WandOptions(itemstack);
        int limit = WandLimit.resolve(itemstack, options.cores.get().getWandAction().getLimit(itemstack));

        String langTooltip = ConstructionWand.MODID + ".tooltip.";

        // +SHIFT tooltip: show all options + installed cores
        if(Screen.hasShiftDown()) {
            for(int i = 1; i < options.allOptions.length; i++) {
                IOption<?> opt = options.allOptions[i];
                lines.add(Component.translatable(opt.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable(opt.getValueTranslation()).withStyle(ChatFormatting.GRAY))
                );
            }
            // The plus settings live in their own tag, so they are listed separately.
            for(ExtraWandOption opt : ExtraWandOption.values()) {
                int index = ExtraWandOption.getDisplayIndex(itemstack, opt);
                lines.add(Component.translatable(opt.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable(opt.getValueTranslation(index)).withStyle(ChatFormatting.GRAY))
                );
            }
            if(!options.cores.getUpgrades().isEmpty()) {
                lines.add(Component.literal(""));
                lines.add(Component.translatable(langTooltip + "cores").withStyle(ChatFormatting.GRAY));

                for(IWandCore core : options.cores.getUpgrades()) {
                    lines.add(Component.translatable(options.cores.getKeyTranslation() + "." + core.getRegistryName().toString()));
                }
            }
            if(!options.materialCores.getUpgrades().isEmpty()) {
                lines.add(Component.literal(""));
                lines.add(Component.translatable(langTooltip + "material_cores").withStyle(ChatFormatting.GRAY));

                for(IWandCore core : options.materialCores.getUpgrades()) {
                    lines.add(Component.translatable(options.materialCores.getKeyTranslation() + "." + core.getRegistryName().toString()));
                }
            }
            if(!options.upgrades.getUpgrades().isEmpty()) {
                lines.add(Component.literal(""));
                lines.add(Component.translatable(langTooltip + "upgrades").withStyle(ChatFormatting.GRAY));

                for(IWandUpgrade upgrade : options.upgrades.getUpgrades()) {
                    if(upgrade instanceof net.minecraft.world.item.Item item) {
                        lines.add(Component.translatable(item.getDescriptionId())
                                .withStyle(PassiveUpgrades.isDisabled(itemstack, item) ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE));
                    }
                }
            }
            // Installed cores can be taken off again on the wand screen.
            if(!options.cores.getUpgrades().isEmpty() || !options.materialCores.getUpgrades().isEmpty()) {
                lines.add(Component.translatable(langTooltip + "core_removal").withStyle(ChatFormatting.DARK_GRAY));
            }
            // Binding status of the storage core.
            int bound = ContainerBinding.count(itemstack);
            if(options.cores.get() instanceof ItemCoreStorage || options.getMaterialCore() != null || bound > 0) {
                lines.add(Component.literal(""));
                lines.add(Component.translatable(
                        bound > 0 ? langTooltip + "container_bound" : langTooltip + "storage_unbound", bound
                ).withStyle(bound > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
            // Binding status of the ME network.
            if(options.materialCores.get() instanceof ItemCoreAE) {
                AeGridAccess.Bound aeBound = AeGridAccess.readBinding(itemstack);
                lines.add(Component.translatable(
                        aeBound == null ? langTooltip + "ae_unbound" : langTooltip + "ae_bound"
                ).withStyle(aeBound == null ? ChatFormatting.GRAY : ChatFormatting.GREEN));
            }
        }
        // Default tooltip: show block limit + active wand core
        else {
            IOption<?> opt = options.allOptions[0];
            lines.add(Component.translatable(langTooltip + "blocks", limit).withStyle(ChatFormatting.GRAY));
            // The time core is the one core whose whole behaviour sits in a number the player cannot see
            // anywhere else, so the ladder and this wand's top step are spelled out here.
            if(options.cores.get() instanceof ItemCoreTime) {
                lines.add(Component.translatable(langTooltip + "time_levels",
                        TimeCoreLevels.ladderText()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(langTooltip + "time_cap",
                        TimeCoreLevels.cap(itemstack)).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(opt.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable(opt.getValueTranslation()).withStyle(ChatFormatting.WHITE)));
            lines.add(Component.translatable(langTooltip + "shift").withStyle(ChatFormatting.AQUA));
        }
    }

    public static void optionMessage(Player player, IOption<?> option) {
        player.displayClientMessage(
                        Component.translatable(option.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable(option.getValueTranslation()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable(option.getDescTranslation()).withStyle(ChatFormatting.WHITE))
                , true);
    }

    @Override
    public void generateCustomItemModel(ItemModelGenerator generator, String name) {
        ModelFile wandWithCore = generator.withExistingParent(name + "_core", "item/handheld")
                .texture("layer0", generator.modLoc("item/" + name))
                .texture("layer1", generator.modLoc("item/overlay_core"));

        generator.withExistingParent(name, "item/handheld")
                .texture("layer0", generator.modLoc("item/" + name))
                .override()
                .predicate(generator.modLoc("using_core"), 1)
                .model(wandWithCore).end();

    }
}
