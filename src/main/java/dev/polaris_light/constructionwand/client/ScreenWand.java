package dev.polaris_light.constructionwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.api.IWandAction;
import dev.polaris_light.constructionwand.api.IWandCore;
import dev.polaris_light.constructionwand.api.IWandUpgrade;
import dev.polaris_light.constructionwand.basics.ConfigClient;
import dev.polaris_light.constructionwand.basics.ConfigServer;
import dev.polaris_light.constructionwand.basics.option.IOption;
import dev.polaris_light.constructionwand.basics.option.WandOptions;
import dev.polaris_light.constructionwand.basics.option.WandUpgradesSelectable;
import dev.polaris_light.constructionwand.client.ui.Ui;
import dev.polaris_light.constructionwand.client.ui.WandRow;
import dev.polaris_light.constructionwand.wand.action.ActionAngel;
import dev.polaris_light.constructionwand.wand.action.ActionConstruction;
import dev.polaris_light.constructionwand.wand.action.ActionDestruction;
import dev.polaris_light.constructionwand.plus.wand.ActionDigging;
import dev.polaris_light.constructionwand.plus.wand.ActionSlay;
import dev.polaris_light.constructionwand.plus.wand.ActionTime;
import dev.polaris_light.constructionwand.plus.basics.ExtraWandOption;
import dev.polaris_light.constructionwand.plus.basics.SuperFortuneLevels;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;
import dev.polaris_light.constructionwand.plus.basics.PassiveUpgrades;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreLevels;
import dev.polaris_light.constructionwand.plus.basics.WandLimit;
import dev.polaris_light.constructionwand.plus.compat.ae2.AeGridAccess;
import dev.polaris_light.constructionwand.plus.compat.projecte.EmcAccess;
import dev.polaris_light.constructionwand.plus.items.ItemCoreAE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreProjectE;
import dev.polaris_light.constructionwand.plus.items.ItemCoreStorage;
import dev.polaris_light.constructionwand.plus.items.PlusItems;
import dev.polaris_light.constructionwand.plus.material.ContainerBinding;
import dev.polaris_light.constructionwand.plus.network.PacketBoundContainer;
import dev.polaris_light.constructionwand.plus.network.PacketExtraOption;
import dev.polaris_light.constructionwand.plus.network.PacketRemoveCore;
import dev.polaris_light.constructionwand.plus.network.PacketSuperFortune;
import dev.polaris_light.constructionwand.plus.network.PacketTimeClear;
import dev.polaris_light.constructionwand.plus.network.PacketRemoveUpgrade;
import dev.polaris_light.constructionwand.plus.network.PacketToggleUpgrade;
import dev.polaris_light.constructionwand.plus.network.PacketWandLimit;
import dev.polaris_light.constructionwand.network.ModMessages;
import dev.polaris_light.constructionwand.network.PacketWandOption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The wand screen, rebuilt around tabs and a list of rows.
 * <p>
 * The old screen had one fixed grid of buttons, which left nowhere to put a core that brings settings of
 * its own. This one is a small window with a tab rail on the left and one scrollable list per tab, so a
 * core can add rows for whatever it needs and a longer list simply scrolls. Nothing here knows about a
 * particular core: the rows are built from the wand's own state, and the only per-core knowledge is
 * which extra lines of information are worth showing.
 * <p>
 * Tabs: the behaviour and material cores with their status lines, the wand settings (stock options, the
 * settings of the fork and the block limit), the installed passive upgrades, and the bindings of the
 * selected material core (the bound containers with drag to reorder, or the bound ME network).
 * <p>
 * Every change is applied to the wand in the player's hand on the client and confirmed by a packet, so
 * the screen shows its own edits immediately.
 */
public class ScreenWand extends Screen
{
    private static final int MAX_PANEL_WIDTH = 430;
    private static final int MAX_PANEL_HEIGHT = 340;

    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 18;
    private static final int TAB_WIDTH = 78;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 3;
    private static final int ROW_GAP = 2;
    private static final int PADDING = 8;

    private enum Tab
    {
        CORES("cores"),
        STORAGE("storage"),
        OPTIONS("options"),
        UPGRADES("upgrades");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        private Component label() {
            return Component.translatable(ConstructionWand.MODID + ".gui.tab." + key);
        }
    }

    private final ItemStack wand;
    private final WandOptions wandOptions;
    private final List<WandRow> rows = new ArrayList<>();

    private Tab tab = Tab.values()[Mth.clamp(ConfigClient.LAST_TAB.get(), 0, Tab.values().length - 1)];
    private int scroll;
    private int contentHeight;

    /**
     * Row that took the last press, so a drag that leaves the row still reaches it (the slider).
     */
    @Nullable
    private WandRow activeRow;
    /**
     * Binding rows the player is dragging, {@code -1} while not dragging.
     */
    private int dragFrom = -1;
    private int dragTo = -1;

    // Layout, recalculated on every resize
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeightPixels;

    public ScreenWand(ItemStack wand) {
        super(Component.literal("ScreenWand"));
        this.wand = wand;
        wandOptions = new WandOptions(wand);

        // The look is a preference of the player, not of the wand, so it is read from the client config
        // every time the screen opens and written back when the button is used.
        Ui.setLight(ConfigClient.LIGHT_THEME.get());
    }

    @Override
    protected void init() {
        layout();
        refresh();
    }

    private void layout() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(220, width - 20));

        // The window keeps one height, so switching pages does not make it jump: the pages are laid out to
        // fill it instead, and a page that needs more room scrolls.
        int rail = Tab.values().length * (TAB_HEIGHT + TAB_GAP) - TAB_GAP;
        int smallest = HEADER_HEIGHT + FOOTER_HEIGHT + 8 + rail + 4;
        int available = Math.max(smallest, height - 20);
        int wanted = Math.max(270, (int) (available * 0.8F));
        panelHeight = Math.min(Math.min(MAX_PANEL_HEIGHT, wanted), available);

        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        contentX = panelX + PADDING + TAB_WIDTH + 6;
        contentY = panelY + HEADER_HEIGHT + 4;
        contentWidth = panelWidth - (contentX - panelX) - PADDING - 6;
        contentHeightPixels = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
    }

    /**
     * Rebuilds the list of the current tab and lays it out, sizing the window around it: a short page gets
     * a short window instead of a mostly empty one, and a long page scrolls inside the tallest window.
     * Called after every change, because a change can add or remove rows.
     */
    private void refresh() {
        rows.clear();
        // The geometry comes first: a page may fill the room it has left, which it has to know about.
        layout();
        switch(tab) {
            case CORES -> buildCores();
            case OPTIONS -> buildOptions();
            case UPGRADES -> buildUpgrades();
            case STORAGE -> buildStorage();
        }
        layoutRows();
    }

    private void layoutRows() {
        int y = contentY - scroll;
        int total = 0;
        for(WandRow row : rows) {
            row.x = contentX;
            row.width = contentWidth;
            row.y = y;
            y += row.height() + ROW_GAP;
            total += row.height() + ROW_GAP;
        }
        contentHeight = Math.max(0, total - ROW_GAP);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - contentHeightPixels));
    }

    // Rendering

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics, mouseX, mouseY, partialTicks);

        Ui.setAccent(Ui.fromCoreColor(wandOptions.cores.get().getColor()));
        Ui.begin(graphics);
        Ui.panel(graphics, panelX, panelY, panelWidth, panelHeight, Ui.accent());
        renderHeader(graphics, mouseX, mouseY);
        renderTabs(graphics, mouseX, mouseY);
        Ui.footer(graphics, panelX, panelY + panelHeight - FOOTER_HEIGHT, panelWidth, FOOTER_HEIGHT);
        Ui.text(graphics, font, footerHint(), panelX + PADDING + 2, panelY + panelHeight - FOOTER_HEIGHT + 5, Ui.TEXT_DIM);
        renderThemeButton(graphics, mouseX, mouseY);

        layoutRows();
        boolean overContent = overContent(mouseX, mouseY);
        int rowMouseX = overContent ? mouseX : -1;
        int rowMouseY = overContent ? mouseY : -1;

        graphics.enableScissor(contentX - 2, contentY, contentX + contentWidth + 2, contentY + contentHeightPixels);
        for(WandRow row : rows) {
            if(row.y + row.height() < contentY || row.y > contentY + contentHeightPixels) continue;
            row.render(graphics, font, rowMouseX, rowMouseY);
        }
        renderDragState(graphics);
        graphics.disableScissor();

        Ui.scrollBar(graphics, contentX + contentWidth + 4, contentY, contentHeightPixels,
                contentHeight, contentHeightPixels, scroll);

        // Tooltips go through the font's own line splitter: a description may carry a newline of its own
        // and may be longer than the window, and rendering it as one raw component would show the newline
        // as a missing glyph and run the rest of the sentence off the screen.
        if(overContent) {
            WandRow row = rowAt(mouseX, mouseY);
            Component tooltip = row == null ? null : row.tooltip();
            if(tooltip != null) renderTooltip(graphics, tooltip, mouseX, mouseY);
        }
        else if(overClose(mouseX, mouseY)) {
            renderTooltip(graphics, Component.translatable(ConstructionWand.MODID + ".gui.close"), mouseX, mouseY);
        }
        else if(overTheme(mouseX, mouseY)) {
            renderTooltip(graphics, text(Ui.light() ? "gui.theme.to_dark" : "gui.theme.to_light"), mouseX, mouseY);
        }

        // Everything of this frame is drawn, so the mesh can be let go.
        Ui.end();
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        Ui.header(graphics, panelX, panelY, panelWidth, HEADER_HEIGHT);
        graphics.renderItem(wand, panelX + PADDING, panelY + 7);

        String name = font.plainSubstrByWidth(wand.getHoverName().getString(), panelWidth - 130);
        Ui.text(graphics, font, name, panelX + PADDING + 22, panelY + 11, Ui.TEXT_TITLE);

        Component core = coreName(wandOptions.cores, wandOptions.cores.get());
        int closeX = closeX();
        int accent = Ui.accentInk();
        int available = closeX - 12 - (panelX + PADDING + 22 + font.width(name) + 8);
        if(available > 30) {
            String label = font.plainSubstrByWidth(core.getString(), available);
            Ui.text(graphics, font, label, closeX - 12 - font.width(label), panelY + 11, accent);
        }

        boolean hover = overClose(mouseX, mouseY);
        Ui.text(graphics, font, "\u2715", closeX, panelY + 11, hover ? Ui.TEXT_TITLE : Ui.TEXT_DIM);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = contentY;
        for(Tab candidate : Tab.values()) {
            boolean active = candidate == tab;
            boolean hover = isOverTab(candidate, mouseX, mouseY);
            int x = tabX();

            if(active) Ui.rounded(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, Ui.mix(Ui.accent(), 0x55));
            else if(hover) Ui.rounded(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, 0x1AFFFFFF);

            int color = active ? Ui.TEXT_TITLE : (hover ? Ui.TEXT : Ui.TEXT_DIM);
            Component label = candidate.label();
            Ui.text(graphics, font, label, x + 10, y + 6, color);
            if(active) graphics.fill(x, y + 3, x + 2, y + TAB_HEIGHT - 3, Ui.accent());

            y += TAB_HEIGHT + TAB_GAP;
        }
    }

    /**
     * Marks where the dragged binding would land, and which row is being dragged.
     */
    private void renderDragState(GuiGraphics graphics) {
        if(dragFrom < 0 || dragTo < 0) return;

        for(WandRow row : rows) {
            if(row instanceof WandRow.Binding binding && binding.index == dragFrom) {
                Ui.rounded(graphics, row.x, row.y, row.width, row.height(), 0x33FFFFFF);
            }
            if(row instanceof WandRow.Binding binding && binding.index == dragTo) {
                graphics.fill(row.x + 2, row.y - 2, row.x + row.width - 2, row.y - 1, Ui.accent());
            }
        }
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button == 0 && overClose(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if(button == 0 && overTheme(mouseX, mouseY)) {
            toggleTheme();
            return true;
        }
        if(button == 0) {
            for(Tab candidate : Tab.values()) {
                if(!isOverTab(candidate, mouseX, mouseY)) continue;
                if(candidate != tab) {
                    setTab(candidate);
                    scroll = 0;
                    refresh();
                }
                return true;
            }
        }

        if(!overContent(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);

        WandRow row = rowAt(mouseX, mouseY);
        if(row == null) return true;

        if(row.mouseClicked(mouseX, mouseY, button)) {
            activeRow = row;
            return true;
        }
        // A binding row is grabbed anywhere but on its cross, which is what reorders the list.
        if(button == 0 && row instanceof WandRow.Binding binding && isStorageCore()) {
            dragFrom = binding.index;
            dragTo = binding.index;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(dragFrom >= 0) {
            scrollWhileDragging(mouseY);
            int target = bindingRowAt(mouseY);
            if(target >= 0) dragTo = target;
            return true;
        }
        if(activeRow != null && activeRow.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(dragFrom >= 0 && button == 0) {
            int from = dragFrom;
            int to = dragTo;
            dragFrom = -1;
            dragTo = -1;
            moveBinding(from, to);
            return true;
        }
        boolean handled = activeRow != null && activeRow.mouseReleased(mouseX, mouseY, button);
        activeRow = null;
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(!overContent(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        // A row that scrolls on its own (the core list) takes the wheel before the page does.
        for(WandRow row : rows) {
            if(row.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }

        int max = Math.max(0, contentHeight - contentHeightPixels);
        int next = Mth.clamp(scroll - (int) Math.signum(scrollY) * (WandRow.ROW_HEIGHT + ROW_GAP), 0, max);
        if(next == scroll) return false;
        scroll = next;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        // The tabs are also reachable without aiming at the rail.
        if(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            switchTab(1);
            return true;
        }
        if(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            switchTab(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void switchTab(int delta) {
        Tab[] all = Tab.values();
        setTab(all[Math.floorMod(tab.ordinal() + delta, all.length)]);
        scroll = 0;
        refresh();
    }

    // Contents of the tabs

    /**
     * Choosing the two cores, plus what the chosen material core is bound to. The settings of a core are
     * not repeated here: each of them has its own page on the options tab.
     */
    private void buildCores() {
        // The list is the selector: every installed core is a row, and clicking one selects it. Cycling
        // through a value box needed as many clicks as the wanted core was far away in the order, which
        // stops working once a wand carries a handful of cores.
        rows.add(selectedCoreRow(text("gui.core.behaviour"), wandOptions.cores));
        rows.add(corePicker(wandOptions.cores, roomLeft(0)));
    }

    /**
     * The storage page: which material core the wand takes its blocks from, and where that core is bound
     * to. The two belong together, a storage core without its bound containers has nothing to give.
     */
    private void buildStorage() {
        rows.add(selectedCoreRow(text("gui.core.material"), wandOptions.materialCores));
        rows.add(corePicker(wandOptions.materialCores, roomLeft(0)));
        buildBindings();
    }

    /**
     * One row per installed core, the chosen one marked. A shift click uninstalls that core, the hint in the
     * footer says so.
     */
    /**
     * The core in use, in the same value box the screen used before, only without the two arrows: the box
     * made sense while it was the selector, and now it is a plain reading of the slot.
     */
    private WandRow selectedCoreRow(Component label, WandUpgradesSelectable<IWandCore> option) {
        return new WandRow.Cycle(coreIcon(option.get()), label, null, coreName(option, option.get()), true,
                null, false, false, back -> {
                });
    }

    /**
     * @return the height the page has left for a block that fills it, minus a gap above it
     */
    private int roomLeft(int reserved) {
        int used = reserved;
        for(WandRow row : rows) {
            used += row.height() + ROW_GAP;
        }
        // The block adds its own padding around the window it is given, and the page counts a gap after it:
        // both come off here, or the page ends two pixels too tall and shows its scrollbar for nothing.
        return Math.max(WandRow.ROW_HEIGHT, contentHeightPixels - used - ROW_GAP - 12);
    }

    private WandRow corePicker(WandUpgradesSelectable<IWandCore> option, int fill) {
        IWandCore selected = option.get();
        List<WandRow.TwoColumn.Entry> entries = new ArrayList<>();
        for(IWandCore core : option.getUpgrades()) {
            entries.add(new WandRow.TwoColumn.Entry(coreName(option, core), core == selected));
        }
        return new WandRow.TwoColumn(entries,
                text("gui.status.description").copy().append(translate(option.getDescTranslation())), fill,
                index -> selectCore(option, option.getUpgrades().get(index)));
    }

    private void selectCore(WandUpgradesSelectable<IWandCore> option, IWandCore core) {
        if(Screen.hasShiftDown()) {
            ResourceLocation id = core == null ? null : core.getRegistryName();
            if(id != null && !id.toString().endsWith(":default") && !id.toString().endsWith(":none")) {
                ModMessages.sendToServer(new PacketRemoveCore(id.toString()));
                option.removeUpgrade(core);
                refresh();
            }
            return;
        }
        if(option.get() == core) return;

        option.set(core);
        ModMessages.sendToServer(new PacketWandOption(option, false));
        refresh();
    }

    /**
     * @return the description of that core, from the key the option builds its names with
     */
    private Component coreDesc(WandUpgradesSelectable<IWandCore> option, IWandCore core) {
        if(core == null || core.getRegistryName() == null) return Component.empty();
        return translate(option.getKeyTranslation() + "." + core.getRegistryName() + ".desc");
    }

    /**
     * The options tab is the page of the behaviour core that is in use: it lists what that core actually
     * reads and leaves everything else out, so a wand never offers a setting the selected core ignores.
     * <p>
     * The stock settings are grouped by what the core does with them: a core that places blocks reads the
     * orientation settings, a core that works on a shape reads the restriction and the matching, and the
     * block limit belongs to any of them. Everything a core needs beyond that (the radius of the slay
     * core, the ladder of the time core, the harvest settings) is listed under the name of that core.
     */
    private void buildOptions() {
        IWandCore core = wandOptions.cores.get();
        IWandAction action = core.getWandAction();

        boolean places = action instanceof ActionConstruction || action instanceof ActionAngel;
        boolean shapes = places || action instanceof ActionDestruction || action instanceof ActionDigging;
        // A core from another mod is unknown: rather than hiding settings it might read, it is offered
        // everything the wand has.
        boolean permissive = !shapes && !(action instanceof ActionSlay) && !(action instanceof ActionTime);

        List<WandRow> own = new ArrayList<>();
        if(action instanceof ActionSlay) {
            own.add(limitSlider(true));
            own.add(extraCycle(ExtraWandOption.SILK_TOUCH));
            own.add(extraCycle(ExtraWandOption.DROPS_DESTINATION));
            own.add(superFortuneSlider());
            own.add(extraCycle(ExtraWandOption.SLAY_ALL_LOOT));
        }
        else if(action instanceof ActionTime) {
            // Both settings of the time core are picked right here, which is what replaced clicking the
            // same block over and over to raise its multiplier.
            own.add(extraCycle(ExtraWandOption.TIME_MULTIPLIER));
            own.add(extraCycle(ExtraWandOption.TIME_DURATION));
            own.add(new WandRow.Text(text("gui.info.time_cap"),
                    Component.literal(TimeCoreLevels.cap(wand) + "x"), Ui.OK, true, null));
            // The blocks are accelerated out in the world, so this is the one place to let them go. Letting
            // every block go at once is not something to do by accident, so the button asks first.
            own.add(new WandRow.Action(text("gui.time.clear"), text("gui.time.clear.button"), true,
                    text("gui.time.clear.desc"), () -> ModMessages.sendToServer(new PacketTimeClear()),
                    "constructionwand.gui.time.clear.confirm"));

            // What the wand is actually running: name, dimension, position and speed of every accelerated
            // block, each with its own way to let it go. The name comes from the block id that travels with
            // the effect, so it is known even for another dimension or a chunk this client never loaded.
            own.add(new WandRow.Header(text("gui.section.accelerated")));
            List<TimeAcceleration.Entry> accelerated = TimeClientState.all();
            if(accelerated.isEmpty()) {
                own.add(WandRow.Text.plain(text("gui.section.accelerated.empty")));
            }
            else {
                for(TimeAcceleration.Entry entry : accelerated) {
                    own.add(new WandRow.Action(acceleratedLabel(entry), text("gui.time.cancel.block"), true,
                            text("gui.time.cancel.block.desc"),
                            () -> ModMessages.sendToServer(new PacketTimeClear(entry.pos())),
                            "constructionwand.gui.time.cancel.block.confirm"));
                }
            }
        }
        else if(action instanceof ActionDigging) {
            own.add(extraCycle(ExtraWandOption.SILK_TOUCH));
            own.add(extraCycle(ExtraWandOption.DROPS_DESTINATION));
            own.add(superFortuneSlider());
            own.add(extraCycle(ExtraWandOption.FLUID_REMOVAL));
            own.add(extraCycle(ExtraWandOption.FLUID_STORAGE));
        }
        else if(action instanceof ActionDestruction) {
            own.add(extraCycle(ExtraWandOption.FLUID_REMOVAL));
        }
        else if(permissive) {
            for(ExtraWandOption option : ExtraWandOption.values()) own.add(extraCycle(option));
        }

        if(!own.isEmpty()) {
            rows.add(new WandRow.Header(coreName(wandOptions.cores, core)));
            rows.addAll(own);
        }

        if(!shapes && !permissive) return;

        rows.add(new WandRow.Header(text("gui.section.wand")));
        if(places || permissive) {
            rows.add(optionCycle(wandOptions.direction));
            rows.add(optionCycle(wandOptions.replace));
            rows.add(optionCycle(wandOptions.random));
        }
        rows.add(optionCycle(wandOptions.lock));
        rows.add(optionCycle(wandOptions.match));

        // The slay core answers the same slider as its radius, which is listed under its own name above.
        if(action instanceof ActionSlay) return;
        if(configuredLimitMax() > 1) rows.add(limitSlider(false));
        else rows.add(WandRow.Text.plain(text("gui.info.single_block")));
    }

    private WandRow optionCycle(IOption<?> option) {
        return new WandRow.Cycle(null, translate(option.getKeyTranslation()), null,
                translate(option.getValueTranslation()), option.isEnabled(),
                translate(option.getDescTranslation()), false, back -> cycleOption(option, back));
    }

    /**
     * The slider of the block limit, or the radius of the slay core, which is the same number for a core
     * that kills in an area instead of working on an area of blocks.
     */
    private WandRow limitSlider(boolean radius) {
        int configured = configuredLimitMax();
        int current = WandLimit.resolve(wand, configured);
        String key = radius ? "gui.limit.slay_radius" : WandLimit.getKeyTranslation();
        String valueKey = radius ? "gui.limit.slay_radius.value" : WandLimit.getValueTranslation();
        Component label = radius ? text(key) : translate(key);
        java.util.function.IntFunction<Component> value =
                shown -> radius ? text(valueKey, shown) : translate(valueKey, shown);
        Component tooltip = radius ? text("gui.limit.slay_radius.desc", configured)
                : translate(WandLimit.getDescriptionTranslation(), configured);

        return new WandRow.Slider(label, value, 1, configured, current, true, tooltip, limit -> {
            WandLimit.set(wand, limit);
            ModMessages.sendToServer(new PacketWandLimit(limit));
            refresh();
        });
    }

    /**
     * A value row for one of the extra options.
     * <p>
     * Silk touch is the one that can stand in the way of another component: while the fortune component is
     * on and the wand digs, silk touch does nothing. The row is then greyed out and explains itself instead
     * of quietly showing the value it falls back to, and it keeps showing what the wand is set to, so the
     * setting is still there once the component is switched off again.
     */
    private WandRow extraCycle(ExtraWandOption option) {
        boolean blocked = option == ExtraWandOption.SILK_TOUCH && ExtraWandOption.isSilkTouchDisabled(wand);
        int index = blocked ? ExtraWandOption.getIndex(wand, option)
                : ExtraWandOption.getDisplayIndex(wand, option);
        Component tooltip = translate(option.getDescTranslation(index));
        if(blocked) {
            tooltip = tooltip.copy().append("\n").append(text("option.silk_touch.blocked_fortune"));
        }
        return new WandRow.Cycle(null, translate(option.getKeyTranslation()),
                translate(option.getDescTranslation(index)), translate(option.getValueTranslation(index)),
                !blocked, tooltip, true, back -> cycleExtra(option, back));
    }

    /**
     * The super fortune factor as a slider: every whole number from one to the cap of the wand's tier can
     * be picked, so the row is continuous instead of stepping through a handful of values. One is the same
     * as no multiplication; zero is left out on purpose, since it would delete the drops outright.
     */
    private WandRow superFortuneSlider() {
        int cap = SuperFortuneLevels.cap(wand);
        int current = SuperFortuneLevels.get(wand);

        return new WandRow.Slider(text("option.super_fortune"), shown -> Component.literal(shown + "x"),
                SuperFortuneLevels.MIN, cap, current, true, text("option.super_fortune.desc", cap), value -> {
            SuperFortuneLevels.set(wand, value);
            ModMessages.sendToServer(new PacketSuperFortune(value));
            refresh();
        });
    }

    private void buildUpgrades() {
        List<Item> upgrades = PassiveUpgrades.installed(wand);
        rows.add(new WandRow.Header(text("gui.upgrades")));

        if(upgrades.isEmpty()) {
            rows.add(WandRow.Text.plain(text("gui.upgrades.none")));
            return;
        }

        for(Item upgrade : upgrades) {
            Component label = Component.translatable(upgrade.getDescriptionId());
            Component description = Component.translatable(ConstructionWand.MODID + ".upgrade."
                    + path(upgrade) + ".desc");
            boolean on = !PassiveUpgrades.isDisabled(wand, upgrade);

            // The description is already the second line of the row, so the tooltip only carries how the
            // row is used.
            rows.add(new WandRow.Toggle(new ItemStack(upgrade), label, description, on, true,
                    text("upgrade.toggle_hint"), () -> clickUpgrade(upgrade)));
        }
    }

    private void buildBindings() {
        if(isStorageCore()) {
            List<ContainerBinding.Bound> bounds = boundContainers();
            int capacity = ContainerBinding.getMaxContainers();
            Component title = text("gui.bindings");
            if(capacity >= 0) title = title.copy().append(" " + bounds.size() + "/" + capacity);
            else if(!bounds.isEmpty()) title = title.copy().append(" " + bounds.size());
            rows.add(new WandRow.Header(title));

            if(bounds.isEmpty()) {
                rows.add(WandRow.Text.plain(text("gui.binding.unbound")));
            }
            else {
                Level level = Minecraft.getInstance().level;
                for(int index = 0; index < bounds.size(); index++) {
                    ContainerBinding.Bound bound = bounds.get(index);
                    rows.add(new WandRow.Binding(index, bound.pos(), Component.literal(bindingName(bound, level)),
                            Component.literal(coords(bound)), text("gui.binding.row_hint"),
                            () -> removeBinding(bound)));
                }
            }
            rows.add(WandRow.Text.plain(text("gui.binding.hint_storage")));
            if(bounds.size() > 1) rows.add(WandRow.Text.plain(text("gui.hint.drag")));
            return;
        }

        rows.add(new WandRow.Header(text("gui.bindings")));
        if(isMaterialCore(PlusItems.CORE_AE)) {
            AeBinding binding = aeBinding();
            rows.add(new WandRow.Text(text("gui.binding.ae"), binding.value(), binding.color(), true, null));
            rows.add(WandRow.Text.plain(text("gui.binding.hint_ae")));
            return;
        }
        rows.add(WandRow.Text.plain(text("gui.binding.none")));
    }

    // Actions, each applying the change locally and telling the server

    private void cycleCore(WandUpgradesSelectable<IWandCore> option, boolean back) {
        if(Screen.hasShiftDown() && removeSelectedCore(option)) return;

        option.next(!back);
        ModMessages.sendToServer(new PacketWandOption(option, false));
        refresh();
    }

    private boolean removeSelectedCore(WandUpgradesSelectable<IWandCore> option) {
        String id = option.getValueString();
        if(id == null || id.endsWith(":default") || id.endsWith(":none")) return false;

        ModMessages.sendToServer(new PacketRemoveCore(id));
        option.removeUpgrade(option.get());
        refresh();
        return true;
    }

    private void cycleOption(IOption<?> option, boolean back) {
        option.next(!back);
        ModMessages.sendToServer(new PacketWandOption(option, false));
        refresh();
    }

    private void cycleExtra(ExtraWandOption option, boolean back) {
        int next = stepExtraIndex(option, !back);
        if(next < 0 || !ExtraWandOption.setIndex(wand, option, next)) return;

        ModMessages.sendToServer(new PacketExtraOption(option, next));
        refresh();
    }

    private void clickUpgrade(Item upgrade) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(upgrade);
        if(id == null) return;

        if(Screen.hasShiftDown()) {
            ModMessages.sendToServer(new PacketRemoveUpgrade(id.toString()));
            wandOptions.upgrades.removeUpgrade((IWandUpgrade) upgrade);
        }
        else {
            boolean disabled = !PassiveUpgrades.isDisabled(wand, upgrade);
            ModMessages.sendToServer(new PacketToggleUpgrade(id.toString(), disabled));
            PassiveUpgrades.setDisabled(wand, upgrade, disabled);
        }
        refresh();
    }

    private void removeBinding(ContainerBinding.Bound bound) {
        Level level = Minecraft.getInstance().level;
        if(level == null) return;

        ModMessages.sendToServer(new PacketBoundContainer(PacketBoundContainer.ACTION_REMOVE, bound.pos()));
        ContainerBinding.removeBinding(wand, bound.pos(), level);
        refresh();
    }

    /**
     * Moves a row to where it was dropped, on the client and on the server.
     */
    private void moveBinding(int from, int to) {
        List<ContainerBinding.Bound> bounds = boundContainers();
        if(from < 0 || from >= bounds.size() || to < 0 || to >= bounds.size() || from == to) return;

        Level level = Minecraft.getInstance().level;
        if(level == null) return;

        ContainerBinding.Bound bound = bounds.get(from);
        // The list was built from an earlier read: skip a drop whose binding the wand no longer carries.
        if(!ContainerBinding.isBound(wand, bound.pos(), level)) return;

        ModMessages.sendToServer(new PacketBoundContainer(PacketBoundContainer.ACTION_MOVE, bound.pos(), to));
        ContainerBinding.moveBindingTo(wand, bound.pos(), level, to);
        refresh();
    }

    // Small helpers

    private Component text(String key) {
        return Component.translatable(ConstructionWand.MODID + "." + key);
    }

    private Component text(String key, Object... args) {
        return Component.translatable(ConstructionWand.MODID + "." + key, args);
    }

    /**
     * For the keys that are already complete, which is how the wand options, the extra settings and the
     * block limit hand theirs out.
     */
    private static Component translate(String key) {
        return Component.translatable(key);
    }

    private static Component translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String path(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "unknown" : id.getPath();
    }

    private Component coreName(WandUpgradesSelectable<IWandCore> option,
                               IWandCore core) {
        if(core == null || core.getRegistryName() == null) return Component.empty();
        return Component.translatable(option.getKeyTranslation() + "." + core.getRegistryName().toString());
    }

    @Nullable
    private ItemStack coreIcon(@Nullable IWandCore core) {
        return core instanceof Item item ? new ItemStack(item) : null;
    }



    /**
     * @return the next value this wand can actually use; values that need a core the wand does not carry
     * (sending the drops to the material core without one) are skipped
     */
    private int stepExtraIndex(ExtraWandOption option, boolean forward) {
        int count = option.getValueCount();
        int current = ExtraWandOption.getDisplayIndex(wand, option);
        for(int step = 1; step <= count; step++) {
            int candidate = Math.floorMod(current + (forward ? step : -step), count);
            if(option.isValueAllowed(wand, candidate)) return candidate;
        }
        return -1;
    }



    /**
     * The highest value the limit of the selected core may be set to: the configured one for that wand
     * tier, before the per wand limit lowers it, so a limit that was lowered can be raised again.
     */
    /**
     * The dimension, the block and its position, in one line: what is accelerated and where.
     */
    private Component acceleratedLabel(TimeAcceleration.Entry entry) {
        Component dimension = Component.translatable(entry.dimension().location().toLanguageKey("dimension"));
        Component name = acceleratedName(entry);
        return dimension.copy().append(Component.literal(" · ")).append(name)
                .append(Component.literal(" (" + entry.pos().getX() + ", " + entry.pos().getY() + ", "
                        + entry.pos().getZ() + ") · " + entry.rate() + "x"));
    }

    /**
     * The block id that travels with the effect is the reliable source: it names a block in another
     * dimension, or in a chunk this client has never loaded. Only a missing id (an effect from a save that
     * predates it) falls back to the live block, which needs the chunk, and then to a question mark.
     */
    private Component acceleratedName(TimeAcceleration.Entry entry) {
        if(entry.blockId() != null && !entry.blockId().isEmpty()) {
            return Component.translatable("block." + entry.blockId().replace(':', '.'));
        }
        var level = Minecraft.getInstance().level;
        if(level != null && level.dimension().equals(entry.dimension()) && level.isLoaded(entry.pos())) {
            return level.getBlockState(entry.pos()).getBlock().getName();
        }
        return Component.literal("?");
    }

    private int configuredLimitMax() {
        // Deliberately the configured maximum, not the value in use: a value that was lowered has to be
        // raisable again.
        return WandLimit.configuredMax(wand);
    }

    private List<ContainerBinding.Bound> boundContainers() {
        if(!isStorageCore()) return List.of();
        return ContainerBinding.read(wand);
    }

    private boolean isStorageCore() {
        return wandOptions.materialCores.get() instanceof ItemCoreStorage;
    }

    /**
     * @return whether the selected material core is the named one. The cores of the optional mods are
     * absent while those mods are not installed, which the null holder stands for.
     */
    private boolean isMaterialCore(@Nullable DeferredHolder<Item, Item> core) {
        return core != null && core.get() == wandOptions.materialCores.get();
    }

    private record EmcStatus(String value, int color)
    {
    }

    /**
     * The EMC the ProjectE core would spend. It is read from the player, because that is where ProjectE
     * keeps it: the core takes blocks out of the player's EMC and sells drops back into it.
     */
    private EmcStatus emcStatus() {
        if(minecraft == null || minecraft.player == null) return new EmcStatus("-", Ui.TEXT_OFF);

        String emc = EmcAccess.read(minecraft.player);
        if(emc == null) return new EmcStatus(text("gui.binding.no_knowledge").getString(), Ui.TEXT_OFF);
        return new EmcStatus(emc, EmcAccess.hasEmc(minecraft.player) ? Ui.OK : Ui.TEXT_OFF);
    }

    private record AeBinding(Component value, int color)
    {
    }

    private AeBinding aeBinding() {
        AeGridAccess.Bound binding =
                AeGridAccess.readBinding(wand);
        if(binding == null) {
            return new AeBinding(text("gui.binding.unbound"), Ui.TEXT_OFF);
        }
        return new AeBinding(Component.literal(binding.pos().toShortString() + " @ " + dimensionName(binding.dimension())),
                Ui.OK);
    }

    private static String dimensionName(net.minecraft.resources.ResourceKey<Level> dimension) {
        String value = dimension.location().toString();
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private String coords(ContainerBinding.Bound bound) {
        return "(" + dimensionName(bound.dimension()) + " " + bound.pos().toShortString() + ")";
    }

    /**
     * The name of a bound container, falling back to nothing when the world cannot name it: the row then
     * shows the coordinates alone instead of repeating them.
     */
    private String bindingName(ContainerBinding.Bound bound, @Nullable Level level) {
        String name = ContainerBinding.displayName(level, bound);
        if(name == null || name.isEmpty() || name.equals(bound.pos().toShortString())) return "";
        return name;
    }

    private Component footerHint() {
        return switch(tab) {
            case CORES, STORAGE -> text("gui.hint.select").copy().append(" · ").append(text("gui.hint.core_removable"));
            case OPTIONS -> text("gui.hint.change");
            case UPGRADES -> text("upgrade.toggle_hint");
        };
    }

    // Geometry

    /**
     * Opens a tab and remembers it, so the next time the screen is opened it is where it was left.
     */
    private void setTab(Tab candidate) {
        tab = candidate;
        ConfigClient.LAST_TAB.set(candidate.ordinal());
        ConfigClient.SPEC.save();
    }

    private int tabX() {
        return panelX + PADDING;
    }

    private boolean isOverTab(Tab candidate, double mouseX, double mouseY) {
        int index = candidate.ordinal();
        int y = contentY + index * (TAB_HEIGHT + TAB_GAP);
        return mouseX >= tabX() && mouseX < tabX() + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    private int closeX() {
        return panelX + panelWidth - PADDING - 8;
    }

    /**
     * The theme button, at the right end of the footer: a sun for the light look, a moon for the dark one.
     * It shows the theme it switches to, the way a toggle does.
     */
    private void renderThemeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int size = 16;
        int x = themeX();
        int y = themeY();
        boolean hover = overTheme(mouseX, mouseY);

        int background = hover ? Ui.mix(0xFFFFFF, Ui.light() ? 0x33 : 0x1E) : Ui.FOOTER;
        Ui.rounded(graphics, x, y, size, size, background, 3);

        // The icon is the theme in use: a sun while the light one is on, a moon while the dark one is.
        int icon = hover ? Ui.TEXT_TITLE : Ui.TEXT_DIM;
        double centreX = x + size / 2.0D;
        double centreY = y + size / 2.0D;
        if(Ui.light()) Ui.sun(graphics, centreX, centreY, icon);
        else Ui.moon(graphics, centreX, centreY, icon);
    }

    private int themeX() {
        return panelX + panelWidth - PADDING - 16;
    }

    private int themeY() {
        return panelY + panelHeight - FOOTER_HEIGHT + 1;
    }

    /**
     * Draws a tooltip over the panel, wrapped to it. Everything the screen shows goes through here, so a
     * long description and a description with a newline in it are both handled in one place.
     */
    private void renderTooltip(GuiGraphics graphics, Component tooltip, int mouseX, int mouseY) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(tooltip, Math.max(80, panelWidth - 40));
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    private boolean overTheme(double mouseX, double mouseY) {
        return mouseX >= themeX() - 2 && mouseX < themeX() + 18
                && mouseY >= themeY() - 1 && mouseY < themeY() + 17;
    }

    private void toggleTheme() {
        Ui.setLight(!Ui.light());
        ConfigClient.LIGHT_THEME.set(Ui.light());
        ConfigClient.SPEC.save();
    }

    private boolean overClose(double mouseX, double mouseY) {
        return mouseX >= closeX() - 3 && mouseX < closeX() + 11 && mouseY >= panelY + 6 && mouseY < panelY + 22;
    }

    private boolean overContent(double mouseX, double mouseY) {
        return mouseX >= contentX && mouseX < contentX + contentWidth + 6
                && mouseY >= contentY && mouseY < contentY + contentHeightPixels;
    }

    @Nullable
    private WandRow rowAt(double mouseX, double mouseY) {
        for(WandRow row : rows) {
            if(row.contains(mouseX, mouseY)) return row;
        }
        return null;
    }

    /**
     * @return the index of the binding row the mouse is over, or {@code -1}. Used while dragging, where a
     * position between two rows counts for the one above.
     */
    private int bindingRowAt(double mouseY) {
        int fallback = -1;
        for(WandRow row : rows) {
            if(!(row instanceof WandRow.Binding binding)) continue;
            if(mouseY >= row.y && mouseY < row.y + row.height()) return binding.index;
            if(mouseY >= row.y) fallback = binding.index;
        }
        return fallback;
    }

    /**
     * Dragging a row past the top or the bottom edge scrolls the list, so a list longer than the window
     * can still be reordered in one go.
     */
    private void scrollWhileDragging(double mouseY) {
        int max = Math.max(0, contentHeight - contentHeightPixels);
        if(max == 0) return;

        int next = scroll;
        if(mouseY < contentY + 6) next -= 4;
        else if(mouseY > contentY + contentHeightPixels - 6) next += 4;
        scroll = Mth.clamp(next, 0, max);
    }
}
