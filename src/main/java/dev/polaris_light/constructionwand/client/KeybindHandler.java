package dev.polaris_light.constructionwand.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.basics.ConfigClient;
import dev.polaris_light.constructionwand.basics.ServerRuleCache;
import dev.polaris_light.constructionwand.basics.WandUtil;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.items.wand.ItemWand;
import dev.polaris_light.constructionwand.network.ModMessages;
import dev.polaris_light.constructionwand.network.PacketQueryUndo;
import dev.polaris_light.constructionwand.network.PacketUndoRequest;
import dev.polaris_light.constructionwand.network.PacketWandOption;
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;
import dev.polaris_light.constructionwand.plus.items.CoreNoMaterial;
import dev.polaris_light.constructionwand.plus.items.ItemCoreAE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreStorage;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;

import java.util.Set;

public class KeybindHandler {
    public static final KeyMapping KEY_OPT = new KeyMapping(getKey("wand_option"), GLFW.GLFW_KEY_LEFT_CONTROL, getKey("category"));
    public static final KeyMapping KEY_UNDO = new KeyMapping(getKey("undo"), GLFW.GLFW_KEY_Z, getKey("category"));

    private static String getKey(String name) {
		return String.join(".", "key", ConstructionWand.MODID, name);
	}

	public KeybindHandler() {
        leftShiftPressed = false;
        optPressed = false;
        undoKeyWasDown = false;
	}

    private boolean leftShiftPressed;
    private boolean optPressed;
    private boolean undoKeyWasDown;

    /**
     * The undo key performs the undo while the undo combination (the wand config key, plus sneak when
     * the configuration asks for it) is held. It is read on the client tick rather than from the key
     * event so that it follows the same combination the right click undo uses.
     */
    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        if(player == null || WandUtil.holdingWand(player) == null || !modeKeyCombDown()) {
            undoKeyWasDown = false;
            return;
        }

        boolean down = KEY_UNDO.isDown();
        if(down && !undoKeyWasDown) {
            ModMessages.sendToServer(new PacketUndoRequest());
        }
        undoKeyWasDown = down;
    }

    /**
     * Leaving a server drops the rules it sent, so the local configuration applies again.
     */
    @SubscribeEvent
    public void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ServerRuleCache.clear();
    }

    @SubscribeEvent
    public void KeyEvent(InputEvent.Key event) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;

        if(WandUtil.holdingWand(player) == null) {
            // Without a wand there is nothing the server could act on. The remembered state is dropped,
            // so the next time a wand is held the real state is sent even if no key moved in between:
            // the server would otherwise keep believing the config key is up, and a sneaking right click
            // would be read as the slay core's area kill while the player is only opening the screen.
            optPressed = false;
            leftShiftPressed = false;
            return;
        }

        boolean optState = isOptKeyDown();
        boolean leftShiftState = isLeftShiftKeyDown();
        if(optPressed != optState || leftShiftPressed != leftShiftState) {
            optPressed = optState;
            leftShiftPressed = leftShiftState;
            ModMessages.sendToServer(new PacketQueryUndo(optPressed, leftShiftPressed));
            //ConstructionWand.LOGGER.debug("OPT key update: " + optPressed);
        }
    }

    /**
     * Opens the wand screen when the use key is pressed with the config key held, before the click is
     * turned into anything.
     * <p>
     * This is the only place the click can really be taken. Cancelling the block or item interaction
     * event does not do it: since 1.21 the client always sends its use packet and only skips its own
     * prediction, so the server would still run the item and read the sneaking right click as the slay
     * core's area kill while the player only wanted the screen. Cancelling this input event ends the use
     * before a packet exists at all.
     * <p>
     * The right click undo rides on the same combination, so a click aimed at what the last operation
     * touched is left alone: it has to reach the server. The screen still opens for it through the
     * interaction handlers below, exactly as it always did.
     */
    @SubscribeEvent
    public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if(!event.isUseItem()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if(player == null || !guiKeyCombDown()) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!(wand.getItem() instanceof ItemWand)) return;
        if(TimeConnectedEvents.takesOverUse(wand)) return;
        if(wouldBind(player, wand)) return;
        if(isUndoClick(minecraft)) return;

        minecraft.setScreen(new ScreenWand(wand));
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /**
     * @return whether the click is aimed at a block the last operation touched, which makes it the right
     * click undo. The edited blocks are the ones the server sent for the preview, and the range check
     * mirrors the one the server uses to accept an undo.
     */
    private static boolean isUndoClick(Minecraft minecraft) {
        if(!(minecraft.hitResult instanceof BlockHitResult hit)) return false;

        RenderBlockPreview preview = ClientHandler.renderBlockPreview;
        if(preview == null) return false;

        Set<BlockPos> blocks = preview.undoBlocks;
        if(blocks == null || blocks.isEmpty()) return false;

        BlockPos pos = hit.getBlockPos();
        if(blocks.contains(pos)) return true;
        for(BlockPos candidate : blocks) {
            if(pos.closerThan(candidate, 3)) return true;
        }
        return false;
    }

    // (Sneak)+OPT+Scroll to change direction lock
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void MouseScrollEvent(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        double scroll = event.getScrollDeltaY();
        if (scroll == 0.0D) {
            scroll = event.getScrollDeltaX();
        }

        if(!modeKeyCombDown() || scroll == 0) return;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.lock.next(scroll < 0);
        ModMessages.sendToServer(new PacketWandOption(wandOptions.lock, true));
        event.setCanceled(true);
    }

    // (Sneak)+OPT+Left click wand to change core
    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if(!modeKeyCombDown()) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.cores.next();
        ModMessages.sendToServer(new PacketWandOption(wandOptions.cores, true));
    }

    // (Sneak)+OPT+Right click wand to open GUI. The input event above normally takes the click before it
    // gets here; this stays as the fallback for the paths that do not go through it.
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if(event.getSide().isServer()) return;

        if(!guiKeyCombDown()) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        Minecraft.getInstance().setScreen(new ScreenWand(wand));
        event.setCanceled(true);
    }

    // (Sneak)+OPT+Right click a block also opens the GUI instead of using the block, so the screen is
    // reachable no matter what the player is looking at. While the click would bind or unbind a
    // container, that is left to the binding handler.
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.getSide().isServer()) return;

        if(!guiKeyCombDown()) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;
        if(TimeConnectedEvents.takesOverUse(wand)) return;
        if(wouldBind(event, wand)) return;

        Minecraft.getInstance().setScreen(new ScreenWand(wand));
        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        // The GUI click must not fall through to the item's sneaking right click.
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /**
     * @return whether the click on the block the player is aiming at belongs to a binding instead of the
     * screen. The binding has to win: it is the only way to make one, and the screen is reachable by
     * aiming at any other block.
     */
    private static boolean wouldBind(PlayerInteractEvent.RightClickBlock event, ItemStack wand) {
        return wouldBind(event.getEntity(), wand, event.getLevel(), event.getPos());
    }

    private static boolean wouldBind(Player player, ItemStack wand) {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
        return wouldBind(player, wand, minecraft.level, hit.getBlockPos());
    }

    private static boolean wouldBind(Player player, ItemStack wand, Level level, BlockPos pos) {
        if(player == null || level == null || pos == null || !player.isCrouching()) return false;

        IWandCore materialCore = new WandOptions(wand).materialCores.get();

        if(materialCore instanceof ItemCoreAE) {
            // The AE core binds a controller of an ME network.
            return AeGridAccess.isController(level, pos);
        }

        if(!(materialCore instanceof ItemCoreStorage) && !(materialCore instanceof CoreNoMaterial)) {
            return false;
        }
        return ContainerBinding.isContainer(level, pos);
    }

    public static boolean isLeftShiftKeyDown() {
        return Minecraft.getInstance().options.keyShift.isDown();
    }

    public static boolean isOptKeyDown() {
        return KEY_OPT.isDown();
    }

    public static boolean modeKeyCombDown() {
        return isOptKeyDown() && (isLeftShiftKeyDown() || !ConfigClient.SHIFTOPT_MODE.get());
    }

    public static boolean guiKeyCombDown() {
        // The screen always needs shift as well: ctrl alone belongs to the time core's connected mode.
        return isOptKeyDown() && isLeftShiftKeyDown();
    }
}
