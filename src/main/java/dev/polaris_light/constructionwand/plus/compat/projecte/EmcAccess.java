package dev.polaris_light.constructionwand.plus.compat.projecte;

import java.math.BigInteger;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Reads the EMC of a player, which the ProjectE core spends and fills.
 * <p>
 * This class names ProjectE types, so it may only be touched while ProjectE is installed. The ProjectE core
 * itself only exists then, which is what keeps this safe.
 */
public final class EmcAccess
{
    private EmcAccess() {
    }

    /**
     * @return the EMC the player has, written the way a player reads numbers, or {@code null} when the
     * player has no ProjectE knowledge at all
     */
    @Nullable
    public static String read(Player player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if(knowledge == null) return null;

        BigInteger emc = knowledge.getEmc();
        return String.format("%,d", emc);
    }

    /**
     * @return whether the player has any EMC to spend
     */
    public static boolean hasEmc(Player player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        return knowledge != null && knowledge.getEmc().signum() > 0;
    }
}