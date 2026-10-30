package thetadev.constructionwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.option.IOption;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.network.PacketWandOption;
import thetadev.constructionwand.plus.basics.ExtraWandOption;
import thetadev.constructionwand.plus.basics.PassiveUpgrades;
import thetadev.constructionwand.plus.basics.WandLimit;
import thetadev.constructionwand.plus.items.ItemCoreStorage;
import thetadev.constructionwand.plus.material.ContainerBinding;
import thetadev.constructionwand.plus.network.PacketBoundContainer;
import thetadev.constructionwand.plus.network.PacketExtraOption;
import thetadev.constructionwand.plus.network.PacketRemoveCore;
import thetadev.constructionwand.plus.network.PacketRemoveUpgrade;
import thetadev.constructionwand.plus.network.PacketToggleUpgrade;
import thetadev.constructionwand.plus.network.PacketWandLimit;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The wand screen.
 * <p>
 * The left half cycles through the wand settings, the extra settings of the plus content and the
 * per-wand block limit. The right half lists the installed passive upgrades and the containers the
 * wand is bound to: an upgrade is switched on or off with a left click and uninstalled with a shift
 * click, a bound container can be moved in the priority order or unbound.
 * <p>
 * Every change is applied to the wand in the player's hand on the client and confirmed by a packet,
 * so the screen shows its own edits immediately.
 */
public class ScreenWand extends Screen
{
    private final ItemStack wand;
    private final WandOptions wandOptions;

    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING_WIDTH = 20;
    private static final int SPACING_HEIGHT = 26;
    private static final int N_COLS = 2;
    private static final int N_ROWS = 6;
    private static final int PANEL_GAP = 24;
    private static final int PANEL_WIDTH = 190;
    private static final int ROW_HEIGHT = 22;
    private static final int ICON_SIZE = 20;
    private static final int ROW_BUTTON_SIZE = 14;
    private static final int ROW_BUTTON_GAP = 2;

    /** Index of the first binding row shown; the list scrolls when the wand carries more than fit. */
    private int bindingScroll;
    /** Row the player grabbed, and the row it would be dropped on; {@code -1} while not dragging. */
    private int bindingDragFrom = -1;
    private int bindingDragTo = -1;

    private static final int FIELD_WIDTH = N_COLS * (BUTTON_WIDTH + SPACING_WIDTH) - SPACING_WIDTH;
    private static final int FIELD_HEIGHT = N_ROWS * (BUTTON_HEIGHT + SPACING_HEIGHT) - SPACING_HEIGHT;

    public ScreenWand(ItemStack wand) {
        super(Component.literal("ScreenWand"));
        this.wand = wand;
        wandOptions = new WandOptions(wand);
    }

    @Override
    protected void init() {
        createButton(0, 0, wandOptions.cores);
        createButton(1, 0, wandOptions.materialCores);
        createButton(0, 1, wandOptions.lock);
        createButton(1, 1, wandOptions.direction);
        createButton(0, 2, wandOptions.replace);
        createButton(1, 2, wandOptions.match);
        createButton(0, 3, wandOptions.random);
        createExtraButton(1, 3, ExtraWandOption.SILK_TOUCH);
        createExtraButton(0, 4, ExtraWandOption.DROPS_DESTINATION);
        createExtraButton(1, 4, ExtraWandOption.FLUID_REMOVAL);
        createExtraButton(1, 5, ExtraWandOption.FLUID_STORAGE);

        // Block limit slider, only usable when the wand allows lowering its own limit.
        int configured = Math.max(1, wandOptions.cores.get().getWandAction().getLimit(wand));
        int current = WandLimit.resolve(wand, configured);
        addRenderableWidget(new LimitSlider(getX(0), getY(5), BUTTON_WIDTH, BUTTON_HEIGHT, current, configured));

        createUpgradeButtons();
        createBindingButtons();
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics);
        int originX = getOriginX();
        int originY = getOriginY();
        guiGraphics.drawCenteredString(font, wand.getDisplayName(), originX + FIELD_WIDTH / 2 + (PANEL_GAP + PANEL_WIDTH) / 2, originY - SPACING_HEIGHT, 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderPanel(guiGraphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Scrolling and dragging of the binding list

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = bindingRowCount();
        int visible = bindingVisibleRows();
        if(rows > visible && isOverBindings(mouseX, mouseY)) {
            int offset = Mth.clamp(scrollOffset() - (int) Math.signum(delta), 0, rows - visible);
            if(offset != bindingScroll) {
                bindingScroll = offset;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = bindingRowAt(mouseX, mouseY);
        if(button == 0 && row >= 0) {
            bindingDragFrom = row;
            bindingDragTo = row;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(bindingDragFrom >= 0) {
            scrollWhileDragging(mouseY);

            int row = bindingRowAt(mouseX, mouseY);
            if(row >= 0) bindingDragTo = row;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(bindingDragFrom >= 0 && button == 0) {
            int from = bindingDragFrom;
            int to = bindingDragTo;
            bindingDragFrom = -1;
            bindingDragTo = -1;
            moveBinding(from, to);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Moves a row to the position it was dropped on, on the client and on the server. The wand only
     * carries the new order afterwards, the server confirms it the same way the buttons do.
     */
    private void moveBinding(int from, int to) {
        List<ContainerBinding.Bound> bounds = boundContainers();
        if(from < 0 || from >= bounds.size() || to < 0 || to >= bounds.size() || from == to) return;

        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if(level == null) return;

        ContainerBinding.Bound bound = bounds.get(from);
        ConstructionWand.instance.HANDLER.sendToServer(
                new PacketBoundContainer(PacketBoundContainer.ACTION_MOVE, bound.pos(), to));
        ContainerBinding.moveBindingTo(wand, bound.pos(), level, to);
        rebuildWidgets();
    }

    /**
     * @return the index of the binding row under the mouse, or {@code -1}. Only the part left of the
     * controls is grabbable, so the arrow and unbind buttons keep doing their own job.
     */
    private int bindingRowAt(double mouseX, double mouseY) {
        if(!(wandOptions.materialCores.get() instanceof ItemCoreStorage)) return -1;
        if(mouseX < getPanelX() || mouseX > bindingControlsX()) return -1;

        int rows = boundContainers().size();
        int offset = scrollOffset();
        for(int index = offset; index < rows && index < offset + bindingVisibleRows(); index++) {
            int top = getBindingRowY(index - offset);
            if(mouseY >= top && mouseY < top + ROW_BUTTON_SIZE) return index;
        }
        return -1;
    }

    /**
     * Dragging a row past the top or the bottom edge scrolls the list, so a list longer than the panel
     * can still be reordered in one go.
     */
    private void scrollWhileDragging(double mouseY) {
        int rows = bindingRowCount();
        int visible = bindingVisibleRows();
        if(rows <= visible) return;

        int offset = bindingScroll;
        if(mouseY < getBindingRowY(0) && offset > 0) offset--;
        else if(mouseY > getBindingRowY(visible - 1) + ROW_BUTTON_SIZE && offset + visible < rows) offset++;

        if(offset != bindingScroll) {
            bindingScroll = offset;
            rebuildWidgets();
        }
    }

    private boolean isOverBindings(double mouseX, double mouseY) {
        return mouseX >= getPanelX() && mouseX <= getPanelX() + PANEL_WIDTH
                && mouseY >= getBindingHeaderY() && mouseY <= getOriginY() + FIELD_HEIGHT;
    }

    // Left grid

    private void createButton(int cx, int cy, IOption<?> option) {
        Button button = Button.builder(getButtonLabel(option), bt -> clickButton(bt, option))
                .pos(getX(cx), getY(cy))
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(getButtonTooltip(option))
                .build();

        button.active = option.isEnabled();
        addRenderableWidget(button);
    }

    private void clickButton(Button button, IOption<?> option) {
        // Shift click uninstalls the selected core instead of cycling through the options.
        if(Screen.hasShiftDown() && (option == wandOptions.cores || option == wandOptions.materialCores)) {
            if(removeSelectedCore(option)) return;
        }
        option.next();
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(option, false));
        rebuildWidgets();
    }

    private boolean removeSelectedCore(IOption<?> option) {
        String id = option.getValueString();
        if(id == null || id.endsWith(":default") || id.endsWith(":none")) return false;

        ConstructionWand.instance.HANDLER.sendToServer(new PacketRemoveCore(id));
        if(option == wandOptions.cores) wandOptions.cores.removeUpgrade(wandOptions.cores.get());
        else wandOptions.materialCores.removeUpgrade(wandOptions.materialCores.get());
        rebuildWidgets();
        return true;
    }

    private void createExtraButton(int cx, int cy, ExtraWandOption option) {
        Button button = Button.builder(getExtraLabel(option), bt -> clickExtraButton(bt, option))
                .pos(getX(cx), getY(cy))
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(getExtraTooltip(option)))
                .build();

        button.active = isExtraApplicable(option);
        addRenderableWidget(button);
    }

    /**
     * The description of an extra setting, with the reason appended when the setting is off because of
     * another one.
     */
    private Component getExtraTooltip(ExtraWandOption option) {
        Component description = Component.translatable(option.getDescTranslation(ExtraWandOption.getDisplayIndex(wand, option)));
        if(option == ExtraWandOption.SILK_TOUCH && ExtraWandOption.isSilkTouchBlocked(wand)) {
            description = description.copy().append(Component.literal("\n"))
                    .append(Component.translatable(ConstructionWand.MODID + ".option.silk_touch.blocked_fortune"));
        }
        return description;
    }

    private void clickExtraButton(Button button, ExtraWandOption option) {
        int next = nextExtraIndex(option);
        if(next < 0 || !ExtraWandOption.setIndex(wand, option, next)) return;

        ConstructionWand.instance.HANDLER.sendToServer(new PacketExtraOption(option, next));
        button.setMessage(getExtraLabel(option));
        button.setTooltip(Tooltip.create(getExtraTooltip(option)));
        rebuildWidgets();
    }

    /**
     * @return the next value this wand can actually use; values that need a core the wand does not
     * carry (sending the drops to the material core without one) are skipped
     */
    private int nextExtraIndex(ExtraWandOption option) {
        int count = option.getValueCount();
        int current = ExtraWandOption.getDisplayIndex(wand, option);
        for(int step = 1; step <= count; step++) {
            int candidate = Math.floorMod(current + step, count);
            if(option.isValueAllowed(wand, candidate)) return candidate;
        }
        return -1;
    }

    private boolean isExtraApplicable(ExtraWandOption option) {
        Item core = wandOptions.cores.get() instanceof Item ? (Item) wandOptions.cores.get() : null;
        boolean digging = core == thetadev.constructionwand.plus.items.PlusItems.CORE_DIGGING.get();
        boolean slay = core == thetadev.constructionwand.plus.items.PlusItems.CORE_SLAY.get();
        boolean destruction = core == thetadev.constructionwand.items.ModItems.CORE_DESTRUCTION.get();

        return switch(option) {
            // Silk touch and the fortune component cannot be on at once, see ExtraWandOption.
            case SILK_TOUCH -> digging && !PassiveUpgrades.hasFortune(wand);
            case DROPS_DESTINATION -> digging || slay;
            case FLUID_REMOVAL -> digging || destruction;
            case FLUID_STORAGE -> digging;
        };
    }

    private Component getExtraLabel(ExtraWandOption option) {
        int index = ExtraWandOption.getDisplayIndex(wand, option);
        return Component.translatable(option.getKeyTranslation())
                .append(Component.translatable(option.getValueTranslation(index)));
    }

    // Right panel: upgrades and bound containers

    private void createUpgradeButtons() {
        List<Item> upgrades = PassiveUpgrades.installed(wand);
        for(int index = 0; index < upgrades.size(); index++) {
            Item upgrade = upgrades.get(index);
            int x = getPanelX() + 2;
            int y = getUpgradeRowY(index);
            addRenderableWidget(new ItemButton(x, y, new ItemStack(upgrade), Component.translatable(upgrade.getDescriptionId()),
                    () -> clickUpgrade(upgrade)));
        }
    }

    private void clickUpgrade(Item upgrade) {
        if(Screen.hasShiftDown()) {
            ConstructionWand.instance.HANDLER.sendToServer(new PacketRemoveUpgrade(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(upgrade).toString()));
            wandOptions.upgrades.removeUpgrade((thetadev.constructionwand.api.IWandUpgrade) upgrade);
        }
        else {
            boolean disabled = !PassiveUpgrades.isDisabled(wand, upgrade);
            ConstructionWand.instance.HANDLER.sendToServer(new PacketToggleUpgrade(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(upgrade).toString(), disabled));
            PassiveUpgrades.setDisabled(wand, upgrade, disabled);
        }
        rebuildWidgets();
    }

    private void createBindingButtons() {
        List<ContainerBinding.Bound> bounds = boundContainers();
        int offset = scrollOffset();
        int visible = bindingVisibleRows();
        for(int index = offset; index < bounds.size() && index < offset + visible; index++) {
            ContainerBinding.Bound bound = bounds.get(index);
            int rowY = getBindingRowY(index - offset);
            int controlsX = bindingControlsX();

            Button up = rowButton("\u2191", Component.translatable(ConstructionWand.MODID + ".gui.binding.priority_up"),
                    controlsX, rowY, index > 0, () -> sendBinding(PacketBoundContainer.ACTION_UP, bound));
            Button down = rowButton("\u2193", Component.translatable(ConstructionWand.MODID + ".gui.binding.priority_down"),
                    controlsX + ROW_BUTTON_SIZE + ROW_BUTTON_GAP, rowY, index < bounds.size() - 1,
                    () -> sendBinding(PacketBoundContainer.ACTION_DOWN, bound));
            Button remove = rowButton("\u2715", Component.translatable(ConstructionWand.MODID + ".gui.binding.remove"),
                    controlsX + 2 * (ROW_BUTTON_SIZE + ROW_BUTTON_GAP), rowY, true,
                    () -> sendBinding(PacketBoundContainer.ACTION_REMOVE, bound));

            addRenderableWidget(up);
            addRenderableWidget(down);
            addRenderableWidget(remove);
        }
    }

    private void sendBinding(int action, ContainerBinding.Bound bound) {
        ConstructionWand.instance.HANDLER.sendToServer(new PacketBoundContainer(action, bound.pos()));
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if(level == null) return;

        BlockPos pos = bound.pos();
        switch(action) {
            case PacketBoundContainer.ACTION_UP -> ContainerBinding.moveBinding(wand, pos, level, -1);
            case PacketBoundContainer.ACTION_DOWN -> ContainerBinding.moveBinding(wand, pos, level, 1);
            case PacketBoundContainer.ACTION_REMOVE -> ContainerBinding.removeBinding(wand, pos, level);
            default -> {
            }
        }
        rebuildWidgets();
    }

    /**
     * The containers the panel lists and offers controls for. Only the storage core reads them, so a
     * wand with any other material core shows its own binding instead and leaves this list alone.
     */
    private List<ContainerBinding.Bound> boundContainers() {
        if(!(wandOptions.materialCores.get() instanceof ItemCoreStorage)) return List.of();
        return ContainerBinding.read(wand);
    }

    /**
     * How many rows the panel lists for the selected material core: one per bound container, one for
     * the ME binding or the EMC, none without a material core.
     */
    private int bindingRowCount() {
        Object materialCore = wandOptions.materialCores.get();
        if(materialCore instanceof thetadev.constructionwand.plus.compat.ae2.ItemCoreAE
                || materialCore instanceof thetadev.constructionwand.plus.compat.projecte.ItemCoreProjectE) return 1;
        if(materialCore instanceof ItemCoreStorage) return Math.max(1, boundContainers().size());
        return 0;
    }

    /**
     * How many binding rows fit between the header and the bottom of the panel.
     */
    private int bindingVisibleRows() {
        return Math.max(1, (getOriginY() + FIELD_HEIGHT - getBindingRowY(0)) / ROW_HEIGHT);
    }

    /**
     * The first binding row shown, clamped so the list cannot be scrolled past its ends.
     */
    private int scrollOffset() {
        return Mth.clamp(bindingScroll, 0, Math.max(0, bindingRowCount() - bindingVisibleRows()));
    }

    /**
     * The left edge of the controls of a binding row.
     */
    private int bindingControlsX() {
        return getPanelX() + PANEL_WIDTH - 3 * ROW_BUTTON_SIZE - 2 * ROW_BUTTON_GAP;
    }

    private Button rowButton(String label, Component tooltip, int x, int y, boolean active, Runnable action) {
        Button button = Button.builder(Component.literal(label), bt -> action.run())
                .pos(x, y)
                .size(ROW_BUTTON_SIZE, ROW_BUTTON_SIZE)
                .tooltip(Tooltip.create(tooltip))
                .build();
        button.active = active;
        return button;
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        int panelX = getPanelX();
        int originY = getOriginY();

        guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.upgrades"), panelX, originY + 7, 0xFFFFFF, false);

        List<Item> upgrades = PassiveUpgrades.installed(wand);
        if(upgrades.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.upgrades.none"),
                    panelX + ICON_SIZE + 4, getUpgradeRowY(0) + 6, 0x808080, false);
        }
        else {
            for(int index = 0; index < upgrades.size(); index++) {
                Item upgrade = upgrades.get(index);
                boolean disabled = PassiveUpgrades.isDisabled(wand, upgrade);
                Component name = Component.translatable(
                        ConstructionWand.MODID + ".upgrade." + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(upgrade).getPath() + ".label");
                guiGraphics.drawString(font, name, panelX + ICON_SIZE + 4, getUpgradeRowY(index) + 6,
                        disabled ? 0x808080 : 0x55FF55, false);
            }
        }

        renderBindings(guiGraphics, panelX);
    }

    /**
     * The binding the selected material core reads, so the panel follows the core:
     * <ul>
     * <li>storage core: one row per bound container, numbered by the order they are used in;</li>
     * <li>AE core: the ME controller the wand is bound to;</li>
     * <li>ProjectE core: the EMC of the player, which is what that core draws from;</li>
     * <li>no material core: nothing, the panel is left to the upgrades.</li>
     * </ul>
     * A wand without a material core has no binding to report, and the container list of the storage
     * core is not shown for the other cores because they do not read it.
     */
    private void renderBindings(GuiGraphics guiGraphics, int panelX) {
        int rows = bindingRowCount();
        if(rows == 0) return;

        renderBindingHeader(guiGraphics, panelX, rows);

        Object materialCore = wandOptions.materialCores.get();
        if(materialCore instanceof thetadev.constructionwand.plus.compat.ae2.ItemCoreAE) {
            renderAeBinding(guiGraphics, panelX + 4, getBindingRowY(0) + 3);
            return;
        }
        if(materialCore instanceof thetadev.constructionwand.plus.compat.projecte.ItemCoreProjectE) {
            renderProjecteBinding(guiGraphics, panelX + 4, getBindingRowY(0) + 3);
            return;
        }

        List<ContainerBinding.Bound> bounds = boundContainers();
        if(bounds.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.binding.unbound"),
                    panelX + 4, getBindingRowY(0) + 3, 0x808080, false);
            return;
        }
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        int offset = scrollOffset();
        for(int index = offset; index < bounds.size() && index < offset + bindingVisibleRows(); index++) {
            ContainerBinding.Bound bound = bounds.get(index);
            guiGraphics.drawString(font, bindingLabel(index, bound, level), panelX + 4, getBindingRowY(index - offset) + 3,
                    index == bindingDragFrom ? 0xFFFFFF : 0x55FF55, false);
        }
        renderDragMarker(guiGraphics, panelX);
    }

    /**
     * The header of the binding panel, with the part of the list that is on screen when it had to be
     * cut off, so a scrolled list is not mistaken for a short one.
     */
    private void renderBindingHeader(GuiGraphics guiGraphics, int panelX, int rows) {
        int headerY = getBindingHeaderY();
        guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.bindings"), panelX, headerY, 0xFFFFFF, false);

        int visible = bindingVisibleRows();
        if(rows <= visible) return;

        int offset = scrollOffset();
        String counter = (offset + 1) + "-" + Math.min(rows, offset + visible) + "/" + rows;
        guiGraphics.drawString(font, counter, panelX + PANEL_WIDTH - font.width(counter), headerY, 0x808080, false);
    }

    /**
     * Marks the gap the dragged row would be dropped into.
     */
    private void renderDragMarker(GuiGraphics guiGraphics, int panelX) {
        if(bindingDragFrom < 0 || bindingDragTo < 0) return;

        int row = bindingDragTo - scrollOffset();
        if(row < 0 || row >= bindingVisibleRows()) return;

        int y = getBindingRowY(row);
        guiGraphics.fill(panelX + 2, y - 3, panelX + PANEL_WIDTH - 2, y - 2, 0xFFFFFFFF);
    }

    /**
     * The dimension a binding lives in, without the namespace the game's own dimensions all share, so a
     * row reads "the_nether" instead of "minecraft:the_nether" and the coordinates keep their room. A
     * binding that was made in another dimension is otherwise indistinguishable from a local one, and
     * only one of them keeps working after a trip.
     */
    private String dimensionLabel(ContainerBinding.Bound bound) {
        String dimension = bound.dimension().location().toString();
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }

    /**
     * The block name together with the coordinates of a bound container, with the name trimmed so the
     * row still fits the panel. The name is dropped when there is none recorded, so the coordinates
     * are never repeated.
     */
    private String bindingLabel(int index, ContainerBinding.Bound bound, net.minecraft.world.level.Level level) {
        String coords = "(" + dimensionLabel(bound) + " " + bound.pos().toShortString() + ")";
        String prefix = (index + 1) + ". ";
        int available = PANEL_WIDTH - 8 - font.width(prefix) - font.width(coords);

        String name = available <= 0 ? "" : font.plainSubstrByWidth(ContainerBinding.displayName(level, bound), available);
        if(name.equals(bound.pos().toShortString())) name = "";
        return name.isEmpty() ? prefix + coords : prefix + name + " " + coords;
    }

    /**
     * The controller of the bound ME network, named by its position so a stale binding is visible.
     */
    private void renderAeBinding(GuiGraphics guiGraphics, int x, int y) {
        thetadev.constructionwand.plus.compat.ae2.AE2Compat.Bound binding =
                thetadev.constructionwand.plus.compat.ae2.AE2Compat.readBinding(wand);
        Component value = binding == null
                ? Component.translatable(ConstructionWand.MODID + ".gui.binding.unbound")
                : Component.literal("(" + binding.pos().toShortString() + ")");

        guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.binding.ae").append(": ").append(value),
                x, y, binding == null ? 0x808080 : 0x55FF55, false);
    }

    /**
     * The EMC of the player, which the ProjectE core builds from instead of a bound block.
     */
    private void renderProjecteBinding(GuiGraphics guiGraphics, int x, int y) {
        long emc = thetadev.constructionwand.plus.compat.projecte.ProjectECompat.emc(Minecraft.getInstance().player);
        Component value = emc < 0
                ? Component.translatable(ConstructionWand.MODID + ".gui.binding.unavailable")
                : Component.literal(Long.toString(emc));

        guiGraphics.drawString(font, Component.translatable(ConstructionWand.MODID + ".gui.binding.emc", value),
                x, y, emc < 0 ? 0x808080 : 0x55FF55, false);
    }

    private int getOriginX() {
        return (width - (FIELD_WIDTH + PANEL_GAP + PANEL_WIDTH)) / 2;
    }

    private int getOriginY() {
        return (height - FIELD_HEIGHT) / 2;
    }

    private int getPanelX() {
        return getOriginX() + FIELD_WIDTH + PANEL_GAP;
    }

    private int getUpgradeRowY(int index) {
        return getOriginY() + ROW_HEIGHT * (index + 1) + 4;
    }

    private int getBindingHeaderY() {
        int rows = Math.max(1, PassiveUpgrades.installed(wand).size());
        return getOriginY() + ROW_HEIGHT * (rows + 1) + 18;
    }

    private int getBindingRowY(int index) {
        return getBindingHeaderY() + ROW_HEIGHT * (index + 1);
    }

    private int getX(int n) {
        return getOriginX() + n * (BUTTON_WIDTH + SPACING_WIDTH);
    }

    private int getY(int n) {
        return getOriginY() + n * (BUTTON_HEIGHT + SPACING_HEIGHT);
    }

    private Component getButtonLabel(IOption<?> option) {
        return Component.translatable(option.getKeyTranslation()).append(Component.translatable(option.getValueTranslation()));
    }

    private Tooltip getButtonTooltip(IOption<?> option) {
        Component description = Component.translatable(option.getDescTranslation());
        if(option == wandOptions.cores || option == wandOptions.materialCores) {
            description = description.copy().append(Component.literal("\n"))
                    .append(Component.translatable(ConstructionWand.MODID + ".gui.core_removal_hint"));
        }
        return Tooltip.create(description);
    }

    /**
     * Slider for the per-wand block limit. The value is only written when the mouse is released, so
     * dragging does not spam the server.
     */
    private class LimitSlider extends AbstractSliderButton
    {
        private final int configuredMax;

        LimitSlider(int x, int y, int width, int height, int current, int configuredMax) {
            super(x, y, width, height, Component.empty(), configuredMax <= 1 ? 1.0D : (double) (current - 1) / (double) (configuredMax - 1));
            this.configuredMax = configuredMax;
            updateMessage();
        }

        private int limit() {
            return Mth.clamp((int) Math.round(1 + value * (configuredMax - 1)), 1, configuredMax);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(WandLimit.getKeyTranslation())
                    .append(Component.translatable(ConstructionWand.MODID + ".option.block_limit.value", limit())));
        }

        @Override
        protected void applyValue() {
            // Applied on release, see onRelease.
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            int limit = limit();
            WandLimit.set(wand, limit);
            ConstructionWand.instance.HANDLER.sendToServer(new PacketWandLimit(limit));
        }
    }

    /**
     * A button that shows an item icon, used for the installed upgrades.
     */
    private static class ItemButton extends Button
    {
        private final ItemStack icon;
        private final Runnable action;

        ItemButton(int x, int y, ItemStack icon, Component tooltip, Runnable action) {
            super(x, y, ICON_SIZE, ICON_SIZE, Component.empty(), bt -> {
            }, Button.DEFAULT_NARRATION);
            this.icon = icon;
            this.action = action;
            setTooltip(Tooltip.create(tooltip.copy().append(Component.literal("\n"))
                    .append(Component.translatable(ConstructionWand.MODID + ".upgrade.toggle_hint"))));
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.renderItem(icon, getX() + 2, getY() + 2);
            if(isHovered) {
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x33FFFFFF);
            }
        }
    }
}
