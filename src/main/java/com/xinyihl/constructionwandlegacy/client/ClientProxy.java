package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.ModRuntime;
import com.xinyihl.constructionwandlegacy.config.RuleSnapshot;
import com.xinyihl.constructionwandlegacy.proxy.CommonProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.Set;

public final class ClientProxy extends CommonProxy {
    private final ClientState state = new ClientState();
    private ClientPreviewController previewController;

    @Override
    public void preInit(ModRuntime runtime) {
        previewController = new ClientPreviewController(state, runtime.getWandPlanner());
        MinecraftForge.EVENT_BUS.register(new ClientEvents(previewController));
        MinecraftForge.EVENT_BUS.register(new RenderBlockPreview(state, previewController));
        ClientRegistry.registerKeyBinding(ModKeys.UNDO);
    }

    @Override
    public void handleUndoBlocks(Set<BlockPos> blocks) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            state.replaceUndoBlocks(blocks);
            previewController.resetPreview();
        });
    }

    @Override
    public void handleServerRules(RuleSnapshot rules) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (state.applyServerRules(rules)) {
                previewController.resetPreview();
            }
        });
    }
}
