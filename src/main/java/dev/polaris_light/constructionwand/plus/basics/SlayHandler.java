package dev.polaris_light.constructionwand.plus.basics;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.ArrayList;
import dev.polaris_light.constructionwand.plus.wand.undo.DigSnapshot;
import dev.polaris_light.constructionwand.plus.basics.SuperFortuneLevels;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.plus.DefenseEvents;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.plus.material.IMaterialCore;

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
@EventBusSubscriber(modid = ConstructionWand.MODID)
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
            // The guaranteed loot is handed out here, while the slay context is still in place: the vanilla
            // death path rolls the normal loot, this adds one of everything else the table can produce.
            if(killed && player.level() instanceof ServerLevel killedIn) {
                dropEveryPossibleItem(victim, killedIn, killedIn.damageSources().playerAttack(player));
            }
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
     * Every step is checked against the death flag rather than the health: a guarded death can leave a
     * target at zero health with its death path never having run, which is exactly the case the last
     * step exists for.
     *
     * @return whether the target is dead
     */
    private static boolean forceKill(LivingEntity victim, Player player) {
        if(victim.isDeadOrDying()) return true;

        // 1) Ordinary attack. Armour, potion resistance and the normal death path all run, which is
        //    enough for regular mobs and keeps their drops, experience and kill credit intact.
        victim.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
        if(victim.isDeadOrDying()) return true;

        // 2) Forced death: clear the health and run the death path directly. This bypasses every hurt
        //    override (shields, damage caps, invulnerability, immunity) as well as the attack and hurt
        //    events that some mods cancel. The source carries the player: bosses whose loot or death
        //    logic asks who killed them would otherwise drop nothing on this path.
        DamageSource bypass = player.damageSources().playerAttack(player);
        markHit(victim, bypass, player);
        victim.setHealth(0.0F);
        victim.die(bypass);
        if(victim.isDeadOrDying()) return true;

        // 3) The victim's death path refused to run. A boss that guards its death returns from die before
        //    the loot is rolled, and the vanilla death tick would then remove it without ever dropping
        //    anything, so the death is carried out here instead, the way the sword of the cosmos does.
        forceDeath(victim, bypass);
        return true;
    }

    /**
     * Carries out the death a victim refused to take: the state a vanilla death leaves behind is set by
     * hand and the loot is rolled with it, under a temporary looting level so the fortune upgrade pays
     * out like it does on the ordinary path.
     * <p>
     * The death flag is what makes this safe to do at all. It marks the victim as dead, so the death path
     * of the entity cannot run afterwards and hand the same loot out a second time, and the vanilla
     * death tick starts removing it like any other corpse.
     */
    private static void forceDeath(LivingEntity victim, DamageSource source) {
        LootingBoost boost = LootingBoost.apply(victim);
        try {
            victim.dead = true;
            victim.getCombatTracker().recheckStatus();

            if(victim.level() instanceof ServerLevel level) {
                Entity killer = source.getEntity();
                if(killer == null || killer.killedEntity(level, victim)) {
                    victim.gameEvent(GameEvent.ENTITY_DIE);
                    rollLoot(victim, level, source);
                    dropEveryPossibleItem(victim, level, source);
                }
                level.broadcastEntityEvent(victim, (byte) 3);
            }
            victim.setPose(Pose.DYING);
            // Taken out of the world here instead of being left to the death tick. A boss that refuses to
            // die is one that writes its own health and its dead flag back every tick, and a corpse that
            // is still in the world is a corpse it can do that to: the loot is rolled, the boss stands up
            // again and the fight continues. Once the entity is gone there is nothing left to put back.
            victim.remove(Entity.RemovalReason.KILLED);
        } finally {
            boost.restore();
        }
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
     * Rolls the loot of the victim the way the vanilla death path does.
     * <p>
     * The method is protected and cannot be lifted by an access transformer: every subclass overrides it
     * with the same, protected visibility, and a public base method would turn all of those into illegal
     * visibility reductions. Reflection keeps the dispatching override intact, which is what the loot of
     * a special mob depends on.
     */
    private static final java.lang.reflect.Method DROP_ALL_DEATH_LOOT = findDropAllDeathLoot();

    @Nullable
    private static java.lang.reflect.Method findDropAllDeathLoot() {
        try {
            java.lang.reflect.Method method = LivingEntity.class.getDeclaredMethod(
                    "dropAllDeathLoot", ServerLevel.class, DamageSource.class);
            method.setAccessible(true);
            return method;
        } catch(ReflectiveOperationException | RuntimeException exception) {
            ConstructionWand.LOGGER.warn("Could not reach LivingEntity#dropAllDeathLoot; a forced kill will not roll loot", exception);
            return null;
        }
    }

    /**
     * Hands out one of every item the victim could possibly drop, on top of the normal roll, when the
     * guaranteed loot option is set. The list comes from reading the loot table ({@link LootTableWalk}),
     * which names a rare entry just as well as a common one; an entry type the walk does not know is
     * skipped without a word, the way a loot viewer does. Every item is delivered through the usual route,
     * so the super fortune factor and the destination still apply.
     */
    private static void dropEveryPossibleItem(LivingEntity victim, ServerLevel level, DamageSource source) {
        SlayContext context = SLAYING.get();
        if(context == null || !context.allLoot) return;
        if(!(level.getServer() != null)) return;

        LootTable table = level.getServer().reloadableRegistries().getLootTable(victim.getLootTable());
        List<ItemStack> found = LootTableWalk.possible(level, table);
        // Some mods hand their drops out from a death handler instead of a loot table; those are added here, or
        // the wand would silently skip them.
        for(ItemStack extra : SpecialDeathDrops.forVictim(victim)) {
            boolean known = false;
            for(ItemStack stack : found) {
                if(ItemStack.isSameItemSameComponents(stack, extra)) {
                    known = true;
                    break;
                }
            }
            if(!known) found.add(extra);
        }
        if(found.isEmpty()) return;

        DigSnapshot.multiplyDrops(found, SuperFortuneLevels.multiplier(context.wand));
        context.handling = true;
        try {
            for(ItemStack stack : found) {
                DropDelivery.deliver(level, context.player, victim.blockPosition(), stack,
                        context.destination, context.materialCore, context.wand, null);
            }
        } finally {
            context.handling = false;
        }
    }
    private static void rollLoot(LivingEntity victim, ServerLevel level, DamageSource source) {
        if(DROP_ALL_DEATH_LOOT == null) return;
        try {
            DROP_ALL_DEATH_LOOT.invoke(victim, level, source);
        } catch(ReflectiveOperationException | RuntimeException exception) {
            ConstructionWand.LOGGER.warn("Could not roll the loot of a forced kill", exception);
        }
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
     * Runs after every other damage handler. Some protections cancel the damage outright; for a target
     * the slay core is killing, the damage is let through so the normal damage and death path runs,
     * which keeps drops, kill credit and the death message intact.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingIncomingDamageLowest(LivingIncomingDamageEvent event) {
        SlayContext context = SLAYING.get();
        if(context != null && event.getEntity() == context.target && event.isCanceled()) event.setCanceled(false);
    }

    /**
     * Runs after every other living update. A protection that restores health every tick would undo
     * the kill, so the health of a killed target is put back to zero. This also lets a killed player
     * respawn, because the server ignores a respawn request while the player still has health.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTickLowest(EntityTickEvent.Post event) {
        if(DOOMED.isEmpty()) return;
        if(!(event.getEntity() instanceof LivingEntity entity)) return;
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
            // The super fortune upgrade copies the drop instead of rolling it: a mob that handed out one
            // bone hands out the chosen number of bones.
            List<ItemStack> stacks = new ArrayList<>();
            stacks.add(stack);
            DigSnapshot.multiplyDrops(stacks, SuperFortuneLevels.multiplier(context.wand));

            context.handling = true;
            try {
                for(ItemStack delivered : stacks) {
                    DropDelivery.deliver(context.level, context.player, item.blockPosition(), delivered,
                            context.destination, context.materialCore, context.wand, null);
                }
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
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
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
        final boolean allLoot;
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
            // The guaranteed drop belongs to the super fortune upgrade: without it the setting means nothing.
            this.allLoot = PassiveUpgrades.hasSuperFortune(wand)
                    && ExtraWandOption.getDisplayIndex(wand, ExtraWandOption.SLAY_ALL_LOOT) == 1;
        }
    }

    /**
     * The looting the fortune upgrade grants, handed to the loot roll of a forced death.
     * <p>
     * 1.21 reads the looting level off the enchantment on the attacker's weapon instead of a loot
     * context parameter, so there is no event left to set it on. The level is therefore put onto the
     * player's held item for the duration of the loot roll and taken off again right after: the wand
     * itself is restored, not replaced, so nothing else about it changes. Only the forcing path needs
     * this - an ordinary kill already carries the enchantment of the item it was dealt with.
     */
    private static final class LootingBoost
    {
        private final Player player;
        private final ItemStack original;

        private LootingBoost(Player player, ItemStack original) {
            this.player = player;
            this.original = original;
        }

        @Nullable
        static LootingBoost apply(LivingEntity victim) {
            SlayContext context = SLAYING.get();
            if(context == null || context.looting <= 0) return new LootingBoost(null, ItemStack.EMPTY);

            Player player = context.player;
            ItemStack held = player.getMainHandItem();
            if(held.isEmpty()) return new LootingBoost(null, ItemStack.EMPTY);

            try {
                Holder<Enchantment> looting = player.level().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.LOOTING);
                ItemStack boosted = held.copy();
                boosted.enchant(looting, Math.min(context.looting, 255));
                player.setItemInHand(InteractionHand.MAIN_HAND, boosted);
                return new LootingBoost(player, held);
            } catch(RuntimeException exception) {
                return new LootingBoost(null, ItemStack.EMPTY);
            }
        }

        void restore() {
            if(player == null || original.isEmpty()) return;
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
    }
}
