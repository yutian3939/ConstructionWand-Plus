package dev.polaris_light.constructionwand.plus.basics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * The drops a mod hands out from its own death handler rather than from a loot table.
 * <p>
 * The guaranteed drops of the slay core come from walking the loot table of the victim, which cannot see an
 * item another mod adds in {@code LivingDropsEvent}. Those are listed here by hand, mod by mod, and are handed
 * out along with the table when the wand is set to drop everything.
 * <p>
 * Nothing is referenced by class, only by item id, so a game without the mod simply finds no items and the list
 * stays empty.
 */
public final class SpecialDeathDrops
{
    private SpecialDeathDrops() {
    }

    /**
     * @return the extra items a victim of that kind drops through the handlers of other mods, which a walk of
     * its loot table cannot find
     */
    public static List<ItemStack> forVictim(LivingEntity victim) {
        List<ItemStack> found = new ArrayList<>();
        if(ModList.get() == null) return found;

        // Reliquary: the eye of the storm is added to a powered creeper by a loot injection, which the walk of
        // the plain loot table does not see.
        if(isLoaded("reliquary") && victim instanceof Creeper creeper && creeper.isPowered()) {
            add(found, "reliquary:eye_of_the_storm", 1);
        }

        // Mystical Agradditions: the withering soul drops from a wither at a chance written into its handler,
        // and the dragon scale is added the same way when a dragon dies.
        if(isLoaded("mysticalagradditions")) {
            if(victim instanceof WitherBoss) add(found, "mysticalagradditions:withering_soul", 1);
            if(victim instanceof EnderDragon) add(found, "mysticalagradditions:dragon_scale", 8);
        }

        return found;
    }

    private static void add(List<ItemStack> found, String id, int count) {
        Item item = item(id);
        if(item != null) found.add(new ItemStack(item, count));
    }

    @Nullable
    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if(key == null) return null;
        return BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.get(key) : null;
    }

    private static boolean isLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }
}