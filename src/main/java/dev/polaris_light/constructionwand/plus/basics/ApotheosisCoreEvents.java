package dev.polaris_light.constructionwand.plus.basics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.compat.apotheosis.ApotheosisBridge;
import dev.polaris_light.constructionwand.plus.compat.enchantinginfuser.InfuserBridge;

/**
 * The interaction of the apotheosis core: a right click on an enchanting table writes the world tier and the
 * three enchantment stats the wand screen shows onto that table.
 * <p>
 * The click itself is not taken. A table opens its own screen, and the numbers are written onto that screen as
 * soon as it is open, then kept there for a while: adding an item to the slot makes the table recompute its
 * stats from the shelves, which would otherwise drop the values between the click and the button press.
 * <p>
 * The tier is written straight away, since it belongs to the player rather than to the table, and Apotheosis
 * re-applies the tier augments of the new tier while doing so.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class ApotheosisCoreEvents
{
    /** How long the numbers stay on a table after the click: far more than one enchanting session. */
    private static final int HOLD_TICKS = 20 * 60;

    /**
     * A table a player has just written numbers onto, and how long they are to stay there.
     */
    private record Target(ResourceKey<Level> dimension, BlockPos pos, int until) {
    }

    private static final Map<UUID, Target> TARGETS = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled() || !ApotheosisBridge.isLoaded()) return;

        Player player = event.getEntity();
        if(player == null) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!ItemWand.isApotheosisCore(wand)) return;

        Level touched = event.getLevel();
        BlockState state = touched.getBlockState(event.getPos());

        // An infuser of Enchanting Infuser has no table to write onto: it is told a rank instead, which its own
        // power calculation then hands out instead of the shelves. It is a global behaviour, so the tick of
        // both sides keeps it in place while the mod can replace it.
        if(!ApotheosisBridge.isTable(state)) {
            if(hasInfuser() && InfuserBridge.isInfuser(state)) {
                InfuserBridge.setRank(touched, event.getPos(), ApotheosisLevels.eterna(wand));
                if(player instanceof ServerPlayer serverPlayer) {
                    ApotheosisBridge.applyTier(serverPlayer, ApotheosisLevels.tier(wand));
                }
            }
            return;
        }

        if(!(touched instanceof ServerLevel level)) return;
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        if(!level.mayInteract(player, event.getPos())) return;

        // The block still opens its own screen: this only prepares the numbers that go onto it.
        ApotheosisBridge.applyTier(serverPlayer, ApotheosisLevels.tier(wand));

        MinecraftServer server = serverPlayer.server;
        TARGETS.put(player.getUUID(),
                new Target(level.dimension(), event.getPos().immutable(), server.getTickCount() + HOLD_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // The infuser behaviour is a single static of its mod, so it is put back whenever the mod replaced it.
        if(hasInfuser()) InfuserBridge.ensureInstalled();
        if(TARGETS.isEmpty()) return;

        MinecraftServer server = event.getServer();
        TARGETS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Target target = entry.getValue();

            if(player == null) return true;
            if(server.getTickCount() > target.until()) return true;
            // The screen opens a tick or two after the click: waiting for it is what keeps this quiet.
            if(!ApotheosisBridge.hasTableOpen(player)) return false;

            ServerLevel level = ApotheosisBridge.level(player);
            if(level == null || !level.dimension().equals(target.dimension())) return true;

            ApotheosisBridge.applyStats(player, level, target.pos());
            return false;
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TARGETS.clear();
        if(hasInfuser()) InfuserBridge.clear();
    }

    /**
     * @return whether Enchanting Infuser is installed. Asked without touching the bridge, so that a game
     * without the mod never has to load the classes of it.
     */
    private static boolean hasInfuser() {
        return ModList.get() != null && ModList.get().isLoaded(InfuserBridge.MODID);
    }
}