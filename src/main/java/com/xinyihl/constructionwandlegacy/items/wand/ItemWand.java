package com.xinyihl.constructionwandlegacy.items.wand;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandOption;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.client.ClientEvents;
import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.items.core.CoreDefault;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import com.xinyihl.constructionwandlegacy.wand.WandContext;
import com.xinyihl.constructionwandlegacy.wand.WandPlan;
import com.xinyihl.constructionwandlegacy.wand.WandSpec;
import com.xinyihl.constructionwandlegacy.wand.WandTier;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import com.xinyihl.morewandcore.basics.SlayHandler;
import com.xinyihl.morewandcore.basics.WandLimit;
import com.xinyihl.morewandcore.item.ItemCoreSlay;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public abstract class ItemWand extends Item {
    private final WandTier tier;

    protected ItemWand(WandTier tier) {
        this.tier = Objects.requireNonNull(tier, "tier");
        setMaxStackSize(1);
        addPropertyOverride(ConstructionWandLegacy.loc("using_core"), (stack, worldIn, entityIn) -> hasCustomCore(stack) ? 1.0F : 0.0F);
    }

    private static boolean hasCustomCore(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return WandDataCodec.read(stack).getSelectedCore().getColor() > -1;
    }

    public static void optionMessage(EntityPlayer player, WandOption option, WandState state) {
        ITextComponent key = new TextComponentTranslation(option.getKeyTranslation());
        key.getStyle().setColor(TextFormatting.AQUA);

        ITextComponent value = new TextComponentTranslation(option.getValueTranslation(state));
        value.getStyle().setColor(TextFormatting.WHITE);

        ITextComponent desc = new TextComponentTranslation(option.getDescriptionTranslation(state));
        desc.getStyle().setColor(TextFormatting.WHITE);

        key.appendSibling(value);
        key.appendSibling(new TextComponentString(" - ").setStyle(key.getStyle().createShallowCopy().setColor(TextFormatting.GRAY)));
        key.appendSibling(desc);

        player.sendStatusMessage(key, true);
    }

    private static boolean executeWand(EntityPlayer player, World world, RayTraceResult hit, ItemStack wand) {
        WandContext context = WandContext.create(player, world, hit, wand);
        WandPlan plan = ConstructionWandLegacy.instance.getRuntime().getWandPlanner().plan(context);
        return ConstructionWandLegacy.instance.getRuntime().getWandExecutor().execute(context, plan).isSuccess();
    }

    public final WandTier getTier() {
        return tier;
    }

    public final WandSpec getSpec() {
        return tier.getSpec();
    }

    public final int getPlacementLimit() {
        return tier.getConfiguredPlacementLimit();
    }

    private boolean bindContainer(ItemStack stack, EntityPlayer player, World world, BlockPos pos) {
        WandState state = WandDataCodec.read(stack);
        // Only default core supports container binding
        if (!(state.getSelectedCore() instanceof CoreDefault)) {
            return false;
        }
        int dimension = world.provider.getDimension();
        // Clicking a container the wand is already bound to takes the binding off again.
        if (BoundContainerSourceFactory.isBound(stack, pos, dimension)) {
            if (BoundContainerSourceFactory.removeBinding(stack, pos, dimension)) {
                player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".tooltip.container_unbound", BoundContainerSourceFactory.readBindings(stack).size()), true);
            }
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) return false;
        // A container that only holds fluid is a binding as well: it is where the fluid storage setting
        // of the digging core puts the fluid it clears.
        if (!tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                && !tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            return false;
        }
        BoundContainerSourceFactory.BindResult result = BoundContainerSourceFactory.storeBinding(stack, pos, dimension);
        if (result == BoundContainerSourceFactory.BindResult.FAILED) {
            return false;
        }
        if (result == BoundContainerSourceFactory.BindResult.FULL) {
            player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".tooltip.binding_full", BoundContainerSourceFactory.getMaxContainers()), true);
            return true;
        }
        player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".tooltip.container_bound", BoundContainerSourceFactory.readBindings(stack).size()), true);
        return true;
    }

    public int remainingDurability(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    private static boolean isSlayCore(ItemStack stack) {
        return WandDataCodec.read(stack).getSelectedCore() instanceof ItemCoreSlay;
    }

    /**
     * Whether the player is holding the config key combination (sneak + ctrl). The sneaking right
     * click actions (e.g. the slay core's area kill) must not fire while the player is opening the
     * config screen, so they check this first.
     */
    private static boolean isConfigMode(EntityPlayer player) {
        return ConstructionWandLegacy.instance.getRuntime().getUndoService().isUndoActive(player);
    }

    private void slayRadius(ItemStack stack, EntityPlayer player) {
        WandState state = WandDataCodec.read(stack);
        int radius = WandLimit.resolve(stack, tier.getConfiguredSlayRadius());
        SlayHandler.slayRadius(player, stack, state, radius);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        if (player.world.isRemote) {
            return false;
        }
        if (isSlayCore(stack)) {
            SlayHandler.slaySingle(player, entity, stack, WandDataCodec.read(stack));
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        ItemStack stack = player.getHeldItem(hand);

        if (player.isSneaking() && isSlayCore(stack) && !isConfigMode(player)) {
            slayRadius(stack, player);
            return EnumActionResult.SUCCESS;
        }

        if (player.isSneaking() && CompatRegistrar.tryBindAE(stack, player, world, pos)) {
            return EnumActionResult.SUCCESS;
        }

        if (player.isSneaking() && bindContainer(stack, player, world, pos)) {
            return EnumActionResult.SUCCESS;
        }

        RayTraceResult hitResult = new RayTraceResult(new Vec3d(pos).add(hitX, hitY, hitZ), facing, pos);
        return executeWand(player, world, hitResult, stack) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (player.isSneaking()) {
            if (isSlayCore(stack) && !isConfigMode(player)) {
                slayRadius(stack, player);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        RayTraceResult miss = new RayTraceResult(RayTraceResult.Type.MISS, player.getPositionEyes(1.0F), EnumFacing.getFacingFromVector((float) player.getLookVec().x, (float) player.getLookVec().y, (float) player.getLookVec().z), player.getPosition());
        return new ActionResult<>(executeWand(player, world, miss, stack) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        WandState state = WandDataCodec.read(stack);
        int limit = WandLimit.resolve(stack, state.getSelectedCore().getWandAction().getLimit(stack));
        if (GuiScreen.isShiftKeyDown()) {
            for (WandOption option : WandOption.values()) {
                if (option == WandOption.CORES || option == WandOption.MATERIAL_CORES) {
                    continue;
                }
                tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(option.getKeyTranslation()) + TextFormatting.GRAY + I18n.translateToLocal(option.getValueTranslation(state)));
            }

            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.cores"));
            for (IWandCore core : state.getBehaviorCores()) {
                tooltip.add(I18n.translateToLocal(WandOption.CORES.getKeyTranslation() + "." + core.getRegistryName().toString()));
            }

            if (!state.getMaterialCores().isEmpty()) {
                tooltip.add("");
                tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.material_cores"));
                for (IWandCore core : state.getMaterialCores()) {
                    tooltip.add(I18n.translateToLocal(WandOption.MATERIAL_CORES.getKeyTranslation() + "." + core.getRegistryName().toString()));
                }
            }

            if (!state.getUpgrades().isEmpty()) {
                tooltip.add("");
                tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.upgrades"));
                for (IWandUpgrade upgrade : state.getUpgrades()) {
                    if (upgrade instanceof Item) {
                        tooltip.add(I18n.translateToLocal(((Item) upgrade).getTranslationKey() + ".name"));
                    }
                }
            }

            // Show binding status
            if (CompatRegistrar.hasAE2Binding(stack, state.getSelectedMaterialCore())) {
                tooltip.add(TextFormatting.GREEN + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.ae_bound"));
            }
            if (state.getSelectedCore() instanceof CoreDefault && BoundContainerSourceFactory.hasBinding(stack)) {
                tooltip.add(TextFormatting.GREEN + I18n.translateToLocalFormatted(Tags.MOD_ID + ".tooltip.container_bound", BoundContainerSourceFactory.readBindings(stack).size()));
            }
        } else {
            tooltip.add(TextFormatting.GRAY + String.format(I18n.translateToLocal(Tags.MOD_ID + ".tooltip.blocks"), limit));
            tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(WandOption.CORES.getKeyTranslation()) + TextFormatting.WHITE + I18n.translateToLocal(WandOption.CORES.getValueTranslation(state)));
            tooltip.add(TextFormatting.AQUA + I18n.translateToLocal(Tags.MOD_ID + ".tooltip.shift"));
        }
    }
}
