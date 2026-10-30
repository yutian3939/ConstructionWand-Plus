package thetadev.constructionwand.plus.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Trinket slot supply. The 1.12.2 version used Baubles, which does not exist for 1.20.1, so the same
 * feature is provided through Curios.
 * <p>
 * The Curios classes are only touched by the nested provider, which is loaded lazily and only after
 * {@link #isLoaded()} confirmed that the mod is present, so a wand works fine without it.
 */
public final class CuriosCompat
{
    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("curios");
    }

    /**
     * @return how many items equivalent to the template the player carries in trinket slots
     */
    public static int count(Player player, ItemStack template) {
        if(!isLoaded()) return 0;
        try {
            return Provider.count(player, template);
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * @return the amount that could not be taken out of the trinket slots
     */
    public static int take(Player player, ItemStack template, int amount) {
        if(!isLoaded()) return amount;
        try {
            return Provider.take(player, template, amount);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    private static final class Provider
    {
        private Provider() {
        }

        static int count(Player player, ItemStack template) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> {
                        int total = 0;
                        for(var slot : handler.getCurios().values()) {
                            net.minecraftforge.items.IItemHandler stacks = slot.getStacks();
                            for(int index = 0; index < stacks.getSlots(); index++) {
                                ItemStack present = stacks.getStackInSlot(index);
                                if(!present.isEmpty()
                                        && net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(present, template)) {
                                    total += present.getCount();
                                }
                            }
                        }
                        return total;
                    })
                    .orElse(0);
        }

        static int take(Player player, ItemStack template, int amount) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> {
                        int remaining = amount;
                        for(var slot : handler.getCurios().values()) {
                            net.minecraftforge.items.IItemHandler stacks = slot.getStacks();
                            for(int index = 0; index < stacks.getSlots() && remaining > 0; index++) {
                                ItemStack present = stacks.getStackInSlot(index);
                                if(present.isEmpty()
                                        || !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(present, template)) {
                                    continue;
                                }
                                remaining -= stacks.extractItem(index, remaining, false).getCount();
                            }
                        }
                        return remaining;
                    })
                    .orElse(amount);
        }
    }
}
