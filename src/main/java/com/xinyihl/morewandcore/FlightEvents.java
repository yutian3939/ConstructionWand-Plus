package com.xinyihl.morewandcore;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.morewandcore.basics.WandUpgrades;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants creative flight while a player carries a wand with the flight upgrade installed anywhere in
 * their inventory (not just in the held hand), and takes it away again as soon as the wand leaves
 * them. A player who loses flight mid-air is spared the fall damage of that one fall.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class FlightEvents {
    /**
     * Players who just lost flight and have not landed yet. Their accumulated fall distance is kept
     * at zero until they touch the ground, so the fall deals no damage.
     */
    private static final Set<UUID> FALL_PROTECTED = ConcurrentHashMap.newKeySet();

    private FlightEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player.world.isRemote) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            resetFallDistance(event.player);
        } else {
            updateFlight(event.player);
        }
    }

    private static void resetFallDistance(EntityPlayer player) {
        if (!FALL_PROTECTED.contains(player.getUniqueID())) {
            return;
        }
        if (player.onGround) {
            // The player has landed safely; the protection ends here.
            FALL_PROTECTED.remove(player.getUniqueID());
        } else {
            // Keep the accumulated fall distance at zero while the player falls out of the air, so
            // the landing deals no damage.
            player.fallDistance = 0.0F;
        }
    }

    private static void updateFlight(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        // Creative and spectator players already fly, and their ability must not be taken away.
        if (serverPlayer.capabilities.isCreativeMode || serverPlayer.isSpectator()) {
            return;
        }
        boolean flight = carriesFlightWand(serverPlayer);
        if (flight) {
            if (!serverPlayer.capabilities.allowFlying) {
                serverPlayer.capabilities.allowFlying = true;
                serverPlayer.sendPlayerAbilities();
            }
        } else if (serverPlayer.capabilities.allowFlying) {
            serverPlayer.capabilities.allowFlying = false;
            serverPlayer.capabilities.isFlying = false;
            serverPlayer.sendPlayerAbilities();
            // The player drops out of the air now; spare them the fall damage of this one fall.
            FALL_PROTECTED.add(serverPlayer.getUniqueID());
        }
    }

    /**
     * @return whether the player carries a wand with the flight upgrade anywhere in their inventory,
     * not just in the hand that is currently held. Armour can never hold a wand, so only the main
     * inventory and the off hand are checked.
     */
    private static boolean carriesFlightWand(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isFlightWand(stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isFlightWand(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFlightWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand && WandUpgrades.hasFlight(stack);
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof EntityPlayer && FALL_PROTECTED.remove(event.getEntity().getUniqueID())) {
            event.setDamageMultiplier(0.0F);
        }
    }
}
