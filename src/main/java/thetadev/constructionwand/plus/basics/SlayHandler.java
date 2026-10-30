package thetadev.constructionwand.plus.basics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.plus.DefenseEvents;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.plus.material.IMaterialCore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The slay core's killing logic.
 * <p>
 * A single kill happens synchronously on the server thread, so the drops and experience orbs the
 * victim spawns are caught through {@link EntityJoinLevelEvent} while the slay context is active,
 * rerouted to their configured destination and given to the player.
 * <p>
 * Because the slay core is meant to kill things that resist damage, the kill escalates: a normal
 * attack first, then a forced death that bypasses armour, shields, damage caps, immunity, damage
 * events and invulnerability frames, and finally removing the entity outright.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class SlayHandler
{
    private static final ThreadLocal<SlayContext> SLAYING = new ThreadLocal<>();

    /**
     * Victims whose experience drop is still pending. Vanilla drops experience some ticks after death,
     * long after the synchronous slay context above has been cleared, so the deferred drop is matched
     * against this map instead. Keys are weak so a victim that never drops experience cannot leak.
     */
    private static final Map<LivingEntity, Player> PENDING_XP = new WeakHashMap<>();

    /**
     * Killed targets that have to be held down because a protection puts their health back after the
     * kill, usually every tick. Killed players also end up here: the server ignores a respawn request
     * while the player still has health, so the health has to stay at zero until they actually respawn.
     */
    private static final Map<LivingEntity, Integer> DOOMED = new WeakHashMap<>();

    private static final int PLAYER_DOOM_TICKS = 1200;
    private static final int OTHER_DOOM_TICKS = 100;

    private SlayHandler() {
    }

    /**
     * Kills one living entity instantly and routes its drops and experience to the player.
     */
    public static void slaySingle(Player player, Entity target, ItemStack wand, WandOptions options) {
        if(player.level().isClientSide || !(target instanceof LivingEntity victim) || victim.isDeadOrDying()) return;
        // Players are only targeted when the config allows it, and never the wielder.
        if(victim instanceof Player && (victim == player || !ConfigServer.SLAY_AFFECT_PLAYERS.get())) return;
        // A player carrying the defense upgrade is out of reach for the slay core as well. It is the only
        // attack in the mod that walks around every protection by writing onto the entity directly, so
        // without this the wand would beat the very upgrade that is meant to be the answer to it. Mobs
        // are never protected, so they are unaffected.
        if(DefenseEvents.isProtected(victim)) return;

        SlayContext context = new SlayContext(player, victim, wand, options);
        SLAYING.set(context);
        boolean killed;
        try {
            killed = forceKill(victim, player);
        } finally {
            SLAYING.remove();
        }
        if(context.experience > 0) player.giveExperiencePoints(context.experience);
        if(!killed) return;

        // A per-tick protection may put the target's health back after the kill, so it is held down
        // from here on.
        DOOMED.put(victim, victim instanceof Player ? PLAYER_DOOM_TICKS : OTHER_DOOM_TICKS);
        // Only mobs drop experience through the vanilla deferred drop; players do not, so they are kept
        // out of the pending map.
        if(!(victim instanceof Player)) PENDING_XP.put(victim, player);

        if(context.silkTouch) dropSpawnEgg(context);
    }

    /**
     * Kills every living entity within {@code radius} blocks (Manhattan distance) of the player.
     */
    public static void slayRadius(Player player, ItemStack wand, WandOptions options, int radius) {
        Level level = player.level();
        if(level.isClientSide || radius <= 0) return;

        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
        for(LivingEntity entity : entities) {
            if(entity == player || entity.isDeadOrDying()) continue;
            if(manhattanDistance(player, entity) > radius) continue;
            slaySingle(player, entity, wand, options);
        }
    }

    private static int manhattanDistance(Entity first, Entity second) {
        return Math.abs(first.blockPosition().getX() - second.blockPosition().getX())
                + Math.abs(first.blockPosition().getY() - second.blockPosition().getY())
                + Math.abs(first.blockPosition().getZ() - second.blockPosition().getZ());
    }

    /**
     * Kills a target that may be protected by any combination of shields, damage caps, immunity,
     * invulnerability frames, death prevention or health locks.
     * <p>
     * It escalates instead of jumping straight to the most destructive option, so ordinary mobs keep
     * their normal death message, kill credit and full drops.
     * <p>
     * Every step is checked against {@code dead} rather than the health: a guarded death can leave a
     * target at zero health with its death path never having run, which is exactly the case the last
     * step exists for.
     *
     * @return whether the target is dead
     */
    private static boolean forceKill(LivingEntity victim, Player player) {
        if(victim.dead) return true;

        // 1) Ordinary attack. Armour, potion resistance and the normal death path all run, which is
        //    enough for regular mobs and keeps their drops, experience and kill credit intact.
        victim.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
        if(victim.dead) return true;

        // 2) Forced death: clear the health and run the death path directly. This bypasses every hurt
        //    override (shields, damage caps, invulnerability, immunity) as well as the attack and hurt
        //    events that some mods cancel. The source carries the player: bosses whose loot or death
        //    logic asks who killed them would otherwise drop nothing on this path.
        DamageSource bypass = player.damageSources().playerAttack(player);
        markHit(victim, bypass, player);
        victim.setHealth(0.0F);
        victim.die(bypass);
        if(victim.dead) return true;

        // 3) The victim's death path refused to run. A boss that guards its death returns from die before
        //    the loot is rolled, and the vanilla death tick would then remove it without ever dropping
        //    anything, so the death is carried out here instead, the way the sword of the cosmos does.
        forceDeath(victim, bypass);
        return true;
    }

    /**
     * Carries out the death a victim refused to take: the state a vanilla death leaves behind is set by
     * hand and the loot is rolled with it.
     * <p>
     * {@code dead} is what makes this safe to do at all. It marks the victim as dead, so the death path
     * of the entity cannot run afterwards and hand the same loot out a second time, and the vanilla
     * death tick starts removing it like any other corpse.
     */
    private static void forceDeath(LivingEntity victim, DamageSource source) {
        victim.dead = true;
        victim.getCombatTracker().recheckStatus();

        if(victim.level() instanceof ServerLevel level) {
            Entity killer = source.getEntity();
            if(killer == null || killer.killedEntity(level, victim)) {
                victim.gameEvent(GameEvent.ENTITY_DIE);
                victim.dropAllDeathLoot(source);
            }
            level.broadcastEntityEvent(victim, (byte) 3);
        }
        victim.setPose(Pose.DYING);
        // Taken out of the world here instead of being left to the death tick. A boss that refuses to die
        // is one that writes its own health and its dead flag back every tick, and a corpse that is still
        // in the world is a corpse it can do that to: the loot is rolled, the boss stands up again and
        // the fight continues. Once the entity is gone there is nothing left to put back.
        victim.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }

    /**
     * Marks the victim as recently hit by the player, so equipped mobs still drop their gear when the
     * forced death path skips the ordinary attack, and records the damage in its combat tracker so the
     * death message and kill attribution still make sense.
     */
    private static void markHit(LivingEntity victim, DamageSource source, Player player) {
        victim.setLastHurtByPlayer(player);
        victim.setLastHurtByMob(player);
        victim.getCombatTracker().recordDamage(source, Float.MAX_VALUE);
    }

    /**
     * With silk touch enabled the slay core additionally drops the victim's spawn egg (if that entity
     * has one), routed to the same destination as the rest of the drops.
     */
    private static void dropSpawnEgg(SlayContext context) {
        SpawnEggItem egg = SpawnEggItem.byId(context.target.getType());
        if(egg == null) return;
        ItemStack stack = new ItemStack(egg);
        DropDelivery.deliver(context.level, context.player, context.target.blockPosition(), stack,
                context.destination, context.materialCore, context.wand, null);
    }

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        SlayContext context = SLAYING.get();
        // The fortune upgrade acts as looting for the slay core.
        if(context != null && event.getEntity() == context.target) event.setLootingLevel(context.looting);
    }

    /**
     * Runs after every other death handler. If a target we are killing had its death prevented (a
     * charm of life, a second chance potion, a resurrection script, ...), the death is forced through
     * so the slay core still kills it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeathLowest(LivingDeathEvent event) {
        SlayContext context = SLAYING.get();
        if(context == null || event.getEntity() != context.target) return;
        if(event.isCanceled()) {
            event.setCanceled(false);
            event.getEntity().setHealth(0.0F);
        }
    }

    /**
     * Runs after every other attack handler. Some protections cancel the attack outright; for a target
     * the slay core is killing, the attack is let through so the normal damage and death path runs,
     * which keeps drops, kill credit and the death message intact.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingAttackLowest(LivingAttackEvent event) {
        SlayContext context = SLAYING.get();
        if(context != null && event.getEntity() == context.target && event.isCanceled()) event.setCanceled(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurtLowest(LivingHurtEvent event) {
        SlayContext context = SLAYING.get();
        if(context != null && event.getEntity() == context.target && event.isCanceled()) event.setCanceled(false);
    }

    /**
     * Runs after every other living update. A protection that restores health every tick would undo
     * the kill, so the health of a killed target is put back to zero. This also lets a killed player
     * respawn, because the server ignores a respawn request while the player still has health.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTickLowest(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        Integer remaining = DOOMED.get(entity);
        if(remaining == null) return;

        if(entity instanceof Player) {
            // A killed player has to be let go once the death has taken: the server ignores a respawn
            // request while the player still has health, and after respawning the health is put back,
            // which would look like a protection and would start the kill over again.
            if(entity.isDeadOrDying()) {
                DOOMED.remove(entity);
                return;
            }
            if(entity.getHealth() > 0.0F) entity.setHealth(0.0F);
        }
        else {
            // Anything else is held for as long as the hold lasts, including the death flag. The hold
            // used to stop as soon as the target was flagged dead, trusting the vanilla death tick to
            // finish the job; a boss that refuses to die clears that flag and puts its health back on
            // the same tick, which let it walk away from its own death after the loot had been rolled.
            if(!entity.dead) entity.dead = true;
            if(entity.getHealth() > 0.0F) entity.setHealth(0.0F);
        }

        if(remaining <= 1) DOOMED.remove(entity);
        else DOOMED.put(entity, remaining - 1);
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        Player owner = PENDING_XP.remove(event.getEntity());
        if(owner == null) return;
        // Take the experience away from the world and hand it to the player directly.
        owner.giveExperiencePoints(event.getDroppedExperience());
        event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        SlayContext context = SLAYING.get();
        if(context == null || event.getLevel().isClientSide) return;

        Entity entity = event.getEntity();
        if(entity instanceof ItemEntity item) {
            // A drop the delivery re-spawns (e.g. to the ground) must not be caught again.
            if(context.handling) return;

            ItemStack stack = item.getItem();
            if(context.autoSmelt) {
                ItemStack smelted = smelt(context.level, stack);
                if(!smelted.isEmpty()) stack = smelted;
            }
            context.handling = true;
            try {
                DropDelivery.deliver(context.level, context.player, item.blockPosition(), stack,
                        context.destination, context.materialCore, context.wand, null);
            } finally {
                context.handling = false;
            }
            event.setCanceled(true);
        }
        else if(entity instanceof ExperienceOrb orb) {
            context.experience += orb.getValue();
            event.setCanceled(true);
        }
    }

    private static ItemStack smelt(Level level, ItemStack stack) {
        try {
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).orElse(null);
            if(recipe == null) return ItemStack.EMPTY;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if(result.isEmpty()) return ItemStack.EMPTY;
            ItemStack smelted = result.copy();
            smelted.setCount(result.getCount() * stack.getCount());
            return smelted;
        } catch(RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Everything one kill needs to know, captured when the kill starts.
     */
    private static final class SlayContext
    {
        final Player player;
        final Level level;
        final LivingEntity target;
        final DropDestination destination;
        @Nullable
        final IMaterialCore materialCore;
        final ItemStack wand;
        final boolean autoSmelt;
        final boolean silkTouch;
        final int looting;
        int experience;
        boolean handling;

        SlayContext(Player player, LivingEntity target, ItemStack wand, WandOptions options) {
            this.player = player;
            this.level = player.level();
            this.target = target;
            this.destination = ExtraWandOption.getDestination(wand);
            this.materialCore = options.materialCores.get() instanceof IMaterialCore core ? core : null;
            this.wand = wand;
            this.autoSmelt = PassiveUpgrades.hasAutoSmelt(wand);
            this.silkTouch = ExtraWandOption.isSilkTouch(wand);
            this.looting = PassiveUpgrades.hasFortune(wand) ? PassiveUpgrades.fortuneLevel() : 0;
        }
    }
}
