package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.ChatFormatting;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.network.PacketTimeAcceleration;

/**
 * The interaction of the time core: a right click on a block accelerates it with the speed and the
 * duration the wand is set to, and the server tick that keeps every accelerated block running.
 * <p>
 * The click is taken here rather than from the item's {@code useOn}, because the block in front of the
 * player would otherwise get the click first: a furnace or a chest opens its screen and the wand never
 * hears about the interaction, which is exactly the blocks a player wants to speed up. Consuming the
 * event also keeps the container from opening.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class TimeCoreEvents
{
    private TimeCoreEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled()) return;

        Player player = event.getEntity();
        if(player == null) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!ItemWand.isTimeCore(wand)) return;

        // Consume the click on both sides, the work below happens on the server only. The client has to
        // let go of it as well, or it would predict the block's own use and open its screen locally.
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if(!(player instanceof ServerPlayer serverPlayer)) return;
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        // Respecting the world protection of the area: a machine inside a protected region is not sped
        // up either, and nothing is reported so the wand stays silent about it.
        if(!level.mayInteract(player, event.getPos())) return;

        // Sneaking is the opposite action here, the way it is for the bindings: it lets the block go.
        // The key itself is read rather than the crouch pose, which is only set a tick later and not at all
        // while flying or riding.
        if(player.isShiftKeyDown()) {
            TimeAcceleration.Result removed = TimeAcceleration.remove(level, event.getPos());
            reportRemoval(serverPlayer, level, event.getPos(), removed);
            if(removed == TimeAcceleration.Result.REMOVED) broadcast();
            return;
        }

        accelerate(serverPlayer, level, event.getPos(), false);
    }

    /**
     * Accelerates one block, or every block of the same body when {@code connected} is asked for. The
     * settings come from the wand that is held, so a connected click gives every block the speed and the
     * duration that the screen shows.
     *
     * @return how many blocks were accelerated
     */
    public static int accelerate(ServerPlayer player, ServerLevel level, BlockPos pos, boolean connected) {
        ItemStack wand = player.getMainHandItem();
        if(!ItemWand.isTimeCore(wand)) return 0;
        if(!level.mayInteract(player, pos)) return 0;

        int rate = selectedRate(wand);
        int duration = selectedDuration(wand);
        if(!connected) {
            TimeAcceleration.Outcome outcome = TimeAcceleration.apply(player, level, pos, rate, duration);
            report(player, level, pos, outcome);
            if(outcome.result() == TimeAcceleration.Result.APPLIED) broadcast();
            return outcome.result() == TimeAcceleration.Result.APPLIED ? 1 : 0;
        }

        List<BlockPos> blocks = TimeAcceleration.connected(level, pos,
                ConfigServer.TIME_CORE_CONNECTED_LIMIT.get());
        int applied = 0;
        for(BlockPos block : blocks) {
            if(!level.mayInteract(player, block)) continue;
            if(TimeAcceleration.apply(player, level, block, rate, duration).result()
                    == TimeAcceleration.Result.APPLIED) applied++;
        }
        if(applied > 0) {
            player.displayClientMessage(Component.translatable(ConstructionWand.MODID + ".time.connected",
                    applied), true);
            broadcast();
        }
        return applied;
    }

    /**
     * @return the multiplier the wand is set to, which is what the screen shows as well
     */
    private static int selectedRate(ItemStack wand) {
        int index = ExtraWandOption.getDisplayIndex(wand, ExtraWandOption.TIME_MULTIPLIER);
        return TimeCoreLevels.rateOf(ExtraWandOption.getValueAt(ExtraWandOption.TIME_MULTIPLIER, index));
    }

    /**
     * @return how many ticks one application lasts, or {@code -1} for permanent
     */
    private static int selectedDuration(ItemStack wand) {
        int index = ExtraWandOption.getDisplayIndex(wand, ExtraWandOption.TIME_DURATION);
        return TimeCoreLevels.durationTicksOf(ExtraWandOption.getValueAt(ExtraWandOption.TIME_DURATION, index));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if(TimeAcceleration.tick(event.getServer())) broadcast();
    }

    /**
     * Opens the table of the save, which is what puts the accelerations of the last session back in place,
     * and tells the table to mark itself as changed whenever a block is added or taken away.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        TimeAccelerationSave.of(overworld);
        TimeAcceleration.setDirtyListener(TimeAccelerationSave.dirtyMarker(overworld));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        // The world save has just run; this makes sure the table went with it before it is let go.
        TimeAccelerationSave.of(overworld).setDirty();
        overworld.getDataStorage().save();

        TimeAcceleration.setDirtyListener(null);
        TimeAcceleration.clear();
    }

    /**
     * A player who joins or travels needs the table of the dimension they arrive in, and sending the whole
     * table keeps this simple: it is short, and it only travels when something changes.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) send(player);
    }

    private static void send(ServerPlayer player) {
        player.connection.send(new PacketTimeAcceleration(TimeAcceleration.snapshot()));
    }

    /**
     * Tells every client what is accelerated right now. Used whenever the table changes.
     */
    public static void broadcast() {
        PacketDistributor.sendToAllPlayers(new PacketTimeAcceleration(TimeAcceleration.snapshot()));
    }

    private static void report(ServerPlayer player, ServerLevel level, BlockPos pos, TimeAcceleration.Outcome outcome) {
        String key = ConstructionWand.MODID + ".time.";
        Block block = level.getBlockState(pos).getBlock();

        switch(outcome.result()) {
            case APPLIED -> {
                MutableComponent message = outcome.permanent()
                        ? Component.translatable(key + "applied_permanent", block.getName(), outcome.rate())
                        : Component.translatable(key + "applied", block.getName(), outcome.rate(), outcome.seconds());
                player.displayClientMessage(message.withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, pitch(outcome.rate()));
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        6, 0.3, 0.3, 0.3, 0.01);
            }
            case INVALID -> player.displayClientMessage(Component.translatable(key + "invalid",
                    block.getName()).withStyle(ChatFormatting.GRAY), true);
            case LIMITED -> player.displayClientMessage(Component.translatable(key + "limited",
                            ConfigServer.TIME_CORE_MAX_ACTIVE.get())
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void reportRemoval(ServerPlayer player, ServerLevel level, BlockPos pos, TimeAcceleration.Result result) {
        String key = ConstructionWand.MODID + ".time.";
        Block block = level.getBlockState(pos).getBlock();

        switch(result) {
            case REMOVED -> {
                player.displayClientMessage(Component.translatable(key + "removed", block.getName())
                        .withStyle(ChatFormatting.GRAY), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
            case NOT_ACCELERATED -> player.displayClientMessage(Component.translatable(key + "not_accelerated",
                    block.getName()).withStyle(ChatFormatting.GRAY), true);
            default -> {
            }
        }
    }

    /**
     * Higher multipliers ring higher, so the setting can be heard without reading the message.
     */
    private static float pitch(int rate) {
        for(int index = 0; index < TimeCoreLevels.LADDER.length; index++) {
            if(TimeCoreLevels.LADDER[index] == rate) return 0.7F + 0.15F * index;
        }
        return 1.0F;
    }
}
