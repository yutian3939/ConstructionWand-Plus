package thetadev.constructionwand.plus.compat.projecte;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.math.BigInteger;

/**
 * EMC supply for the ProjectE core.
 * <p>
 * A wand with this core does not store items: placing a block spends EMC from the player's
 * transmutation knowledge, and harvesting a block turns it into EMC (unlocking knowledge for items
 * the player does not know yet). All ProjectE classes are touched by the nested provider only.
 * <p>
 * Reading works on both sides: while the render thread asks, ProjectE hands out the client player's
 * copy of the knowledge, which is what the wand screen shows as the EMC of the core. Writing only
 * happens on the server, so a client call never changes anything.
 */
public final class ProjectECompat
{
    private ProjectECompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("projecte");
    }

    /**
     * @return the EMC the player owns, or {@code -1} when ProjectE is not available for them
     */
    public static long emc(Player player) {
        if(!isLoaded()) return -1;
        try {
            return Provider.emc(player);
        } catch(RuntimeException | LinkageError error) {
            return -1;
        }
    }

    /**
     * @return how many items equivalent to the template the player could pay for
     */
    public static int countFor(Player player, ItemStack template) {
        if(!isLoaded()) return 0;
        try {
            return Provider.countFor(player, template);
        } catch(RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /**
     * Pays for a stack with EMC. Only the server can spend EMC, so a client call reports the whole
     * amount as unpaid instead of changing the client's copy of the knowledge.
     *
     * @return the amount that could not be paid for
     */
    public static int take(Player player, ItemStack template, int amount) {
        if(!isLoaded() || amount <= 0) return Math.max(0, amount);
        if(!(player instanceof ServerPlayer serverPlayer)) return amount;
        try {
            return Provider.take(serverPlayer, template, amount);
        } catch(RuntimeException | LinkageError error) {
            return amount;
        }
    }

    /**
     * Turns a harvested stack into EMC, learning items the player does not know yet.
     * <p>
     * An item without an EMC value cannot be converted and stays an item, but it is still learned so
     * the transmutation table stops treating it as unknown.
     *
     * @return the part that has no EMC value and therefore stays an item
     */
    public static ItemStack convertToEmc(Player player, ItemStack stack) {
        if(!isLoaded() || stack.isEmpty()) return stack;
        if(!(player instanceof ServerPlayer serverPlayer)) return stack;
        try {
            return Provider.convertToEmc(serverPlayer, stack);
        } catch(RuntimeException | LinkageError error) {
            return stack;
        }
    }

    /**
     * Everything that touches the ProjectE API. Loaded lazily, so the wand works without ProjectE.
     */
    private static final class Provider
    {
        private Provider() {
        }

        /**
         * The knowledge of the player who is asking.
         * <p>
         * ProjectE picks the side from the current thread, not from this argument, and both sides are
         * safe in multiplayer: on the server thread the UUID is looked up among the online players, so
         * the provider belongs to exactly the player that was passed in and never to somebody else; on
         * the render thread the id is ignored and the local player's copy is handed out, which is where
         * the wand screen reads the EMC from. A client caller therefore has to pass the local player,
         * and a client write is impossible because the mutating calls only run for a {@link ServerPlayer}.
         */
        private static moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge(Player player) {
            if(player == null) return null;
            return moze_intel.projecte.api.proxy.ITransmutationProxy.INSTANCE
                    .getKnowledgeProviderFor(player.getUUID());
        }

        private static long toLong(BigInteger value) {
            if(value == null || value.signum() <= 0) return 0;
            return value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }

        static long emc(Player player) {
            moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge = knowledge(player);
            return knowledge == null ? -1 : toLong(knowledge.getEmc());
        }

        /**
         * The EMC value of a single item, never of the whole stack.
         */
        private static long unitValue(ItemStack stack) {
            if(stack.isEmpty()) return 0;
            moze_intel.projecte.api.proxy.IEMCProxy proxy = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE;
            if(!proxy.hasValue(stack)) return 0;
            return Math.max(0, proxy.getValue(stack));
        }

        static int countFor(Player player, ItemStack template) {
            moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge = knowledge(player);
            if(knowledge == null || template.isEmpty()) return 0;
            if(!knowledge.hasKnowledge(template)) return 0;

            long value = unitValue(template);
            if(value <= 0) return 0;
            long total = toLong(knowledge.getEmc()) / value;
            return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, total);
        }

        static int take(ServerPlayer player, ItemStack template, int amount) {
            moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge = knowledge(player);
            if(knowledge == null) return amount;

            long value = unitValue(template);
            if(value <= 0) return amount;

            long affordable = toLong(knowledge.getEmc()) / value;
            int taken = (int) Math.min(amount, affordable);
            if(taken <= 0) return amount;

            // The EMC is a BigInteger, so the price is computed in that domain.
            BigInteger price = BigInteger.valueOf(value).multiply(BigInteger.valueOf(taken));
            knowledge.setEmc(knowledge.getEmc().subtract(price).max(BigInteger.ZERO));
            knowledge.syncEmc(player);
            return amount - taken;
        }

        static ItemStack convertToEmc(ServerPlayer player, ItemStack stack) {
            moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge = knowledge(player);
            if(knowledge == null) return stack;

            learn(player, knowledge, stack);

            long value = unitValue(stack);
            if(value <= 0) return stack;

            BigInteger gained = BigInteger.valueOf(value).multiply(BigInteger.valueOf(stack.getCount()));
            knowledge.setEmc(knowledge.getEmc().add(gained).max(BigInteger.ZERO));
            knowledge.syncEmc(player);
            return ItemStack.EMPTY;
        }

        /**
         * Adds an item the player does not know to the knowledge and tells the client about it.
         * <p>
         * The knowledge change travels in its own packet: sending only the EMC would leave the client
         * showing the item as unknown until the next full sync, which is what made a freshly learned
         * drop look like it was never added to the transmutation table.
         */
        private static void learn(ServerPlayer player, moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge, ItemStack stack) {
            if(stack.isEmpty() || knowledge.hasKnowledge(stack)) return;

            ItemStack sample = stack.copy();
            sample.setCount(1);
            if(!knowledge.addKnowledge(sample)) return;

            try {
                moze_intel.projecte.api.ItemInfo info = moze_intel.projecte.api.ItemInfo.fromStack(sample);
                knowledge.syncKnowledgeChange(player,
                        moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getPersistentInfo(info), true);
            } catch(RuntimeException ignored) {
                // The knowledge is written, the next full sync carries it to the client.
            }
        }
    }
}
