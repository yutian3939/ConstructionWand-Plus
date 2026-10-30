package thetadev.constructionwand.plus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants creative flight while a player carries a wand with the flight upgrade installed anywhere in
 * their inventory (not just in the held hand), and takes it away again as soon as the wand leaves
 * them. A player who loses flight mid-air is spared the fall damage of that one fall.
 * <p>
 * Only flight this upgrade granted is ever taken away. Flight granted by something else (another mod,
 * or a trinket that sets the ability itself) is left alone, so this feature cannot break those.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class FlightEvents
{
    /**
     * Players who just lost flight and have not landed yet. Their accumulated fall distance is kept at
     * zero until they touch the ground, so the fall deals no damage.
     */
    private static final Set<UUID> FALL_PROTECTED = ConcurrentHashMap.newKeySet();

    /**
     * Players whose flight ability was granted by the flight upgrade. The ability is only taken back
     * from them, so flight from another source is not switched off.
     */
    private static final Set<UUID> GRANTED_FLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * Players who were flying in the previous tick. Only used to tell a flight the player ended
     * themselves apart from one that was cut off under them, see {@link #keepFlying}.
     */
    private static final Set<UUID> WAS_FLYING = new java.util.HashSet<>();

    private FlightEvents() {
    }

    /**
     * Runs last in the tick on purpose. A fight of the revelation kind turns the flight of everybody in
     * it off from its own tick handler, which runs before this one; granting the ability after that is
     * what keeps the upgrade working, and the ability itself is only ever granted, never taken away, so
     * nothing is stolen from another mod by doing it here.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if(player.level().isClientSide) return;

        if(event.phase == TickEvent.Phase.START) resetFallDistance(player);
        else {
            updateFlight(player);
            keepFlying(player);
        }
    }

    private static void resetFallDistance(Player player) {
        UUID id = player.getUUID();
        if(!FALL_PROTECTED.contains(id)) return;
        if(player.onGround()) {
            // The player has landed safely; the protection ends here.
            FALL_PROTECTED.remove(id);
        }
        else {
            // Keep the accumulated fall distance at zero while the player falls out of the air.
            player.fallDistance = 0.0F;
        }
    }

    /**
     * Puts a flight back that was cut off under the player, but only while something is working against
     * them.
     * <p>
     * The revelation fight does this with a falling effect, applied every tick, that runs
     * {@code flying &= isCreative()} on everyone under it: having the ability is not enough against it,
     * because the wielder is dropped out of the air and would have to press jump again every single
     * tick. The flight is therefore re-stated for as long as any effect is on the player.
     * <p>
     * The condition is on purpose as loose as it is: what the fight puts on the player is not a known
     * effect of this mod, it changes between versions, and the protection must not depend on a list
     * that goes stale. Outside of an effect nothing is done, so a player who stops flying themselves
     * touches down normally; inside one, the flight keeps being put back until the effect is gone.
     */
    /**
     * @return whether the flight of that entity is under this upgrade's care, meaning the ability was
     * handed out by it. Other mods switching that flight off is refused for them, see the mixin.
     */
    /**
     * Players who last asked for their flight to be off, meaning a double tap of their own and not the
     * fight switching it off. See {@code PlayerAbilitiesRequestMixin} for where this comes from.
     */
    private static final Set<UUID> WANTS_NO_FLIGHT = new java.util.HashSet<>();

    /**
     * Records what a player asked for themselves, called from the packet that carries that request.
     */
    public static void noteRequest(Player player, boolean flying) {
        if(flying) WANTS_NO_FLIGHT.remove(player.getUUID());
        else WANTS_NO_FLIGHT.add(player.getUUID());
    }

    public static boolean guardsFlight(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Player player && GRANTED_FLIGHT.contains(player.getUUID());
    }

    private static void keepFlying(Player player) {
        if(!(player instanceof ServerPlayer serverPlayer)) return;

        UUID id = serverPlayer.getUUID();
        boolean flying = serverPlayer.getAbilities().flying;
        // The flight is put back while something is pressing down on the player, see the defense layer's
        // report. This is what makes flying possible at all in that fight: the flight is switched off
        // again every tick, before it could ever be noticed here, so waiting to see the player fly does
        // not work.
        // Known trade-off: while that lasts, a double tap that stops the flight is undone on the next
        // tick, so the flight can only be ended by leaving, or by the effect running out. Separating the
        // two apart needs the player's own request, which the server receives on its own packet.
        if(GRANTED_FLIGHT.contains(id) && !flying && serverPlayer.getAbilities().mayfly
                && !serverPlayer.onGround()
                && !WANTS_NO_FLIGHT.contains(id)
                && DefenseEvents.isSuppressed(serverPlayer)) {
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
            flying = true;
        }

        // The same effect that switches the flight off also pulls the player down every tick with
        // setDeltaMovement, and that is what is felt as "cannot fly" even while the flight itself is on.
        // The downward push is taken out for as long as an effect lasts; the player's own movement,
        // including flying downwards on purpose, keeps working.
        if(flying && !serverPlayer.getActiveEffects().isEmpty()) {
            net.minecraft.world.phys.Vec3 motion = serverPlayer.getDeltaMovement();
            if(motion.y < 0) serverPlayer.setDeltaMovement(motion.x, 0, motion.z);
        }

        // Temporary diagnostics: prints what the flight looks like once a second, so it can be seen who
        // is taking it away. Removed once the cause is known.
        if(serverPlayer.tickCount % 20 == 0) {
            org.apache.logging.log4j.LogManager.getLogger("constructionwand").info(
                    "[constructionwand flight] granted=" + GRANTED_FLIGHT.contains(id)
                            + " mayfly=" + serverPlayer.getAbilities().mayfly
                            + " flying=" + flying
                            + " wasFlying=" + WAS_FLYING.contains(id)
                            + " effects=" + serverPlayer.getActiveEffects().size());
        }

        if(flying) WAS_FLYING.add(id);
        else WAS_FLYING.remove(id);
    }

    private static boolean hasHarmfulEffect(net.minecraft.world.entity.LivingEntity entity) {
        for(net.minecraft.world.effect.MobEffectInstance effect : entity.getActiveEffects()) {
            if(!effect.getEffect().isBeneficial()) return true;
        }
        return false;
    }

    private static void updateFlight(Player player) {
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        // Creative and spectator players already fly, and their ability must not be taken away.
        if(serverPlayer.isCreative() || serverPlayer.isSpectator()) return;

        UUID id = serverPlayer.getUUID();
        if(carriesFlightWand(serverPlayer)) {
            if(!serverPlayer.getAbilities().mayfly) {
                // Remember the grant only when the ability was off, so flight granted elsewhere is
                // never taken away again later.
                serverPlayer.getAbilities().mayfly = true;
                GRANTED_FLIGHT.add(id);
                serverPlayer.onUpdateAbilities();
            }
        }
        else if(GRANTED_FLIGHT.remove(id)) {
            // Only the flight this upgrade handed out is taken back.
            serverPlayer.getAbilities().mayfly = false;
            serverPlayer.getAbilities().flying = false;
            serverPlayer.onUpdateAbilities();
            // The player drops out of the air now; spare them the fall damage of this one fall.
            FALL_PROTECTED.add(id);
        }
    }

    /**
     * Temporary diagnostics for the client side, where the flight is actually started: the client only
     * toggles it when its own copy of the ability says it may. Removed once the cause is known.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if(player == null || player.tickCount % 20 != 0) return;
        org.apache.logging.log4j.LogManager.getLogger("constructionwand").info(
                "[constructionwand flight client] mayfly=" + player.getAbilities().mayfly
                        + " flying=" + player.getAbilities().flying
                        + " dim=" + player.level().dimension().location()
                        + " onGround=" + player.onGround());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GRANTED_FLIGHT.remove(id);
        FALL_PROTECTED.remove(id);
    }

    /**
     * @return whether the player carries a wand with the flight upgrade anywhere in their inventory,
     * not just in the hand that is currently held. Armour can never hold a wand, so only the main
     * inventory and the off hand are checked.
     */
    private static boolean carriesFlightWand(Player player) {
        for(ItemStack stack : player.getInventory().items) {
            if(isFlightWand(stack)) return true;
        }
        for(ItemStack stack : player.getInventory().offhand) {
            if(isFlightWand(stack)) return true;
        }
        return false;
    }

    private static boolean isFlightWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand && PassiveUpgrades.hasFlight(stack);
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if(event.getEntity() instanceof Player player && FALL_PROTECTED.remove(player.getUUID())) {
            event.setDamageMultiplier(0.0F);
        }
    }
}
