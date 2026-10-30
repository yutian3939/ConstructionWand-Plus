package dev.polaris_light.constructionwand.plus.compat.apotheosis;

import java.util.List;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.payloads.CluePayload;
import dev.shadowsoffire.apothic_enchanting.payloads.StatsPayload;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.basics.ApotheosisLevels;

import javax.annotation.Nullable;

/**
 * The only place of this mod that names Apotheosis or Apothic Enchanting types.
 * <p>
 * Like the ProjectE access class, it may only be touched while both mods are installed; the core itself only
 * exists then, which is what keeps this safe. Everything here is a thin call into their public classes: the
 * stats of a table are the record {@code EnchantmentTableStats}, and the world tier is the enum of
 * {@code WorldTier} with its own setter.
 */
public final class ApotheosisBridge
{
    /** Mod id of the world tier and gem dust mod. */
    public static final String MODID = "apotheosis";
    /** Mod id of the enchanting table and stat mod. */
    public static final String ENCHANTING_MODID = "apothic_enchanting";

    private ApotheosisBridge() {
    }

    /**
     * @return whether both mods are installed, so this class may be used at all
     */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID) && ModList.get().isLoaded(ENCHANTING_MODID);
    }

    /**
     * @return whether the block is an enchanting table of any kind. Apotheosis replaces the vanilla table with
     * its own class at runtime, and its extra table is a subclass of the vanilla one as well, so this covers
     * the vanilla table, the apothic one and the raven one in a single test.
     */
    public static boolean isTable(BlockState state) {
        return state.getBlock() instanceof EnchantingTableBlock;
    }

    /**
     * @return the number of world tiers Apotheosis offers
     */
    public static int tierCount() {
        return WorldTier.values().length;
    }

    /**
     * @return the name the player reads for a tier, or an empty string when the index is out of range
     */
    public static String tierName(int index) {
        WorldTier[] tiers = WorldTier.values();
        if(index < 0 || index >= tiers.length) return "";
        return tiers[index].getSerializedName();
    }

    /**
     * Writes the world tier of the player. Apotheosis only changes a tier from its own screen when the tier is
     * unlocked; the core is meant to hand the player the choice, so this setter is used directly, which also
     * applies and removes the tier augments of both tiers.
     */
    public static void applyTier(ServerPlayer player, int index) {
        WorldTier[] tiers = WorldTier.values();
        WorldTier tier = tiers[Math.min(tiers.length - 1, Math.max(0, index))];
        WorldTier.setTier(player, tier);

        // The setter bails out when the player already has that tier, and something else in Apotheosis may put
        // the old one back. Writing the attachment as well makes the value the one the wand asked for either way.
        if(WorldTier.getTier(player) != tier) {
            player.setData(Apoth.Attachments.WORLD_TIER, tier);
        }

        // Having a tier and having unlocked it are two different things in Apotheosis: the unlock is an
        // advancement, and without it the tier is marked as locked and never counts as one the player reached.
        // The core is the way to reach a tier without the journey, so the advancements up to it are awarded.
        for(int index2 = 0; index2 <= tiers.length - 1 && index2 <= Math.max(0, index); index2++) {
            awardTier(player, tiers[index2]);
        }
    }

    private static void awardTier(ServerPlayer player, WorldTier tier) {
        var advancement = player.server.getAdvancements().get(tier.getUnlockAdvancement());
        if(advancement == null) return;

        // Neither the criteria nor their number are known here, and granting an unknown one throws, so every
        // criterion the advancement has is granted.
        for(String criterion : advancement.value().criteria().keySet()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /**
     * Raises the highest rank the player may use, so a pinned rank is not cut down by the attribute a world
     * tier gives. Apotheosis shows that cut as "enchantment power limited" on the table screen.
     */
    public static void raiseRankLimit(ServerPlayer player, int rank) {
        var attribute = player.getAttribute(Ench.Attributes.MAX_ETERNA);
        if(attribute == null) return;

        // The screen of the table warns whenever the value of the attribute is below a hundred, and the tier
        // augments of Apotheosis subtract from it, so raising the base alone is not enough: a modifier is put
        // on the attribute that brings the value up to the rank the wand wants.
        ResourceLocation id = ConstructionWand.loc("apoth_rank");
        double before = attribute.getValue();
        attribute.removeModifier(id);
        if(before < rank) {
            attribute.addTransientModifier(new AttributeModifier(id, rank - before,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        // The screen reads the value on the client, which only learns about it when it is told.
        player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), List.of(attribute)));

        ConstructionWand.LOGGER.info("[apoth] rank limit value {} -> {} wanted {}", before,
                attribute.getValue(), rank);
    }

    /**
     * @return the world tier the player has right now, as the name the wand shows
     */
    public static String currentTierName(ServerPlayer player) {
        return WorldTier.getTier(player).getSerializedName();
    }

    /**
     * Writes the three stats onto the enchanting table the player has open, using the values the wand is set
     * to. The values of the table's own shelves are kept as they are: only rank, quanta and arcana are
     * replaced, so the clues, the blacklist, the treasure flag and the stability of the table still come from
     * the room the table stands in.
     *
     * @param pos the table the player right clicked, which is where its shelves are read from
     * @return whether the open menu was a table of Apothic Enchanting
     */
    public static boolean applyStats(ServerPlayer player, Level level, BlockPos pos) {
        if(!(player.containerMenu instanceof ApothEnchantmentMenu menu)) return false;

        menu.setStats(statsFor(player, level, pos));
        return true;
    }

    /**
     * @return the stats the wand would write onto a table at that position: the rank, quanta and arcana of the
     *         wand, and the clues, blacklist, treasure flag and stability the shelves of the table give
     */
    public static EnchantmentTableStats statsFor(ServerPlayer player, Level level, BlockPos pos) {
        return statsFor(level, pos, ApotheosisLevels.eterna(player.getMainHandItem()),
                ApotheosisLevels.quanta(player.getMainHandItem()),
                ApotheosisLevels.arcana(player.getMainHandItem()));
    }

    /**
     * @return the stats a table at that position would run with, given the three numbers to use
     */
    public static EnchantmentTableStats statsFor(Level level, BlockPos pos, int rank, int quanta, int arcana) {
        EnchantmentTableStats base = EnchantmentTableStats.gatherStats(level, pos);
        return new EnchantmentTableStats(rank, quanta, arcana,
                base.clues(), base.blacklist(), base.treasure(), base.stable());
    }

    /**
     * Writes ready made stats onto the open table.
     *
     * @return whether the player had a table of Apothic Enchanting open
     */
    public static boolean applyStats(ServerPlayer player, EnchantmentTableStats stats) {
        if(!(player.containerMenu instanceof ApothEnchantmentMenu menu)) return false;
        menu.setStats(stats);
        return true;
    }

    /**
     * Sends the numbers to the player, so the cost the screen shows matches what the server will roll with.
     * The table itself does this when it recomputes its stats; writing them later has to say so as well.
     */
    public static void sendStats(ServerPlayer player, EnchantmentTableStats stats) {
        PacketDistributor.sendToPlayer(player, new StatsPayload(stats));
    }

    /**
     * Repeats the pass the table runs whenever the item in its slot changes, with the numbers given instead of
     * the ones of its shelves: the cost of the three rows and their clues are computed again, so the screen and
     * the price follow the pinned numbers rather than the room.
     */
    public static void refreshRows(ServerPlayer player, EnchantmentTableStats stats) {
        if(!(player.containerMenu instanceof ApothEnchantmentMenu menu)) return;

        ItemStack item = menu.getSlot(0).getItem();
        if(item.isEmpty()) return;

        float eterna = stats.eterna(player);
        if(eterna < 1.5F) eterna = 1.5F;

        menu.setStats(stats);
        // The seed of a table is the one of the player, which the game advances with a fresh random number
        // after every enchantment. Reading it here keeps the offers as random as a table that was never
        // pinned; counting it up by hand would have made the next set of offers predictable.
        menu.enchantmentSeed.set(player.getEnchantmentSeed());
        menu.random.setSeed(menu.enchantmentSeed.get());
        Registry<Enchantment> registry = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for(int slot = 0; slot < 3; slot++) {
            menu.costs[slot] = ApothEnchantmentHelper.getEnchantmentCost(menu.random, slot, eterna, item);
            if(menu.costs[slot] < slot + 1) menu.costs[slot]++;

            // The clue of a row is the enchantment the table would offer for it, and it is computed from the
            // cost of that row. Leaving the clues of the table's own pass would show offers of the shelves, so
            // the pass is repeated here with the pinned numbers, seeded exactly as the table seeds it.
            menu.enchantClue[slot] = -1;
            menu.levelClue[slot] = -1;
            if(menu.costs[slot] > 0) {
                menu.random.setSeed(menu.enchantmentSeed.get() + slot);
                List<EnchantmentInstance> list = ApothEnchantmentHelper.selectEnchantment(menu.random, item,
                        menu.costs[slot], stats, registry.asLookup());
                if(list != null && !list.isEmpty()) {
                    EnchantmentInstance data = list.remove(menu.random.nextInt(list.size()));
                    menu.enchantClue[slot] = registry.getId(data.enchantment.value());
                    menu.levelClue[slot] = data.level;

                    // The hint a row shows is a packet of its own, and the table sent it before the numbers were
                    // written here: the row is told again, with what the pinned numbers offer.
                    PacketDistributor.sendToPlayer(player, new CluePayload(slot, List.of(data), list.isEmpty()));
                }
            }
        }
        menu.broadcastChanges();
    }

    /**
     * @return the table position behind the open menu, or {@code null} when the player has no table open. The    /**
     * @return the table position behind the open menu, or {@code null} when the player has no table open. The
     * client side menu carries no position, which is what the {@code null} of its level access tells apart.
     */
    @Nullable
    public static BlockPos menuPos(ServerPlayer player) {
        if(!(player.containerMenu instanceof EnchantmentMenu menu)) return null;
        return menu.access.evaluate((level, pos) -> pos, null);
    }
    /**
     * @return whether the player currently has an enchanting table of Apothic Enchanting open
     */
    public static boolean hasTableOpen(ServerPlayer player) {
        return player.containerMenu instanceof ApothEnchantmentMenu;
    }

    /**
     * @return the level of the player, which is where the shelves of the table are read from
     */
    @Nullable
    public static ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
    }
}