package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable identity for one client-side preview calculation.
 */
public final class PreviewKey {
    private static final Item TEST_ITEM = new Item();
    private final Mode mode;
    private final int dimension;
    private final long worldTick;
    private final long playerX;
    private final long playerY;
    private final long playerZ;
    private final long lookX;
    private final long lookY;
    private final long lookZ;
    private final long targetPos;
    private final int targetSide;
    private final EnumHand hand;
    private final int slot;
    private final Item wandItem;
    private final int wandDamage;
    @Nullable
    private final NBTTagCompound wandTag;
    private final String stateSignature;
    private final long rulesRevision;

    private PreviewKey(Mode mode, int dimension, long worldTick, double playerX, double playerY, double playerZ, double lookX, double lookY, double lookZ, BlockPos targetPos, @Nullable EnumFacing targetSide, EnumHand hand, int slot, Item wandItem, int wandDamage, @Nullable NBTTagCompound wandTag, String stateSignature, long rulesRevision) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.dimension = dimension;
        this.worldTick = worldTick;
        this.playerX = Double.doubleToLongBits(playerX);
        this.playerY = Double.doubleToLongBits(playerY);
        this.playerZ = Double.doubleToLongBits(playerZ);
        this.lookX = Double.doubleToLongBits(lookX);
        this.lookY = Double.doubleToLongBits(lookY);
        this.lookZ = Double.doubleToLongBits(lookZ);
        this.targetPos = Objects.requireNonNull(targetPos, "targetPos").toLong();
        this.targetSide = targetSide == null ? -1 : targetSide.getIndex();
        this.hand = Objects.requireNonNull(hand, "hand");
        this.slot = slot;
        this.wandItem = Objects.requireNonNull(wandItem, "wandItem");
        this.wandDamage = wandDamage;
        this.wandTag = wandTag == null ? null : wandTag.copy();
        this.stateSignature = Objects.requireNonNull(stateSignature, "stateSignature");
        this.rulesRevision = rulesRevision;
    }

    public static PreviewKey create(Mode mode, int dimension, long worldTick, double playerX, double playerY, double playerZ, Vec3d look, BlockPos targetPos, @Nullable EnumFacing targetSide, WandTarget target, ItemStack wand, WandState state, long rulesRevision) {
        return new PreviewKey(mode, dimension, worldTick, playerX, playerY, playerZ, look.x, look.y, look.z, targetPos, targetSide, target.getHand(), target.getSlot(), wand.getItem(), wand.getItemDamage(), wand.getTagCompound(), stateSignature(state), rulesRevision);
    }

    static PreviewKey forTest(Mode mode, long worldTick, double playerX, double lookX, long targetPos, String stateSignature, long rulesRevision) {
        return new PreviewKey(mode, 0, worldTick, playerX, 0.0D, 0.0D, lookX, 0.0D, 0.0D, BlockPos.fromLong(targetPos), EnumFacing.UP, EnumHand.MAIN_HAND, 0, TEST_ITEM, 0, null, stateSignature, rulesRevision);
    }

    private static String stateSignature(WandState state) {
        ResourceLocation core = state.getSelectedCore().getRegistryName();
        ResourceLocation material = state.getSelectedMaterialCore() == null ? null : state.getSelectedMaterialCore().getRegistryName();
        return String.valueOf(core) + '|' + state.getSelectedCoreIndex() + '|' + material + '|' + state.getSelectedMaterialIndex() + '|' + state.getLock().name() + '|' + state.getDirection().name() + '|' + state.isReplace() + '|' + state.getMatch().name() + '|' + state.isRandom();
    }

    public Mode getMode() {
        return mode;
    }

    public boolean matchesBlock(BlockPos pos, EnumFacing side) {
        return (mode == Mode.BLOCK || mode == Mode.UNDO) && targetPos == pos.toLong() && targetSide == (side == null ? -1 : side.getIndex());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreviewKey)) {
            return false;
        }
        PreviewKey that = (PreviewKey) other;
        return dimension == that.dimension && worldTick == that.worldTick && playerX == that.playerX && playerY == that.playerY && playerZ == that.playerZ && lookX == that.lookX && lookY == that.lookY && lookZ == that.lookZ && targetPos == that.targetPos && targetSide == that.targetSide && slot == that.slot && wandItem == that.wandItem && wandDamage == that.wandDamage && rulesRevision == that.rulesRevision && mode == that.mode && hand == that.hand && Objects.equals(wandTag, that.wandTag) && stateSignature.equals(that.stateSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, dimension, worldTick, playerX, playerY, playerZ, lookX, lookY, lookZ, targetPos, targetSide, hand, slot, System.identityHashCode(wandItem), wandDamage, wandTag, stateSignature, rulesRevision);
    }

    public enum Mode {
        BLOCK, AIR, UNDO
    }
}
