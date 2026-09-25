package com.xinyihl.constructionwandlegacy.material;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

public interface MaterialSourceFactory {
    @Nullable
    MaterialSource create(EntityPlayer player, ItemStack wand);
}
