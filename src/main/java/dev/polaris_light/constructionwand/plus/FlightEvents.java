package dev.polaris_light.constructionwand.plus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.plus.basics.PassiveUpgrades;

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
@EventBusSubscriber(modid = ConstructionWand.MODID)
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
     * Players who last asked for their flight to be off, meaning a double tap of their own and not the
     * fight switching it off. See {@code PlayerAbilitiesRequestMixin} for where this comes from.
     */
    private static final Set<UUID> WANTS_NO_FLIGHT = ConcurrentHashMap.newKeySet();

    private FlightEvents() {
    }

    /**
     * Runs before the entity tick. A player who just lost flight keeps a fall distance of zero until
     * they land, so that one fall is free.
     */
    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if(player.level().isClientSide) return;
        resetFallDistance(player);
    }

    /**
     * Runs last in the tick on purpose. A fight of the revelation kind turns the flight of everybody in
     * it off from its own tick handler, which runs before this one; granting the ability after that is
     * what keeps the upgrade working, and the ability itself is only ever granted, never taken away, so
     * nothing is stolen from another mod by doing it here.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if(player.level().isClientSide) return;
        updateFlight(player);
        keepFlying(player);
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
     * Records what a player asked for themselves, called from the packet that carries that request.
     */
    public static void noteRequest(Player player, boolean flying) {
        if(flying) WANTS_NO_FLIGHT.remove(player.getUUID());
        else WANTS_NO_FLIGHT.add(player.getUUID());
    }

    public static boolean guardsFlight(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Player player && GRANTED_FLIGHT.contains(player.getUUID());
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
    private static void keepFlying(Player player) {
        if(!(player instanceof ServerPlayer serverPlayer)) return;

        UUID id = serverPlayer.getUUID();
        boolean flying = serverPlayer.getAbilities().flying;
        // Known trade-off: while that lasts, a double tap that stops the flight is undone on the next
        // tick, so the flight can only be ended by leaving, or by the effect running out. Telling the
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

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GRANTED_FLIGHT.remove(id);
        FALL_PROTECTED.remove(id);
        WANTS_NO_FLIGHT.remove(id);
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
