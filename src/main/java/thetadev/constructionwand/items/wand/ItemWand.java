package thetadev.constructionwand.items.wand;

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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.generators.ModelFile;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.api.IWandUpgrade;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.IOption;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.data.ICustomItemModel;
import thetadev.constructionwand.data.ItemModelGenerator;
import thetadev.constructionwand.plus.basics.ExtraWandOption;
import thetadev.constructionwand.plus.basics.FluidAwareTrace;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;
import thetadev.constructionwand.plus.basics.PlusWandUtil;
import thetadev.constructionwand.plus.basics.SlayHandler;
import thetadev.constructionwand.plus.basics.WandLimit;
import thetadev.constructionwand.plus.items.ItemCoreSlay;
import thetadev.constructionwand.plus.items.ItemCoreStorage;
import thetadev.constructionwand.plus.material.ContainerBinding;
import thetadev.constructionwand.wand.WandJob;

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

        if(player.isCrouching() && ConstructionWand.instance.undoHistory.isUndoActive(player)) {
            return ConstructionWand.instance.undoHistory.undo(player, world, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
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

        if(!player.isCrouching()) {
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

            // Right click: Place angel block
            WandJob job = getWandJob(player, world, BlockHitResult.miss(player.getLookAngle(),
                    WandUtil.fromVector(player.getLookAngle()), player.blockPosition()), stack);
            return job.doIt() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        }

        // The config screen and the undo preview are opened with the same sneaking right click, so the
        // area kill waits until that combination is released. useOn checks the same thing for the block
        // path, this one covers right clicking mid air.
        if(player.isCrouching() && ConstructionWand.instance.undoHistory.isUndoActive(player)) {
            return InteractionResultHolder.fail(stack);
        }

        // Sneaking right click in mid air: the slay core's area kill.
        if(isSlayCore(stack)) {
            if(!world.isClientSide) SlayHandler.slayRadius(player, stack, new WandOptions(stack), slayRadius(stack));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
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
        return !stack.isEmpty() && new WandOptions(stack).cores.get() instanceof ItemCoreSlay;
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

    public static WandJob getWandJob(Player player, Level world, @Nullable BlockHitResult rayTraceResult, ItemStack wand) {
        WandJob wandJob = new WandJob(player, world, rayTraceResult, wand);
        wandJob.getSnapshots();

        return wandJob;
    }

    @Override
    public boolean isCorrectToolForDrops(@Nonnull BlockState blockIn) {
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
    public void appendHoverText(@Nonnull ItemStack itemstack, Level worldIn, @Nonnull List<Component> lines, @Nonnull TooltipFlag extraInfo) {
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
            if(options.materialCores.get() instanceof thetadev.constructionwand.plus.compat.ae2.ItemCoreAE) {
                boolean aeBound = thetadev.constructionwand.plus.compat.ae2.AE2Compat.hasBinding(itemstack);
                lines.add(Component.translatable(aeBound ? langTooltip + "ae_bound" : langTooltip + "ae_unbound")
                        .withStyle(aeBound ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        // Default tooltip: show block limit + active wand core
        else {
            IOption<?> opt = options.allOptions[0];
            lines.add(Component.translatable(langTooltip + "blocks", limit).withStyle(ChatFormatting.GRAY));
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
