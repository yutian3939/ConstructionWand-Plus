package com.xinyihl.morewandcore;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.morewandcore.basics.WandUpgrades;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The defense upgrade's invulnerability.
 * <p>
 * While a player carries a wand with the defense upgrade installed (anywhere in the main inventory
 * or the off hand, not just in the held hand) they take no damage, cannot die and are put back to
 * full health every tick. The attack, hurt and death events are cancelled at the highest priority so
 * the protection is in place before anything else looks at them, and the health is restored every
 * tick so damage that bypasses the events cannot stick either.
 * <p>
 * Like the other upgrades, an installed defense component can be switched off from the wand screen,
 * which also switches the protection off.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class DefenseEvents {
    private DefenseEvents() {
    }

    /**
     * @return whether that entity is a player protected by a carried wand with the defense upgrade
     */
    public static boolean isProtected(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer) || entity.world.isRemote) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (carriesDefenseUpgrade(stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (carriesDefenseUpgrade(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean carriesDefenseUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand && WandUpgrades.hasDefense(stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (isProtected(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (isProtected(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (isProtected(entity)) {
            entity.setHealth(entity.getMaxHealth());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (!isProtected(entity)) {
            return;
        }
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        if (entity.isBurning()) {
            entity.extinguish();
        }
        clearHarmfulEffects(entity);
    }

    private static void clearHarmfulEffects(EntityLivingBase entity) {
        List<PotionEffect> harmful = null;
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            if (effect.getPotion().isBadEffect()) {
                if (harmful == null) {
                    harmful = new ArrayList<>();
                }
                harmful.add(effect);
            }
        }
        if (harmful == null) {
            return;
        }
        for (PotionEffect effect : harmful) {
            entity.removePotionEffect(effect.getPotion());
        }
    }
}
