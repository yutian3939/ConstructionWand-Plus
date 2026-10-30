package dev.polaris_light.constructionwand.plus.basics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.compat.apotheosis.ApotheosisBridge;
import dev.polaris_light.constructionwand.plus.compat.enchantinginfuser.InfuserBridge;

/**
 * The interaction of the apotheosis core: a right click on an enchanting table pins the four numbers the wand
 * screen shows onto that table, and applies the world tier to the player.
 * <p>
 * The click is taken rather than passed on, the way the time core takes its own: the table does not open, and the
 * numbers stay on it. From then on every time the table is opened the pinned numbers are written onto its menu,
 * so the offer is rolled with them whatever the shelves around it look like. Sneaking with the core removes the
 * pin again.
 * <p>
 * An infuser of Enchanting Infuser has no table to pin: its rank is written through the behaviour of that mod
 * instead, and its own screen still opens.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class ApotheosisCoreEvents
{
    /** The message a player gets on the action bar after a table was pinned or let go. */
    private static final boolean ACTION_BAR = true;

    /** What was last written onto the open table of a player, so the numbers are only sent when they change. */
    private static final Map<UUID, EnchantmentTableStats> SENT = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled() || !ApotheosisBridge.isLoaded()) return;

        Player player = event.getEntity();
        if(player == null) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!ItemWand.isApotheosisCore(wand)) return;

        Level touched = event.getLevel();
        BlockState state = touched.getBlockState(event.getPos());

        if(!ApotheosisBridge.isTable(state)) {
            // An infuser has its own screen and its own way of being told: it keeps the click, gets the rank.
            if(hasInfuser() && InfuserBridge.isInfuser(state)) {
                InfuserBridge.setRank(touched, event.getPos(), ApotheosisLevels.eterna(wand));
                if(player instanceof ServerPlayer serverPlayer) {
                    ApotheosisBridge.applyTier(serverPlayer, ApotheosisLevels.tier(wand));
                }
            }
            return;
        }

        // The table's numbers are pinned instead of opened: the click is taken, on both sides, so no screen is
        // predicted on the client either.
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if(!(touched instanceof ServerLevel level)) return;
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        if(!level.mayInteract(player, event.getPos())) return;

        pinTable(serverPlayer, level, event.getPos(), wand);
    }

    /**
     * Pins the numbers of a table, or lets them go when the player sneaks, and reports it on the action bar.
     */
    private static void pinTable(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack wand) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        ApotheosisPins pins = ApotheosisPins.get(player.server);

        if(player.isShiftKeyDown()) {
            boolean removed = pins.unpin(key);
            tell(player, Component.translatable(ConstructionWand.MODID + (removed
                    ? ".gui.apoth.unpinned" : ".gui.apoth.not_pinned")));
            return;
        }

        int rank = ApotheosisLevels.eterna(wand);
        int quanta = ApotheosisLevels.quanta(wand);
        int arcana = ApotheosisLevels.arcana(wand);
        int tier = ApotheosisLevels.tier(wand);

        pins.pin(key, rank, quanta, arcana);
        ApotheosisBridge.applyTier(player, tier);

        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                20, 0.4D, 0.4D, 0.4D, 0.05D);

        tell(player, Component.translatable(ConstructionWand.MODID + ".gui.apoth.pinned",
                rank, quanta, arcana, Component.translatable(ConstructionWand.MODID + ".apoth.tier."
                        + ApotheosisBridge.tierName(tier))));
    }

    /**
     * Keeps the pinned numbers on the open table: the table recomputes its own stats whenever the item in its slot
     * changes, so the pinned ones are written again right after that, and sent to the screen.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if(hasInfuser()) InfuserBridge.ensureInstalled();

        MinecraftServer server = event.getServer();
        if(server.getTickCount() % 4 != 0) return;

        for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            if(!ApotheosisBridge.hasTableOpen(player)) {
                SENT.remove(player.getUUID());
                continue;
            }

            BlockPos pos = ApotheosisBridge.menuPos(player);
            if(pos == null) continue;

            if(!(player.level() instanceof ServerLevel level)) continue;
            int[] pinned = ApotheosisPins.get(server).get(GlobalPos.of(level.dimension(), pos));
            if(pinned == null) {
                SENT.remove(player.getUUID());
                continue;
            }

            EnchantmentTableStats stats = ApotheosisBridge.statsFor(level, pos, pinned[0], pinned[1], pinned[2]);
            ApotheosisBridge.applyStats(player, stats);

            // Only a change is sent: the numbers of a table do not move while it is open.
            if(!stats.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), stats);
                ApotheosisBridge.sendStats(player, stats);
            }
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, ACTION_BAR);
    }

    /**
     * @return whether Enchanting Infuser is installed. Asked without touching that bridge, so a game without the
     * mod never has to load the classes of it.
     */
    private static boolean hasInfuser() {
        return ModList.get() != null && ModList.get().isLoaded(InfuserBridge.MODID);
    }
}