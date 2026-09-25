package com.xinyihl.constructionwandlegacy.compat;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.compat.ae2.AE2Compat;
import com.xinyihl.constructionwandlegacy.compat.baubles.BaublesMaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.compat.projecte.ProjectEMaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreAE;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceFactory;
import com.xinyihl.constructionwandlegacy.material.MaterialSourceRegistry;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;

public final class CompatRegistrar {
    private static final String BAUBLES_MOD_ID = "baubles";
    private static final String PROJECTE_MOD_ID = "projecte";
    private static final String AE2_MOD_ID = "appliedenergistics2";

    private CompatRegistrar() {
    }

    public static void register(MaterialSourceRegistry registry) {
        if (Loader.isModLoaded(BAUBLES_MOD_ID)) {
            registry.registerInventorySource(BaublesProvider.MATERIAL_SOURCE_FACTORY);
        }
    }

    @Nullable
    public static MaterialSourceFactory getAE2MaterialSourceFactory() {
        return Loader.isModLoaded(AE2_MOD_ID) ? AE2Provider.MATERIAL_SOURCE_FACTORY : null;
    }

    @Nullable
    public static MaterialSourceFactory getProjectEMaterialSourceFactory() {
        return Loader.isModLoaded(PROJECTE_MOD_ID) ? ProjectEProvider.MATERIAL_SOURCE_FACTORY : null;
    }

    public static boolean tryBindAE(ItemStack wand, EntityPlayer player, World world, BlockPos pos) {
        if (!Loader.isModLoaded(AE2_MOD_ID)) {
            return false;
        }
        WandState state = WandDataCodec.read(wand);
        // The AE core is a material core, so it is the material selection that has to be checked.
        IWandCore materialCore = state.getSelectedMaterialCore();
        if (!(materialCore instanceof ItemCoreAE) || !AE2Provider.tryBind(wand, player, world, pos)) {
            return false;
        }
        ResourceLocation coreId = materialCore.getRegistryName();
        if (coreId != null) {
            player.sendStatusMessage(new TextComponentTranslation(Tags.MOD_ID + ".option.cores." + coreId + ".bound"), true);
        }
        return true;
    }

    /**
     * @param selectedMaterialCore the material selection of the wand, see {@link WandState#getSelectedMaterialCore()}
     */
    public static boolean hasAE2Binding(ItemStack wand, IWandCore selectedMaterialCore) {
        return Loader.isModLoaded(AE2_MOD_ID) && selectedMaterialCore instanceof ItemCoreAE && AE2Provider.hasBinding(wand);
    }

    /**
     * Whether the wand carries a stored ME binding, whatever core is selected right now.
     */
    public static boolean hasAE2Binding(ItemStack wand) {
        return Loader.isModLoaded(AE2_MOD_ID) && AE2Provider.hasBinding(wand);
    }

    /**
     * Hands a harvested stack to the ProjectE core, which turns it into EMC.
     *
     * @return the part that has no EMC value and stays with the player
     */
    public static ItemStack depositProjectE(EntityPlayer player, ItemStack stack) {
        return Loader.isModLoaded(PROJECTE_MOD_ID) ? ProjectEProvider.deposit(player, stack) : stack;
    }

    /**
     * How many of {@code template} the EMC pays for, used to check whether an undo can take the
     * handed-over drops back.
     */
    public static int countProjectEEmc(EntityPlayer player, ItemStack template) {
        return Loader.isModLoaded(PROJECTE_MOD_ID) ? ProjectEProvider.count(player, template) : 0;
    }

    /**
     * @return the amount the EMC did not cover
     */
    public static int takeProjectEEmc(EntityPlayer player, ItemStack template, int amount) {
        return Loader.isModLoaded(PROJECTE_MOD_ID) ? ProjectEProvider.take(player, template, amount) : amount;
    }

    /**
     * How much EMC the player has, which is what the ProjectE core works from.
     *
     * @return the amount, or {@code -1} when ProjectE is not present or has no data for the player
     */
    public static long getProjectEEmc(EntityPlayer player) {
        return Loader.isModLoaded(PROJECTE_MOD_ID) ? ProjectEProvider.emc(player) : -1L;
    }

    private static final class BaublesProvider {
        private static final MaterialSourceFactory MATERIAL_SOURCE_FACTORY = new BaublesMaterialSourceFactory();
    }

    private static final class ProjectEProvider {
        private static final MaterialSourceFactory MATERIAL_SOURCE_FACTORY = new ProjectEMaterialSourceFactory();

        private static long emc(EntityPlayer player) {
            return ProjectEMaterialSourceFactory.playerEmc(player);
        }

        private static ItemStack deposit(EntityPlayer player, ItemStack stack) {
            return ProjectEMaterialSourceFactory.convertToEmc(player, stack);
        }

        private static int count(EntityPlayer player, ItemStack template) {
            return ProjectEMaterialSourceFactory.countEmcEquivalent(player, template);
        }

        private static int take(EntityPlayer player, ItemStack template, int amount) {
            return ProjectEMaterialSourceFactory.takeEmcEquivalent(player, template, amount);
        }
    }

    private static final class AE2Provider {
        private static final MaterialSourceFactory MATERIAL_SOURCE_FACTORY = AE2Compat.materialSourceFactory();

        private static boolean tryBind(ItemStack wand, EntityPlayer player, World world, BlockPos pos) {
            return AE2Compat.tryBind(wand, player, world, pos);
        }

        private static boolean hasBinding(ItemStack wand) {
            return AE2Compat.hasBinding(wand);
        }
    }
}
