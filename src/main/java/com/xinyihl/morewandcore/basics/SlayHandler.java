package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The slay core's killing logic.
 * <p>
 * A single kill happens synchronously on the server thread, so the drops and experience orbs the
 * victim spawns are caught through {@link EntityJoinWorldEvent} while the slay context is active,
 * rerouted to their configured destination and given to the player.
 * <p>
 * Because the slay core is meant to kill things that resist damage, the kill escalates: a normal
 * attack first, then a forced death that bypasses armour, shields, damage caps, immunity, damage
 * events and invulnerability frames, and finally removing the entity outright.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class SlayHandler {
    private static final ThreadLocal<SlayContext> SLAYING = new ThreadLocal<>();

    /**
     * Victims whose experience drop is still pending. Vanilla drops experience 20 ticks after death
     * (in {@code EntityLivingBase.onDeathUpdate}), long after the synchronous slay context above has
     * been cleared, so the deferred drop is matched against this map instead. Keys are weak so a
     * victim that never drops experience (e.g. a child) cannot leak.
     */
    private static final Map<EntityLivingBase, EntityPlayer> PENDING_XP = new WeakHashMap<>();

    /**
     * {@code EntityLivingBase.recentlyHit}, which marks the target as "recently hit" so equipped mobs
     * still drop their gear when the forced death path skips {@code attackEntityFrom}. The field is
     * protected, so it is reached reflectively; both the MCP name and the SRG name are tried so the
     * helper keeps working in a deobfuscated dev environment and in production.
     */
    @Nullable
    private static final Field RECENTLY_HIT = resolveRecentlyHit();

    private SlayHandler() {
    }

    /**
     * Kills one living entity instantly and routes its drops and experience to the player.
     */
    public static void slaySingle(EntityPlayer player, Entity target, ItemStack wand, WandState state) {
        if (player.world.isRemote || !(target instanceof EntityLivingBase) || ((EntityLivingBase) target).isDead) {
            return;
        }
        // Players are only targeted when the config allows it, and never the wielder.
        if (target instanceof EntityPlayer && (target == player || !ModConfig.slay.affectPlayers)) {
            return;
        }
        EntityLivingBase victim = (EntityLivingBase) target;
        SlayContext ctx = new SlayContext(player, victim, wand, state);
        SLAYING.set(ctx);
        boolean killed;
        try {
            killed = forceKill(victim, player);
        } finally {
            SLAYING.remove();
        }
        if (ctx.experience > 0) {
            player.addExperience(ctx.experience);
        }
        if (!killed) {
            return;
        }
        // Only mobs drop experience through the vanilla deferred drop; players do not, so they are
        // kept out of the pending map.
        if (!(victim instanceof EntityPlayer)) {
            PENDING_XP.put(victim, player);
        }
        if (ctx.silkTouch) {
            dropSpawnEgg(ctx);
        }
    }

    /**
     * Kills every living entity within {@code radius} blocks (Manhattan distance) of the player.
     */
    public static void slayRadius(EntityPlayer player, ItemStack wand, WandState state, int radius) {
        World world = player.world;
        if (world.isRemote || radius <= 0) {
            return;
        }
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius);
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase entity : entities) {
            if (entity != player && !entity.isDead && manhattanDistance(player, entity) <= radius) {
                slaySingle(player, entity, wand, state);
            }
        }
    }

    private static int manhattanDistance(Entity a, Entity b) {
        return Math.abs(MathHelper.floor(a.posX) - MathHelper.floor(b.posX))
                + Math.abs(MathHelper.floor(a.posY) - MathHelper.floor(b.posY))
                + Math.abs(MathHelper.floor(a.posZ) - MathHelper.floor(b.posZ));
    }

    /**
     * Kills a target that may be protected by any combination of shields, damage caps, immunity,
     * invulnerability frames, death prevention or health locks.
     * <p>
     * It escalates instead of jumping straight to the most destructive option, so ordinary mobs keep
     * their normal death message, kill credit and full drops.
     *
     * @return whether the target ends up dead
     */
    private static boolean forceKill(EntityLivingBase victim, EntityPlayer player) {
        if (isDead(victim)) {
            return true;
        }

        // 1) Ordinary attack. Armour, potion resistance and the normal death path all run, which is
        //    enough for regular mobs and keeps their drops, experience and kill credit intact.
        victim.attackEntityFrom(playerDamage(player), Float.MAX_VALUE);
        if (isDead(victim)) {
            return true;
        }

        // 2) Forced death: clear the health and run onDeath directly. This bypasses every
        //    attackEntityFrom override (shields, damage caps, invulnerability, immunity) as well as
        //    the LivingAttackEvent / LivingHurtEvent that some mods cancel.
        DamageSource bypass = bypassDamage(player);
        markHit(victim, bypass);
        victim.setHealth(0.0F);
        if (victim.getHealth() <= 0.0F) {
            victim.onDeath(bypass);
        }
        if (isDead(victim)) {
            return true;
        }

        // 3) Last resort: pull the entity out of the world, which ignores health locks and any
        //    remaining death prevention.
        victim.setDead();
        return isDead(victim);
    }

    /**
     * Marks the victim as recently hit (so equipped mobs drop their gear) and records the damage in
     * its combat tracker (so the death message and kill attribution still make sense).
     */
    private static void markHit(EntityLivingBase victim, DamageSource source) {
        if (RECENTLY_HIT != null) {
            try {
                RECENTLY_HIT.setInt(victim, 100);
            } catch (IllegalAccessException ignored) {
                // The field could not be written; the drops just lose the "recently hit" bonus.
            }
        }
        victim.getCombatTracker().trackDamage(source, Float.MAX_VALUE, Float.MAX_VALUE);
    }

    private static boolean isDead(EntityLivingBase entity) {
        return entity.isDead || entity.getHealth() <= 0.0F;
    }

    /**
     * A plain player attack that ignores armour and potion resistance, used for the first attempt so
     * ordinary kills keep their normal death message and kill credit.
     */
    private static DamageSource playerDamage(EntityPlayer player) {
        return new EntityDamageSource("player", player)
                .setDamageBypassesArmor()
                .setDamageIsAbsolute()
                .setDamageAllowedInCreativeMode();
    }

    /**
     * A fully bypassing attack used for the forced death. The {@code infinity} damage type is the one
     * Avaritia's own sword uses to get through its full infinity-armour immunity.
     */
    private static DamageSource bypassDamage(EntityPlayer player) {
        return new EntityDamageSource("infinity", player)
                .setDamageBypassesArmor()
                .setDamageIsAbsolute()
                .setDamageAllowedInCreativeMode();
    }

    /**
     * With silk touch enabled the slay core additionally drops the victim's spawn egg (if that entity
     * has one), routed to the same destination as the rest of the drops.
     */
    private static void dropSpawnEgg(SlayContext ctx) {
        ResourceLocation id = EntityList.getKey(ctx.target);
        if (id == null || !EntityList.ENTITY_EGGS.containsKey(id)) {
            return;
        }
        ItemStack egg = new ItemStack(Items.SPAWN_EGG);
        ItemMonsterPlacer.applyEntityIdToItemStack(egg, id);
        DropDelivery.deliver(ctx.world, ctx.player, new BlockPos(ctx.target.posX, ctx.target.posY, ctx.target.posZ), egg, ctx.destination, ctx.materialCore, ctx.wand, null);
    }

    @Nullable
    private static Field resolveRecentlyHit() {
        for (String name : new String[]{"recentlyHit", "field_70718_bc"}) {
            try {
                Field field = EntityLivingBase.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try the next known name
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        SlayContext ctx = SLAYING.get();
        if (ctx != null && event.getEntityLiving() == ctx.target) {
            // The fortune upgrade acts as looting for the slay core.
            event.setLootingLevel(ctx.looting);
        }
    }

    /**
     * Runs after every other death handler. If a target we are killing had its death prevented (a
     * charm of life, a second chance potion, a resurrection script, ...), the death is forced through
     * so the slay core still kills it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeathLowest(LivingDeathEvent event) {
        SlayContext ctx = SLAYING.get();
        if (ctx == null || event.getEntityLiving() != ctx.target) {
            return;
        }
        if (event.isCanceled()) {
            event.setCanceled(false);
            event.getEntityLiving().setHealth(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        EntityPlayer owner = PENDING_XP.remove(event.getEntityLiving());
        if (owner != null) {
            // Take the experience away from the world and hand it to the player directly.
            owner.addExperience(event.getDroppedExperience());
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        SlayContext ctx = SLAYING.get();
        if (ctx == null || event.getWorld().isRemote) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EntityItem) {
            // A drop the delivery re-spawns (e.g. to the ground) must not be caught again.
            if (ctx.handling) {
                return;
            }
            EntityItem item = (EntityItem) entity;
            ItemStack stack = item.getItem();
            if (ctx.autoSmelt) {
                ItemStack smelted = FurnaceRecipes.instance().getSmeltingResult(stack);
                if (!smelted.isEmpty()) {
                    ItemStack result = smelted.copy();
                    result.setCount(smelted.getCount() * stack.getCount());
                    stack = result;
                }
            }
            ctx.handling = true;
            try {
                DropDelivery.deliver(ctx.world, ctx.player, new BlockPos(item.posX, item.posY, item.posZ), stack, ctx.destination, ctx.materialCore, ctx.wand, null);
            } finally {
                ctx.handling = false;
            }
            event.setCanceled(true);
        } else if (entity instanceof EntityXPOrb) {
            ctx.experience += ((EntityXPOrb) entity).getXpValue();
            event.setCanceled(true);
        }
    }

    private static final class SlayContext {
        final EntityPlayer player;
        final World world;
        final EntityLivingBase target;
        final DropDestination destination;
        final IWandCore materialCore;
        final ItemStack wand;
        final boolean autoSmelt;
        final boolean silkTouch;
        final int looting;
        int experience;
        boolean handling;

        SlayContext(EntityPlayer player, EntityLivingBase target, ItemStack wand, WandState state) {
            this.player = player;
            this.world = player.world;
            this.target = target;
            this.destination = ExtraWandOption.getDestination(wand);
            this.materialCore = state.getSelectedMaterialCore();
            this.wand = wand;
            this.autoSmelt = WandUpgrades.hasAutoSmelt(wand);
            this.silkTouch = ExtraWandOption.isSilkTouch(wand);
            this.looting = WandUpgrades.hasFortune(wand) ? WandUpgrades.fortuneLevel() : 0;
        }
    }
}
