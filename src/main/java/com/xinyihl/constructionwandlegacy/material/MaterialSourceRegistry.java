package com.xinyihl.constructionwandlegacy.material;

import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import com.xinyihl.constructionwandlegacy.material.source.PlayerInventorySourceFactory;
import com.xinyihl.constructionwandlegacy.material.source.PortableContainerSourceFactory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MaterialSourceRegistry {
    private final List<MaterialSourceFactory> inventoryFactories = new ArrayList<>();

    public MaterialSourceRegistry() {
        inventoryFactories.add(new BoundContainerSourceFactory());
        inventoryFactories.add(new PlayerInventorySourceFactory());
        inventoryFactories.add(new PortableContainerSourceFactory());
    }

    private static List<MaterialSource> createSources(EntityPlayer player, ItemStack wand, List<MaterialSourceFactory> factories) {
        List<MaterialSource> sources = new ArrayList<>();
        for (MaterialSourceFactory factory : factories) {
            MaterialSource source = factory.create(player, wand);
            if (source != null) {
                sources.add(source);
            }
        }
        return sources;
    }

    public void registerInventorySource(MaterialSourceFactory factory) {
        inventoryFactories.add(factory);
    }

    public MaterialSession createInventorySession(EntityPlayer player, ItemStack wand) {
        List<MaterialSource> sources = createSources(player, wand, inventoryFactories);
        return player.isCreative() ? MaterialSession.creativeUnlimited(sources) : new MaterialSession(sources);
    }

    public MaterialSession createSession(EntityPlayer player, ItemStack wand, MaterialSourceFactory factory) {
        List<MaterialSourceFactory> factories = new ArrayList<>();
        factories.add(factory);
        List<MaterialSource> sources = createSources(player, wand, factories);
        return player.isCreative() ? MaterialSession.creativeCatalog(sources) : new MaterialSession(sources);
    }
}
