package com.xinyihl.morewandcore.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The slay core's killing logic. A single kill happens synchronously on the server thread, so the
 * drops and experience orbs the victim spawns are caught through {@link EntityJoinWorldEvent} while
 * the slay context is active, rerouted to their configured destination and given to the player.
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

    private SlayHandler() {
    }

    /**
     * Kills one living entity instantly and routes its drops and experience to the player.
     */
    public static void slaySingle(EntityPlayer player, Entity target, ItemStack wand, WandState state) {
        if (player.world.isRemote || !(target instanceof EntityLivingBase) || target instanceof EntityPlayer || ((EntityLivingBase) target).isDead) {
            return;
        }
        EntityLivingBase victim = (EntityLivingBase) target;
        SlayContext ctx = new SlayContext(player, victim, wand, state);
        SLAYING.set(ctx);
        try {
            victim.attackEntityFrom(DamageSource.causePlayerDamage(player), Float.MAX_VALUE);
        } finally {
            SLAYING.remove();
        }
        if (ctx.experience > 0) {
            player.addExperience(ctx.experience);
        }
        // The vanilla experience drop is deferred to onDeathUpdate (20 ticks later), so remember the
        // victim for onLivingExperienceDrop instead of relying on the now-cleared thread local.
        PENDING_XP.put(victim, player);
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
        List<EntityLiving> entities = world.getEntitiesWithinAABB(EntityLiving.class, box);
        for (EntityLiving entity : entities) {
            if (!entity.isDead && manhattanDistance(player, entity) <= radius) {
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

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        SlayContext ctx = SLAYING.get();
        if (ctx != null && event.getEntityLiving() == ctx.target) {
            // The fortune upgrade acts as looting for the slay core.
            event.setLootingLevel(ctx.looting);
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
