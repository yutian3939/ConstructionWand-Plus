package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.ConstructionWandLegacy;
import com.xinyihl.constructionwandlegacy.Tags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Collects the items and fluids a refused undo is missing and reports them as one chat line each.
 * <p>
 * One undo attempt touches every block of the transaction, so a naive per block message produced
 * one line each. The reports are merged per player and flushed once at the end of the server tick,
 * which turns eight lines of "ardite ore x1" into one line of "ardite ore x8". The flush is driven
 * by the server tick instead of a scheduled task so it cannot be missed.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class UndoFeedback {
    private static final Map<UUID, List<ItemStack>> MISSING = new LinkedHashMap<>();
    private static final Map<UUID, List<FluidStack>> MISSING_FLUID = new LinkedHashMap<>();
    private static final Map<UUID, EntityPlayerMP> PLAYERS = new HashMap<>();

    private UndoFeedback() {
    }

    public static void report(EntityPlayer player, ItemStack missing) {
        if (!(player instanceof EntityPlayerMP) || missing == null || missing.isEmpty()) {
            return;
        }
        List<ItemStack> collected = MISSING.computeIfAbsent(known(player), ignored -> new ArrayList<>());
        merge(collected, missing);
    }

    /**
     * Reports fluid a refused undo would have needed from the wand's store.
     */
    public static void reportFluid(EntityPlayer player, FluidStack missing) {
        if (!(player instanceof EntityPlayerMP) || missing == null || missing.getFluid() == null || missing.amount <= 0) {
            return;
        }
        List<FluidStack> collected = MISSING_FLUID.computeIfAbsent(known(player), ignored -> new ArrayList<>());
        for (FluidStack existing : collected) {
            if (existing.getFluid() == missing.getFluid()) {
                existing.amount += missing.amount;
                return;
            }
        }
        collected.add(missing.copy());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (MISSING.isEmpty() && MISSING_FLUID.isEmpty())) {
            return;
        }
        Set<UUID> playerIds = new LinkedHashSet<>(MISSING.keySet());
        playerIds.addAll(MISSING_FLUID.keySet());
        for (UUID playerId : playerIds) {
            EntityPlayerMP player = PLAYERS.remove(playerId);
            List<ItemStack> items = MISSING.remove(playerId);
            List<FluidStack> fluids = MISSING_FLUID.remove(playerId);
            if (player == null) {
                continue;
            }
            if (items != null && !items.isEmpty()) {
                String summary = summarize(items);
                if (ConstructionWandLegacy.LOGGER != null) {
                    ConstructionWandLegacy.LOGGER.info("Refused a digging undo for {}, still missing {}", player.getName(), summary);
                }
                player.sendMessage(new TextComponentTranslation(Tags.MOD_ID + ".undo.not_enough_items", summary));
            }
            if (fluids != null && !fluids.isEmpty()) {
                String summary = summarizeFluid(fluids);
                if (ConstructionWandLegacy.LOGGER != null) {
                    ConstructionWandLegacy.LOGGER.info("Refused a digging undo for {}, still missing {}", player.getName(), summary);
                }
                player.sendMessage(new TextComponentTranslation(Tags.MOD_ID + ".undo.not_enough_fluid", summary));
            }
        }
        PLAYERS.clear();
    }

    /**
     * Registers the player the report belongs to and returns their id.
     */
    private static UUID known(EntityPlayer player) {
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        PLAYERS.put(serverPlayer.getUniqueID(), serverPlayer);
        return serverPlayer.getUniqueID();
    }

    private static String summarizeFluid(List<FluidStack> collected) {
        StringBuilder summary = new StringBuilder();
        for (FluidStack fluid : collected) {
            if (summary.length() > 0) {
                summary.append("§7, ");
            }
            summary.append("§f").append(fluid.getLocalizedName()).append(" §7").append(fluid.amount).append(" mB");
        }
        return summary.toString();
    }

    private static String summarize(List<ItemStack> collected) {
        StringBuilder summary = new StringBuilder();
        for (ItemStack stack : collected) {
            if (summary.length() > 0) {
                summary.append("§7, ");
            }
            summary.append("§f").append(stack.getDisplayName()).append(" §7x").append(stack.getCount());
        }
        return summary.toString();
    }

    private static void merge(List<ItemStack> collected, ItemStack missing) {
        for (ItemStack existing : collected) {
            if (ItemStack.areItemsEqual(existing, missing) && ItemStack.areItemStackTagsEqual(existing, missing)) {
                existing.grow(missing.getCount());
                return;
            }
        }
        collected.add(missing.copy());
    }
}
