package com.xinyihl.constructionwandlegacy.compat.projecte;

import com.xinyihl.constructionwandlegacy.material.*;
import com.xinyihl.constructionwandlegacy.material.source.InventoryRefunds;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ProjectEMaterialSourceFactory implements MaterialSourceFactory {
    /**
     * Players whose knowledge still has to be sent to their client, see {@link #queueSync}.
     */
    private static final Set<EntityPlayerMP> PENDING_SYNC = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    @Optional.Method(modid = "projecte")
    public MaterialSource create(EntityPlayer player, ItemStack wand) {
        IKnowledgeProvider knowledge = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getPersistentID());
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        return knowledge == null || emc == null ? null : new ProjectEMaterialSource(player, knowledge, emc);
    }

    /**
     * The EMC the player currently has. The ProjectE core draws from the player instead of from a
     * bound block, so this is what the wand screen reports as its binding.
     *
     * @return the amount, or {@code -1} when it is not available
     */
    @Optional.Method(modid = "projecte")
    public static long playerEmc(EntityPlayer player) {
        if (player == null) {
            return -1L;
        }
        try {
            IKnowledgeProvider knowledge = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getPersistentID());
            return knowledge == null ? -1L : Math.max(0L, knowledge.getEmc());
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    /**
     * Converts a harvested stack into EMC, learning items the player did not know yet.
     * <p>
     * EMC has no notion of storing items, so everything with an EMC value is turned into EMC instead
     * of being kept. The wand's drop destination decides whether that happens first or only with
     * whatever the player inventory cannot hold. Items without an EMC value stay items.
     *
     * @return the part of the stack that could not be converted and therefore stays with the player
     */
    @Optional.Method(modid = "projecte")
    public static ItemStack convertToEmc(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        IKnowledgeProvider knowledge = knowledgeOf(player);
        if (emc == null || knowledge == null || !emc.hasValue(stack)) {
            return stack;
        }
        try {
            long unitValue = emc.getValue(stack);
            if (unitValue <= 0L) {
                return stack;
            }
            // A drop the player never held is learned on the way in; otherwise the EMC would be
            // there while the item itself is still missing from the transmutation table.
            if (!knowledge.hasKnowledge(stack)) {
                ItemStack definition = stack.copy();
                definition.setCount(1);
                knowledge.addKnowledge(definition);
            }
            long gain = unitValue * stack.getCount();
            knowledge.setEmc(SaturatedAmounts.add(Math.max(0L, knowledge.getEmc()), gain));
            queueSync(player);
            return ItemStack.EMPTY;
        } catch (RuntimeException exception) {
            return stack;
        }
    }

    /**
     * How many items equivalent to {@code template} the player's EMC could pay for.
     */
    @Optional.Method(modid = "projecte")
    public static int countEmcEquivalent(EntityPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) {
            return 0;
        }
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        IKnowledgeProvider knowledge = knowledgeOf(player);
        if (emc == null || knowledge == null || !emc.hasValue(template)) {
            return 0;
        }
        try {
            long unitValue = emc.getValue(template);
            return unitValue <= 0L ? 0 : SaturatedAmounts.fromLong(Math.max(0L, knowledge.getEmc()) / unitValue);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /**
     * Pays for {@code amount} items with EMC, which is how an undo takes back what the digging core
     * handed over: those drops only exist as EMC afterwards.
     *
     * @return the amount the EMC did not cover
     */
    @Optional.Method(modid = "projecte")
    public static int takeEmcEquivalent(EntityPlayer player, ItemStack template, int amount) {
        if (player == null || template == null || template.isEmpty() || amount <= 0) {
            return Math.max(0, amount);
        }
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        IKnowledgeProvider knowledge = knowledgeOf(player);
        if (emc == null || knowledge == null || !emc.hasValue(template)) {
            return amount;
        }
        try {
            long unitValue = emc.getValue(template);
            if (unitValue <= 0L) {
                return amount;
            }
            long current = Math.max(0L, knowledge.getEmc());
            int paid = Math.min(amount, SaturatedAmounts.fromLong(current / unitValue));
            if (paid <= 0) {
                return amount;
            }
            knowledge.setEmc(Math.max(0L, current - unitValue * paid));
            queueSync(player);
            return amount - paid;
        } catch (RuntimeException exception) {
            return amount;
        }
    }

    @Nullable
    private static IKnowledgeProvider knowledgeOf(EntityPlayer player) {
        try {
            return ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getPersistentID());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Sends the knowledge to the client on the next server tick.
     * <p>
     * A sync transmits the whole knowledge list and the digging core hands over one stack per block,
     * so syncing every deposit would flood the connection. Everything written within the same tick is
     * sent once instead.
     */
    private static void queueSync(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        MinecraftServer server = serverPlayer.getServer();
        if (server == null || !PENDING_SYNC.add(serverPlayer)) {
            return;
        }
        server.addScheduledTask(() -> {
            PENDING_SYNC.remove(serverPlayer);
            if (serverPlayer.connection == null) {
                return;
            }
            try {
                IKnowledgeProvider knowledge = knowledgeOf(serverPlayer);
                if (knowledge != null) {
                    knowledge.sync(serverPlayer);
                }
            } catch (RuntimeException ignored) {
                // The EMC value is already written, the next change sends another sync.
            }
        });
    }

    private static final class ProjectEMaterialSource implements MaterialSource {
        private static final String ID = "projecte";

        private final EntityPlayer player;
        private final IKnowledgeProvider knowledge;
        private final IEMCProxy emc;
        private final SharedMaterialBudget budget;
        private final Map<MaterialKey, Long> unitCosts = new HashMap<>();

        private ProjectEMaterialSource(EntityPlayer player, IKnowledgeProvider knowledge, IEMCProxy emc) {
            this.player = player;
            this.knowledge = knowledge;
            this.emc = emc;
            this.budget = new SharedMaterialBudget(Math.max(0L, knowledge.getEmc()));
        }

        @Override
        public String getId() {
            return ID;
        }

        @Override
        @Optional.Method(modid = "projecte")
        public void enumerate(MaterialCollector collector) {
            if (!isKnowledgeAvailable()) {
                return;
            }
            Set<MaterialKey> seen = new HashSet<>();
            for (ItemStack stack : knowledge.getKnowledge()) {
                if (stack.isEmpty() || !knowledge.hasKnowledge(stack) || !emc.hasValue(stack)) {
                    continue;
                }
                long unitValue = emc.getValue(stack);
                if (unitValue <= 0L) {
                    continue;
                }
                MaterialKey key = MaterialKey.of(stack);
                if (seen.add(key)) {
                    unitCosts.put(key, unitValue);
                    budget.register(key, unitValue);
                    collector.accept(key, Math.max(0L, knowledge.getEmc()) / unitValue);
                }
            }
        }

        @Override
        public int availableCapacity(MaterialKey key, int cachedAvailable) {
            return budget.available(key, cachedAvailable);
        }

        @Override
        public int reserveCapacity(MaterialKey key, int requested, int cachedAvailable) {
            return budget.reserve(key, requested, cachedAvailable);
        }

        @Override
        public void finishReservation(MaterialKey key, int count, boolean committed) {
            budget.finish(key, count, committed);
        }

        @Override
        @Optional.Method(modid = "projecte")
        public MaterialReceipt extract(MaterialKey key, int count) {
            Long unitValue = unitCosts.get(key);
            if (count <= 0 || unitValue == null || unitValue <= 0L || !isServerKnowledgeUsable()) {
                return MaterialReceipt.empty();
            }
            ItemStack definition = key.createStack(1);
            try {
                if (!knowledge.hasKnowledge(definition) || !emc.hasValue(definition)) {
                    return MaterialReceipt.empty();
                }
                long currentEmc = Math.max(0L, knowledge.getEmc());
                int extracted = Math.min(count, SaturatedAmounts.fromLong(currentEmc / unitValue));
                if (extracted <= 0) {
                    return MaterialReceipt.empty();
                }
                long cost = unitValue * extracted;
                knowledge.setEmc(currentEmc - cost);
                sync();
                return MaterialReceipt.of(ID, key, extracted, (refundKey, refundCount) -> refund(refundKey, refundCount, unitValue));
            } catch (RuntimeException exception) {
                return MaterialReceipt.empty();
            }
        }

        private int refund(MaterialKey key, int count, long unitValue) {
            if (!isServerKnowledgeUsable()) {
                return InventoryRefunds.refund(player, key, count);
            }
            try {
                long cost = unitValue * count;
                knowledge.setEmc(SaturatedAmounts.add(Math.max(0L, knowledge.getEmc()), cost));
                sync();
                return 0;
            } catch (RuntimeException exception) {
                return InventoryRefunds.refund(player, key, count);
            }
        }

        private boolean isKnowledgeAvailable() {
            if (player == null || player.isDead || player.world == null) {
                return false;
            }
            try {
                return ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getPersistentID()) == knowledge;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private boolean isServerKnowledgeUsable() {
            return InventoryRefunds.isUsable(player) && isKnowledgeAvailable();
        }

        private void sync() {
            if (player instanceof EntityPlayerMP) {
                try {
                    knowledge.sync((EntityPlayerMP) player);
                } catch (RuntimeException ignored) {
                    // EMC was already changed; a later sync can repair the client view.
                }
            }
        }
    }
}
