package thetadev.constructionwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigClient;
import thetadev.constructionwand.basics.ServerRuleCache;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.network.PacketQueryUndo;
import thetadev.constructionwand.network.PacketUndoRequest;
import thetadev.constructionwand.network.PacketWandOption;

public class ClientEvents
{
    private boolean optPressed;
    private boolean undoKeyWasDown;

    public ClientEvents() {
        optPressed = false;
        undoKeyWasDown = false;
    }

    // Send state of OPT key to server
    @SubscribeEvent
    public void KeyEvent(InputEvent.Key event) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;
        if(WandUtil.holdingWand(player) == null) return;

        boolean optState = isOptKeyDown();
        if(optPressed != optState) {
            optPressed = optState;
            PacketQueryUndo packet = new PacketQueryUndo(optPressed);
            ConstructionWand.instance.HANDLER.sendToServer(packet);
            //ConstructionWand.LOGGER.debug("OPT key update: " + optPressed);
        }
    }

    // The undo key performs the undo while the undo combination (sneak and the opt key) is held.
    @SubscribeEvent
    public void ClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;

        Player player = Minecraft.getInstance().player;
        if(player == null || WandUtil.holdingWand(player) == null || !modeKeyCombDown(player)) {
            undoKeyWasDown = false;
            return;
        }

        boolean down = ModKeys.UNDO.isDown();
        if(down && !undoKeyWasDown) {
            ConstructionWand.instance.HANDLER.sendToServer(new PacketUndoRequest());
        }
        undoKeyWasDown = down;
    }

    // Leaving a server drops the rules that were sent, so the local configuration applies again.
    @SubscribeEvent
    public void LoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ServerRuleCache.clear();
    }

    // Sneak+(OPT)+Scroll to change direction lock
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void MouseScrollEvent(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        double scroll = event.getScrollDelta();

        if(player == null || !modeKeyCombDown(player) || scroll == 0) return;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.lock.next(scroll < 0);
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(wandOptions.lock, true));
        event.setCanceled(true);
    }

    // Sneak+(OPT)+Left click wand to change core
    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();

        if(player == null || !modeKeyCombDown(player)) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.cores.next();
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(wandOptions.cores, true));
    }

    // Sneak+(OPT)+Right click wand to open GUI
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if(event.getSide().isServer()) return;

        Player player = event.getEntity();
        if(player == null || !guiKeyCombDown(player)) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        Minecraft.getInstance().setScreen(new ScreenWand(wand));
        event.setCanceled(true);
        // Consume the click: the client would otherwise follow up with the wand's air use, which is the
        // sneaking right click the slay core kills with.
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    // Sneak+(OPT)+Right click a block with the wand also opens the GUI instead of using the block,
    // so the screen is reachable no matter what the player is looking at.
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.getSide().isServer()) return;

        Player player = event.getEntity();
        if(player == null || !guiKeyCombDown(player)) return;

        ItemStack wand = player.getItemInHand(event.getHand());
        if(!(wand.getItem() instanceof ItemWand)) return;

        Minecraft.getInstance().setScreen(new ScreenWand(wand));
        event.setCanceled(true);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        // Same as above: the GUI click must not fall through to the item's sneaking right click.
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    private static boolean isKeyDown(int id) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), id);
    }

    public static boolean isOptKeyDown() {
        return isKeyDown(ConfigClient.OPT_KEY.get());
    }

    public static boolean modeKeyCombDown(Player player) {
        return player.isCrouching() && (isOptKeyDown() || !ConfigClient.SHIFTOPT_MODE.get());
    }

    public static boolean guiKeyCombDown(Player player) {
        return player.isCrouching() && (isOptKeyDown() || !ConfigClient.SHIFTOPT_GUI.get());
    }
}
