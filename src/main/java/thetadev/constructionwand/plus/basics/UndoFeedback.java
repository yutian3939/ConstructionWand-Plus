package thetadev.constructionwand.plus.basics;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;

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
 * One undo attempt touches every block of the transaction, so a naive per block message produced one
 * line each. The reports are merged per player and flushed once at the end of the server tick, which
 * turns eight lines of "ardite ore x1" into one line of "ardite ore x8". The flush is driven by the
 * server tick instead of a scheduled task so it cannot be missed.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class UndoFeedback
{
    private static final Map<UUID, List<ItemStack>> MISSING = new LinkedHashMap<>();
    private static final Map<UUID, List<FluidStack>> MISSING_FLUID = new LinkedHashMap<>();
    private static final Map<UUID, ServerPlayer> PLAYERS = new HashMap<>();

    private UndoFeedback() {
    }

    public static void report(Player player, ItemStack missing) {
        if(!(player instanceof ServerPlayer serverPlayer) || missing == null || missing.isEmpty()) return;
        merge(MISSING.computeIfAbsent(known(serverPlayer), ignored -> new ArrayList<>()), missing);
    }

    /**
     * Reports fluid a refused undo would have needed from the wand's store.
     */
    public static void reportFluid(Player player, FluidStack missing) {
        if(!(player instanceof ServerPlayer serverPlayer) || missing == null || missing.isEmpty()) return;
        List<FluidStack> collected = MISSING_FLUID.computeIfAbsent(known(serverPlayer), ignored -> new ArrayList<>());
        for(FluidStack existing : collected) {
            if(existing.isFluidEqual(missing)) {
                existing.setAmount(existing.getAmount() + missing.getAmount());
                return;
            }
        }
        collected.add(missing.copy());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if(event.phase != TickEvent.Phase.END || (MISSING.isEmpty() && MISSING_FLUID.isEmpty())) return;

        Set<UUID> playerIds = new LinkedHashSet<>(MISSING.keySet());
        playerIds.addAll(MISSING_FLUID.keySet());
        for(UUID playerId : playerIds) {
            ServerPlayer player = PLAYERS.remove(playerId);
            List<ItemStack> items = MISSING.remove(playerId);
            List<FluidStack> fluids = MISSING_FLUID.remove(playerId);
            if(player == null) continue;

            if(items != null && !items.isEmpty()) {
                String summary = summarize(items);
                ConstructionWand.LOGGER.info("Refused a digging undo for {}, still missing {}", player.getName().getString(), summary);
                player.displayClientMessage(Component.translatable(ConstructionWand.MODID + ".undo.not_enough_items", summary), false);
            }
            if(fluids != null && !fluids.isEmpty()) {
                String summary = summarizeFluid(fluids);
                ConstructionWand.LOGGER.info("Refused a digging undo for {}, still missing {}", player.getName().getString(), summary);
                player.displayClientMessage(Component.translatable(ConstructionWand.MODID + ".undo.not_enough_fluid", summary), false);
            }
        }
        PLAYERS.clear();
    }

    /**
     * Registers the player the report belongs to and returns their id.
     */
    private static UUID known(ServerPlayer player) {
        PLAYERS.put(player.getUUID(), player);
        return player.getUUID();
    }

    private static String summarizeFluid(List<FluidStack> collected) {
        StringBuilder summary = new StringBuilder();
        for(FluidStack fluid : collected) {
            if(summary.length() > 0) summary.append("§7, ");
            summary.append("§f").append(fluid.getDisplayName().getString()).append(" §7").append(fluid.getAmount()).append(" mB");
        }
        return summary.toString();
    }

    private static String summarize(List<ItemStack> collected) {
        StringBuilder summary = new StringBuilder();
        for(ItemStack stack : collected) {
            if(summary.length() > 0) summary.append("§7, ");
            summary.append("§f").append(stack.getHoverName().getString()).append(" §7x").append(stack.getCount());
        }
        return summary.toString();
    }

    private static void merge(List<ItemStack> collected, ItemStack missing) {
        for(ItemStack existing : collected) {
            if(ItemStack.isSameItemSameTags(existing, missing)) {
                existing.grow(missing.getCount());
                return;
            }
        }
        collected.add(missing.copy());
    }
}
