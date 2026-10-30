package thetadev.constructionwand.plus;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The defense upgrade's invulnerability.
 * <p>
 * While a player carries a wand with the defense upgrade installed (anywhere in the main inventory or
 * the off hand, not just in the held hand) they take no damage, cannot die and are put back to full
 * health every tick. The attack, hurt and death events are cancelled at the highest priority so the
 * protection is in place before anything else looks at them, and the health is restored every tick so
 * damage that bypasses the events cannot stick either.
 * <p>
 * Like the other upgrades, an installed defense component can be switched off from the wand screen,
 * which also switches the protection off.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class DefenseEvents
{
    /**
     * Answer of {@link #isProtected} for the tick it was asked in, per player. Keys are weak so a
     * player that logs out cannot be kept alive by this.
     */
    private static final Map<Player, Cache> CACHE = new WeakHashMap<>();

    /**
     * Players that had a harmful effect on them when their tick was handled, meaning something is
     * working against them right now. The effects themselves are cleared further down, so this is the
     * only place that still knows about them; the flight upgrade asks here.
     */
    private static final java.util.Set<java.util.UUID> SUPPRESSED = new java.util.HashSet<>();

    private DefenseEvents() {
    }

    private record Cache(long tick, boolean protected_)
    {
    }

    /**
     * @return whether that entity is a player protected by a carried wand with the defense upgrade
     */
    public static boolean isProtected(Entity entity) {
        if(!(entity instanceof Player player)) return false;

        // On the client only the local player can be answered for: the inventory of any other player is
        // unknown here and their damage is decided by the server. The local player does have to be
        // answered for, because an attack that runs on both sides (a sword's hurtEnemy, for one) makes
        // the client play the death animation on its own even when the server refuses the kill.
        if(player.level().isClientSide && !isLocalPlayer(player)) return false;

        // The mixin layer asks this on every damage call, so the scan over the inventory is done once
        // per player and tick instead of once per call.
        long tick = player.level().getGameTime();
        Cache cache = CACHE.get(player);
        if(cache != null && cache.tick == tick) return cache.protected_;

        boolean protected_ = carriesProtection(player);
        CACHE.put(player, new Cache(tick, protected_));
        return protected_;
    }

    /**
     * @return whether that entity is a player the protection applies to. This is what the mixin layer
     * asks, and it asks the same question the events ask, on purpose: the two layers used to disagree
     * about one switch, which made the injected refusals silently do nothing while the events worked.
     */
    public static boolean blocksDamage(Entity entity) {
        return isProtected(entity);
    }

    /**
     * @return whether that player is the one this client controls. The client class is reached through
     * {@link DistExecutor} only, so a dedicated server never loads it.
     */
    private static boolean isLocalPlayer(Player player) {
        return DistExecutor.unsafeRunForDist(
                () -> () -> player == Minecraft.getInstance().player,
                () -> () -> false);
    }

    private static boolean carriesProtection(Player player) {
        for(ItemStack stack : player.getInventory().items) {
            if(carriesDefenseUpgrade(stack)) return true;
        }
        for(ItemStack stack : player.getInventory().offhand) {
            if(carriesDefenseUpgrade(stack)) return true;
        }
        return false;
    }

    private static boolean carriesDefenseUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand && PassiveUpgrades.hasDefense(stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if(isProtected(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if(isProtected(event.getEntity())) event.setCanceled(true);
    }

    /**
     * A protected player is not moved by hits they never took, so the knockback of an attack is refused
     * as well. The strongest weapons of the pack knock a target around even when the damage itself is
     * turned away, which reads as being hit.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if(isProtected(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if(!isProtected(entity)) return;
        entity.setHealth(entity.getMaxHealth());
        event.setCanceled(true);
    }

    /**
     * Restores the protected player every tick, at the lowest priority so it runs after everything else
     * in the tick. A boss that empties the health of everybody in the fight, every tick, is what this
     * ordering is for: restoring in the middle of the tick let the two of them trade the health back
     * and forth, which is seen as the bar being emptied and refilled over and over.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if(!isProtected(entity)) return;

        if(entity.getHealth() < entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        // The hurt state only drives the red flash and the camera tilt on the receiving side; an attack
        // that was refused still sent its animation, so it is cleared along with the health.
        if(entity.hurtTime > 0) entity.hurtTime = 0;
        // A death that was written straight onto the entity is undone too, the slay core marks that flag
        // by hand. Nothing else is meant to kill a protected player, so a flag that is set is either
        // stale, left over from before the upgrade was carried, or an attack the protection refused.
        if(entity.dead) entity.dead = false;
        if(entity.isOnFire()) entity.clearFire();
        if(entity instanceof Player player) {
            clearWandCooldowns(player);
            if(hasHarmfulEffect(entity)) SUPPRESSED.add(player.getUUID());
            else SUPPRESSED.remove(player.getUUID());
        }
        clearHarmfulEffects(entity);
    }

    /**
     * @return whether that player was under a harmful effect when their tick was handled. The effect
     * itself is gone by the time this is asked from outside, which is the point: it tells the flight
     * upgrade that the player is being pressed down right now, without depending on a list of effects
     * that has already been emptied here.
     */
    public static boolean isSuppressed(Entity entity) {
        return entity instanceof Player player && SUPPRESSED.contains(player.getUUID());
    }

    private static boolean hasHarmfulEffect(LivingEntity entity) {
        for(MobEffectInstance effect : entity.getActiveEffects()) {
            if(!effect.getEffect().isBeneficial()) return true;
        }
        return false;
    }

    /**
     * Keeps the wand usable while the protection is up.
     * <p>
     * The revelation fight puts an item cooldown on everything a player carries while the barrier is
     * standing, the same way the mod's own mobs do it to a shield, and an item on cooldown cannot be
     * used at all, which is what takes the wand away from the wielder. Cooldowns are held per item, so
     * the ones on a wand are dropped again; every other item is left on cooldown as the fight intends,
     * and the player's own cooldowns are untouched.
     */
    private static void clearWandCooldowns(Player player) {
        if(player.level().isClientSide) return;

        ItemCooldowns cooldowns = player.getCooldowns();
        for(ItemStack stack : player.getInventory().items) {
            if(stack.getItem() instanceof ItemWand) cooldowns.removeCooldown(stack.getItem());
        }
        for(ItemStack stack : player.getInventory().offhand) {
            if(stack.getItem() instanceof ItemWand) cooldowns.removeCooldown(stack.getItem());
        }
    }

    private static void clearHarmfulEffects(LivingEntity entity) {
        List<MobEffectInstance> harmful = null;
        for(MobEffectInstance effect : entity.getActiveEffects()) {
            if(!effect.getEffect().isBeneficial()) {
                if(harmful == null) harmful = new ArrayList<>();
                harmful.add(effect);
            }
        }
        if(harmful == null) return;
        for(MobEffectInstance effect : harmful) entity.removeEffect(effect.getEffect());
    }
}
