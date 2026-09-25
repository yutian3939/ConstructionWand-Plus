package com.xinyihl.constructionwandlegacy.client;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.basics.WandTarget;
import com.xinyihl.constructionwandlegacy.basics.option.WandDataCodec;
import com.xinyihl.constructionwandlegacy.basics.option.WandOption;
import com.xinyihl.constructionwandlegacy.basics.option.WandState;
import com.xinyihl.constructionwandlegacy.compat.CompatRegistrar;
import com.xinyihl.constructionwandlegacy.compat.ae2.AE2Compat;
import com.xinyihl.constructionwandlegacy.config.ConfigRuntime;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreAE;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreDestruction;
import com.xinyihl.constructionwandlegacy.items.core.ItemCoreProjectE;
import com.xinyihl.constructionwandlegacy.items.wand.ItemWand;
import com.xinyihl.constructionwandlegacy.material.source.BoundContainerSourceFactory;
import com.xinyihl.constructionwandlegacy.network.ModMessages;
import com.xinyihl.constructionwandlegacy.network.PacketBoundContainer;
import com.xinyihl.constructionwandlegacy.network.PacketExtraOption;
import com.xinyihl.constructionwandlegacy.network.PacketRemoveCore;
import com.xinyihl.constructionwandlegacy.network.PacketRemoveUpgrade;
import com.xinyihl.constructionwandlegacy.network.PacketWandLimit;
import com.xinyihl.constructionwandlegacy.network.PacketWandOption;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandCore;
import com.xinyihl.constructionwandlegacy.wand.upgrade.IWandUpgrade;
import com.xinyihl.morewandcore.basics.ExtraWandOption;
import com.xinyihl.morewandcore.basics.WandLimit;
import com.xinyihl.morewandcore.basics.WandUpgrades;
import com.xinyihl.morewandcore.item.ItemCoreDigging;
import com.xinyihl.morewandcore.item.ItemCoreStorage;
import com.xinyihl.morewandcore.item.ModItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlider;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wand configuration screen.
 * <p>
 * The settings sit in a two column grid on the left, one button per setting, and the right hand side
 * lists what is installed on the wand: the upgrade components with their icons and the binding the
 * selected material core works with.
 */
public class GuiWand extends GuiScreen {
    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING_WIDTH = 20;
    private static final int SPACING_HEIGHT = 26;

    private static final int OPTION_COLS = 2;
    private static final int OPTION_ROWS = 6;

    private static final int FIELD_WIDTH = OPTION_COLS * (BUTTON_WIDTH + SPACING_WIDTH) - SPACING_WIDTH;
    private static final int FIELD_HEIGHT = OPTION_ROWS * (BUTTON_HEIGHT + SPACING_HEIGHT) - SPACING_HEIGHT;

    /**
     * Right hand panel: one column of icons next to one column of names, used for the upgrades and
     * for the binding. Its rows are tighter than the button grid, the entries are small.
     */
    private static final int PANEL_GAP = 24;
    private static final int PANEL_ROW_HEIGHT = 22;
    private static final int PANEL_SECTION_GAP = 8;
    private static final int ICON_COLUMN = 22;
    private static final int ICON_SIZE = 18;
    private static final int NAME_COLUMN = 170;
    private static final int PANEL_WIDTH = ICON_COLUMN + NAME_COLUMN;

    private static final int TOTAL_WIDTH = FIELD_WIDTH + PANEL_GAP + PANEL_WIDTH;

    /**
     * The controls at the end of a bound container row: move it up or down in the priority order,
     * and unbind it.
     */
    private static final int ROW_BUTTON_SIZE = 14;
    private static final int ROW_BUTTON_GAP = 2;
    private static final int ROW_BUTTON_COUNT = 3;
    private static final int ROW_CONTROLS_WIDTH = ROW_BUTTON_COUNT * ROW_BUTTON_SIZE + (ROW_BUTTON_COUNT - 1) * ROW_BUTTON_GAP;

    /**
     * Height of one line of text in the vanilla font.
     */
    private static final int TEXT_HEIGHT = 8;

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BOUND_COLOR = 0x55FF55;
    private static final int UNBOUND_COLOR = 0x808080;

    private final WandTarget target;
    private final ItemStack wand;
    private WandState state;

    public GuiWand(WandTarget target, ItemStack wand) {
        this.target = target;
        this.wand = wand.copy();
        this.state = WandDataCodec.read(wand);
    }

    private static String key(String path) {
        return Tags.MOD_ID + "." + path;
    }

    /**
     * The upgrade components this screen can list. Read on every use, the items only exist once the
     * registry events ran.
     */
    private static Item[] allUpgrades() {
        return new Item[]{ModItems.ITEM_UPGRADE_FORTUNE, ModItems.ITEM_UPGRADE_AUTO_SMELT};
    }

    private String getButtonLabel(WandOption option) {
        return I18n.format(option.getKeyTranslation()) + I18n.format(option.getValueTranslation(state));
    }

    private String getButtonLabel(ExtraWandOption option) {
        return I18n.format(option.getKeyTranslation()) + I18n.format(option.getValueTranslation(ExtraWandOption.getIndex(wand, option)));
    }

    @Override
    public void initGui() {
        buttonList.clear();
        // The left grid, in reading order: the behaviour core decides what the wand does, the material
        // core where its items come from.
        createButton(0, 0, WandOption.CORES);
        createButton(1, 0, WandOption.MATERIAL_CORES);
        createButton(0, 1, WandOption.LOCK);
        createButton(1, 1, WandOption.DIRECTION);
        createButton(0, 2, WandOption.REPLACE);
        createButton(1, 2, WandOption.MATCH);
        createButton(0, 3, ExtraWandOption.SILK_TOUCH);
        createButton(1, 3, ExtraWandOption.DROPS_DESTINATION);
        createButton(0, 4, WandOption.RANDOM);
        createButton(1, 4, ExtraWandOption.FLUID_REMOVAL);
        // Whether the fluid the digging core clears is kept in the material core instead of dropped.
        createButton(1, 5, ExtraWandOption.FLUID_STORAGE);
        // The block limit of this wand, a slider over the range the configuration allows.
        int configuredMax = configuredLimit();
        buttonList.add(new LimitSlider(this, buttonList.size(), optionX(0), optionY(5), configuredMax, WandLimit.resolve(wand, configuredMax)));
        // One icon per installed upgrade, the ones the wand does not carry are not listed.
        List<Item> upgrades = installedUpgrades();
        for (int index = 0; index < upgrades.size(); index++) {
            buttonList.add(new UpgradeButton(buttonList.size(), panelX(), upgradeRowY(index) + iconOffset(), upgrades.get(index)));
        }
        // The controls of the bound containers, in the same rows the panel lists them in.
        List<BoundContainerSourceFactory.Bound> bindings = boundContainers();
        for (int index = 0; index < bindings.size(); index++) {
            createBindButtons(index, bindings.get(index), bindings.size());
        }
    }

    /**
     * The three controls of one bound container. The first row cannot move up and the last one cannot
     * move down, so those buttons are shown disabled.
     */
    private void createBindButtons(int row, BoundContainerSourceFactory.Bound bound, int count) {
        BoundButton up = new BoundButton(buttonList.size(), row, bound, BoundButton.UP);
        up.enabled = row > 0;
        buttonList.add(up);

        BoundButton down = new BoundButton(buttonList.size(), row, bound, BoundButton.DOWN);
        down.enabled = row < count - 1;
        buttonList.add(down);

        buttonList.add(new BoundButton(buttonList.size(), row, bound, BoundButton.REMOVE));
    }

    private List<Item> installedUpgrades() {
        List<Item> installed = new ArrayList<>(2);
        for (Item upgrade : allUpgrades()) {
            if (upgrade != null && WandUpgrades.has(wand, upgrade)) {
                installed.add(upgrade);
            }
        }
        return installed;
    }

    private String getUpgradeName(Item upgrade) {
        ResourceLocation id = upgrade.getRegistryName();
        return I18n.format(key("upgrade." + (id == null ? "unknown" : id.getPath()) + ".label"));
    }

    /**
     * The binding the selected material core reads: a container for the storage core, the ME
     * controller for the AE core. A wand with any other material core has nothing to show here.
     */
    private List<BindingRow> bindingRows() {
        IWandCore material = state.getSelectedMaterialCore();
        if (material instanceof ItemCoreStorage) {
            return containerBindingRows();
        }
        if (material instanceof ItemCoreAE) {
            return Collections.singletonList(meBindingRow());
        }
        if (material instanceof ItemCoreProjectE) {
            return Collections.singletonList(projecteBindingRow());
        }
        return Collections.emptyList();
    }

    /**
     * One row per bound container, in the order they are used and numbered to show the priority. The
     * controls at the end of every row change that order, so a wand without any binding shows the
     * plain hint instead.
     */
    private List<BindingRow> containerBindingRows() {
        List<BoundContainerSourceFactory.Bound> bindings = boundContainers();
        if (bindings.isEmpty()) {
            return Collections.singletonList(BindingRow.unbound(ModItems.ITEM_CORE_STORAGE, key("gui.binding.container")));
        }
        List<BindingRow> rows = new ArrayList<>(bindings.size());
        for (int index = 0; index < bindings.size(); index++) {
            BoundContainerSourceFactory.Bound bound = bindings.get(index);
            String text = (index + 1) + ". " + describeBoundBlock(bound.getPos(), bound.getDimension(), bound.getBlockKey());
            rows.add(new BindingRow(ModItems.ITEM_CORE_STORAGE, text, true, true));
        }
        return rows;
    }

    /**
     * The containers the wand is bound to, the first one first. Only the storage core reads them, so
     * every other material core leaves the list alone.
     */
    private List<BoundContainerSourceFactory.Bound> boundContainers() {
        return state.getSelectedMaterialCore() instanceof ItemCoreStorage
                ? BoundContainerSourceFactory.readBindings(wand)
                : Collections.emptyList();
    }

    private BindingRow meBindingRow() {
        Item icon = com.xinyihl.constructionwandlegacy.items.ModItems.CORE_AE;
        AE2Compat.Binding binding = AE2Compat.readBinding(wand);
        if (binding == null) {
            return BindingRow.unbound(icon, key("gui.binding.ae"));
        }
        return new BindingRow(icon, I18n.format(key("gui.binding.ae")) + ": " + describeBoundBlock(binding.getPosition(), binding.getDimension(), binding.getBlockKey()), true);
    }

    /**
     * The ProjectE core has no bound block, it draws from the player, so its row reports the EMC that
     * is available.
     */
    private BindingRow projecteBindingRow() {
        Item icon = com.xinyihl.constructionwandlegacy.items.ModItems.CORE_PROJECTE;
        long emc = CompatRegistrar.getProjectEEmc(Minecraft.getMinecraft().player);
        if (emc < 0L) {
            return BindingRow.unavailable(icon, key("gui.binding.projecte"));
        }
        return new BindingRow(icon, I18n.format(key("gui.binding.projecte")) + ": " + I18n.format(key("gui.binding.emc"), emc), true);
    }

    /**
     * The bound block as {@code <block name> (x, y, z)}, falling back to the coordinates alone when the
     * block cannot be named at all.
     * <p>
     * A loaded block is asked about directly, which is the most accurate answer. Otherwise the
     * translation key recorded when the wand was bound is used, which still names a container in an
     * unloaded chunk or in another dimension.
     */
    private String describeBoundBlock(BlockPos pos, int dimension, @Nullable String storedKey) {
        String position = describePosition(pos, dimension);
        String name = loadedBlockName(pos, dimension);
        if (name == null) {
            name = storedBlockName(storedKey);
        }
        return name == null ? position : name + " " + position;
    }

    @Nullable
    private String loadedBlockName(BlockPos pos, int dimension) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.world.provider.getDimension() != dimension || !minecraft.world.isBlockLoaded(pos)) {
            return null;
        }
        return blockDisplayName(minecraft.world.getBlockState(pos), pos);
    }

    /**
     * The name behind the key written down at binding time. A key the language files do not know comes
     * back as itself, which the usual check filters out.
     */
    @Nullable
    private String storedBlockName(@Nullable String storedKey) {
        if (storedKey == null || storedKey.isEmpty()) {
            return null;
        }
        String name = I18n.format(storedKey);
        return isTranslated(name) ? name : null;
    }

    /**
     * The name a container is known by.
     * <p>
     * The item of the block is asked first, it knows the metadata, so drawers and other wood variants
     * are named by their variant. Then the tile entity is asked, containers often carry their own
     * name. Every candidate is checked for a missing translation, those come back as their own key -
     * {@code tile.mod.machine.name} - which is of no use to the player.
     */
    @Nullable
    private String blockDisplayName(IBlockState state, BlockPos pos) {
        try {
            Item item = Item.getItemFromBlock(state.getBlock());
            if (item != null && item != Items.AIR) {
                String itemName = new ItemStack(item, 1, state.getBlock().getMetaFromState(state)).getDisplayName();
                if (isTranslated(itemName)) {
                    return itemName;
                }
            }
            TileEntity tile = mc.world.getTileEntity(pos);
            if (tile != null && tile.getDisplayName() != null) {
                String tileName = tile.getDisplayName().getUnformattedText();
                if (isTranslated(tileName)) {
                    return tileName;
                }
            }
            String blockName = state.getBlock().getLocalizedName();
            return isTranslated(blockName) ? blockName : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Whether a name was really translated: a missing entry is returned as its own key.
     */
    private static boolean isTranslated(@Nullable String name) {
        return name != null && !name.isEmpty() && !name.startsWith("tile.") && !name.startsWith("item.");
    }

    private String describePosition(BlockPos pos, int dimension) {
        String coordinates = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.world.provider.getDimension() == dimension) {
            return coordinates;
        }
        return I18n.format(key("gui.binding.dimension"), dimension) + " " + coordinates;
    }

    /**
     * Takes an upgrade off the wand and returns the component, the same way the cores are taken off.
     * Installing works like it does for the cores as well: by crafting the component with the wand.
     */
    private void removeUpgrade(Item upgrade) {
        if (!WandDataCodec.removeUpgrade(wand, (IWandUpgrade) upgrade)) {
            return;
        }
        state = WandDataCodec.read(wand);
        initGui();
        ModMessages.sendToServer(new PacketRemoveUpgrade(target, upgrade.getRegistryName()));
    }

    private int configuredLimit() {
        ItemWand item = wand.getItem() instanceof ItemWand ? (ItemWand) wand.getItem() : null;
        return item == null ? 1 : Math.max(1, ConfigRuntime.getSnapshot().getPlacementLimit(item.getTier()));
    }

    /**
     * Stores the value a slider was dragged to; it is sent on release so dragging does not flood the
     * connection.
     */
    private void applySliderLimit(LimitSlider slider) {
        int limit = slider.getBlockLimit();
        if (!WandLimit.set(wand, limit)) {
            return;
        }
        slider.setSliderValue(limit, false);
        ModMessages.sendToServer(new PacketWandLimit(target, limit));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, wand.getDisplayName(), originX() + TOTAL_WIDTH / 2, originY() - 20, TEXT_COLOR);
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawListPanel();
        drawTooltip(mouseX, mouseY);
    }

    /**
     * The right hand side: the upgrade components and the binding of the selected material core, both
     * as an icon next to a name.
     */
    private void drawListPanel() {
        drawString(fontRenderer, I18n.format(key("gui.upgrades")), panelX(), upgradeHeaderY() + textOffset(), TEXT_COLOR);
        List<Item> upgrades = installedUpgrades();
        if (upgrades.isEmpty()) {
            drawString(fontRenderer, I18n.format(key("gui.upgrades.none")), nameX(), upgradeRowY(0) + textOffset(), UNBOUND_COLOR);
        } else {
            for (int index = 0; index < upgrades.size(); index++) {
                drawString(fontRenderer, getUpgradeName(upgrades.get(index)), nameX(), upgradeRowY(index) + textOffset(), TEXT_COLOR);
            }
        }

        List<BindingRow> bindings = bindingRows();
        if (bindings.isEmpty()) {
            return;
        }
        drawString(fontRenderer, I18n.format(key("gui.bindings")), panelX(), bindingHeaderY() + textOffset(), TEXT_COLOR);
        for (int index = 0; index < bindings.size(); index++) {
            drawBindingRow(bindings.get(index), bindingRowY(index));
        }
    }

    private void drawBindingRow(BindingRow row, int y) {
        if (row.getIcon() != null) {
            drawItemIcon(row.getIcon(), panelX() + 1, y + iconOffset());
        }
        // Rows with controls keep the room those need, the text is trimmed in front of them.
        int width = row.hasControls() ? NAME_COLUMN - ROW_CONTROLS_WIDTH - 4 : NAME_COLUMN;
        String text = fontRenderer.trimStringToWidth(row.getText(), width);
        drawString(fontRenderer, text, nameX(), y + textOffset(), row.isActive() ? BOUND_COLOR : UNBOUND_COLOR);
    }

    private void drawItemIcon(Item item, int x, int y) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item), x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTooltip(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (!button.isMouseOver()) {
                continue;
            }
            if (button instanceof OptionButton) {
                OptionButton optionButton = (OptionButton) button;
                List<String> tooltip = new ArrayList<>(2);
                tooltip.add(I18n.format(optionButton.option.getDescriptionTranslation(state)));
                if (isCoreOption(optionButton.option)) {
                    tooltip.add(I18n.format(Tags.MOD_ID + ".gui.core_removal_hint"));
                }
                drawHoveringText(tooltip, mouseX, mouseY);
                return;
            }
            if (button instanceof ExtraOptionButton) {
                ExtraOptionButton optionButton = (ExtraOptionButton) button;
                int value = ExtraWandOption.getIndex(wand, optionButton.option);
                drawHoveringText(Collections.singletonList(I18n.format(optionButton.option.getDescriptionTranslation(value))), mouseX, mouseY);
                return;
            }
            if (button instanceof LimitSlider) {
                int configuredMax = configuredLimit();
                drawHoveringText(Collections.singletonList(I18n.format(WandLimit.getDescriptionTranslation(), WandLimit.resolve(wand, configuredMax), configuredMax)), mouseX, mouseY);
                return;
            }
            if (button instanceof UpgradeButton) {
                Item upgrade = ((UpgradeButton) button).upgrade;
                ResourceLocation id = upgrade.getRegistryName();
                List<String> tooltip = new ArrayList<>(2);
                tooltip.add(I18n.format(Tags.MOD_ID + ".upgrade." + (id == null ? "unknown" : id.getPath()) + ".desc"));
                tooltip.add(I18n.format(Tags.MOD_ID + ".upgrade.toggle_hint"));
                drawHoveringText(tooltip, mouseX, mouseY);
                return;
            }
            if (button instanceof BoundButton) {
                BoundButton boundButton = (BoundButton) button;
                if (boundButton.enabled) {
                    drawHoveringText(Collections.singletonList(I18n.format(boundButton.getTooltipKey())), mouseX, mouseY);
                }
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof OptionButton) {
            OptionButton optionButton = (OptionButton) button;
            if (isShiftKeyDown() && removeSelectedCore(optionButton.option)) {
                return;
            }
            if (!WandDataCodec.cycle(wand, optionButton.option, true)) {
                return;
            }
            state = WandDataCodec.read(wand);
            // The whole screen is rebuilt: which extra settings are editable follows the installed
            // behaviour cores, and the binding section with its controls follows the material core.
            initGui();
            ModMessages.sendToServer(new PacketWandOption(optionButton.option, target, WandDataCodec.getNetworkValue(state, optionButton.option), false));
            return;
        }

        if (button instanceof ExtraOptionButton) {
            ExtraOptionButton optionButton = (ExtraOptionButton) button;
            int next = ExtraWandOption.cycleIndex(wand, optionButton.option, true);
            if (next < 0) {
                return;
            }
            optionButton.displayString = getButtonLabel(optionButton.option);
            ModMessages.sendToServer(new PacketExtraOption(optionButton.option, target, next));
            return;
        }

        if (button instanceof UpgradeButton) {
            // Like the core buttons: only the modifier takes the component off.
            if (isShiftKeyDown()) {
                removeUpgrade(((UpgradeButton) button).upgrade);
            }
            return;
        }

        if (button instanceof BoundButton) {
            applyBoundAction((BoundButton) button);
        }
    }

    /**
     * Reorders or unbinds one container. Like the other settings the screen edits its own copy of the
     * wand right away, so the list follows the click, and the server applies the same change to the
     * real stack.
     */
    private void applyBoundAction(BoundButton button) {
        BlockPos pos = button.bound.getPos();
        int dimension = button.bound.getDimension();
        boolean changed;
        PacketBoundContainer packet;
        if (button.action == BoundButton.REMOVE) {
            changed = BoundContainerSourceFactory.removeBinding(wand, pos, dimension);
            packet = PacketBoundContainer.remove(target, pos, dimension);
        } else if (button.action == BoundButton.UP) {
            changed = BoundContainerSourceFactory.moveBinding(wand, pos, dimension, -1);
            packet = PacketBoundContainer.moveUp(target, pos, dimension);
        } else {
            changed = BoundContainerSourceFactory.moveBinding(wand, pos, dimension, 1);
            packet = PacketBoundContainer.moveDown(target, pos, dimension);
        }
        if (!changed) {
            return;
        }
        initGui();
        ModMessages.sendToServer(packet);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (Minecraft.getMinecraft().gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void createButton(int col, int row, WandOption option) {
        OptionButton button = new OptionButton(buttonList.size(), optionX(col), optionY(row), option);
        button.enabled = state.isEnabled(option);
        buttonList.add(button);
    }

    private void createButton(int col, int row, ExtraWandOption option) {
        ExtraOptionButton button = new ExtraOptionButton(buttonList.size(), optionX(col), optionY(row), option);
        button.enabled = isApplicable(option);
        buttonList.add(button);
    }

    private static boolean isCoreOption(WandOption option) {
        return option == WandOption.CORES || option == WandOption.MATERIAL_CORES;
    }

    /**
     * Shift clicking a core button takes the selected core off the wand and hands the item back.
     *
     * @return whether a core was removed, in which case the option is not cycled
     */
    private boolean removeSelectedCore(WandOption option) {
        IWandCore core;
        if (option == WandOption.CORES) {
            core = state.getSelectedCore();
        } else if (option == WandOption.MATERIAL_CORES) {
            core = state.getSelectedMaterialCore();
        } else {
            return false;
        }

        ResourceLocation coreId = core == null ? null : core.getRegistryName();
        if (coreId == null || !WandDataCodec.removeUpgrade(wand, core)) {
            return false;
        }
        state = WandDataCodec.read(wand);
        initGui();
        ModMessages.sendToServer(new PacketRemoveCore(target, coreId));
        return true;
    }

    /**
     * An extra setting is only editable on a wand that carries a core which reads it.
     */
    private boolean isApplicable(ExtraWandOption option) {
        switch (option) {
            case SILK_TOUCH:
            case DROPS_DESTINATION:
                return hasCore(ItemCoreDigging.class);
            case FLUID_REMOVAL:
                return hasCore(ItemCoreDigging.class) || hasCore(ItemCoreDestruction.class);
            case FLUID_STORAGE:
                return hasCore(ItemCoreDigging.class);
            default:
                return false;
        }
    }

    private boolean hasCore(Class<? extends IWandCore> type) {
        for (IWandCore core : state.getCores()) {
            if (type.isInstance(core)) {
                return true;
            }
        }
        return false;
    }

    private int originX() {
        return (width - TOTAL_WIDTH) / 2;
    }

    private int originY() {
        return (height - FIELD_HEIGHT) / 2;
    }

    private int optionX(int col) {
        return originX() + col * (BUTTON_WIDTH + SPACING_WIDTH);
    }

    private int optionY(int row) {
        return originY() + row * (BUTTON_HEIGHT + SPACING_HEIGHT);
    }

    private int panelX() {
        return originX() + FIELD_WIDTH + PANEL_GAP;
    }

    private int nameX() {
        return panelX() + ICON_COLUMN;
    }

    /**
     * Where the controls of a bound container row sit: up, down and unbind, left to right at the end
     * of the row.
     */
    private int controlX(int action) {
        return nameX() + NAME_COLUMN - ROW_CONTROLS_WIDTH + controlIndex(action) * (ROW_BUTTON_SIZE + ROW_BUTTON_GAP);
    }

    private int controlY(int row) {
        return bindingRowY(row) + (PANEL_ROW_HEIGHT - ROW_BUTTON_SIZE) / 2;
    }

    private static int controlIndex(int action) {
        return action < 0 ? 0 : action > 0 ? 1 : 2;
    }

    private int upgradeHeaderY() {
        return originY();
    }

    private int upgradeRowY(int index) {
        return upgradeHeaderY() + PANEL_ROW_HEIGHT * (index + 1);
    }

    /**
     * The binding section follows the upgrade entries, which are one row each - or the single hint
     * line when the wand carries no upgrade at all.
     */
    private int bindingHeaderY() {
        return upgradeHeaderY() + PANEL_ROW_HEIGHT * (upgradeRowCount() + 1) + PANEL_SECTION_GAP;
    }

    private int upgradeRowCount() {
        return Math.max(1, installedUpgrades().size());
    }

    private int bindingRowY(int index) {
        return bindingHeaderY() + PANEL_ROW_HEIGHT * (index + 1);
    }

    private static int textOffset() {
        return (PANEL_ROW_HEIGHT - TEXT_HEIGHT) / 2;
    }

    private static int iconOffset() {
        return (PANEL_ROW_HEIGHT - ICON_SIZE) / 2;
    }

    /**
     * One line of the binding section: an icon next to the text, green once the binding exists.
     */
    private static final class BindingRow {
        @Nullable
        private final Item icon;
        private final String text;
        private final boolean active;
        private final boolean controls;

        private BindingRow(@Nullable Item icon, String text, boolean active) {
            this(icon, text, active, false);
        }

        private BindingRow(@Nullable Item icon, String text, boolean active, boolean controls) {
            this.icon = icon;
            this.text = text;
            this.active = active;
            this.controls = controls;
        }

        private static BindingRow unbound(@Nullable Item icon, String labelKey) {
            return new BindingRow(icon, I18n.format(labelKey) + ": " + I18n.format(key("gui.binding.unbound")), false);
        }

        private static BindingRow unavailable(@Nullable Item icon, String labelKey) {
            return new BindingRow(icon, I18n.format(labelKey) + ": " + I18n.format(key("gui.binding.unavailable")), false);
        }

        @Nullable
        private Item getIcon() {
            return icon;
        }

        private String getText() {
            return text;
        }

        private boolean isActive() {
            return active;
        }

        /**
         * Whether the row carries the priority controls of a bound container.
         */
        private boolean hasControls() {
            return controls;
        }
    }

    private final class OptionButton extends GuiButton {
        private final WandOption option;

        private OptionButton(int buttonId, int x, int y, WandOption option) {
            super(buttonId, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, getButtonLabel(option));
            this.option = option;
        }
    }

    private final class ExtraOptionButton extends GuiButton {
        private final ExtraWandOption option;

        private ExtraOptionButton(int buttonId, int x, int y, ExtraWandOption option) {
            super(buttonId, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, getButtonLabel(option));
            this.option = option;
        }
    }

    /**
     * One upgrade entry: the icon of the component, taken off with the modifier like the cores are.
     */
    private final class UpgradeButton extends GuiButton {
        private final Item upgrade;

        private UpgradeButton(int buttonId, int x, int y, Item upgrade) {
            super(buttonId, x, y, ICON_SIZE, ICON_SIZE, "");
            this.upgrade = upgrade;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, this.hovered && this.enabled ? 0x66FFFFFF : 0x33000000);
            drawItemIcon(this.upgrade, this.x + 1, this.y + 1);
        }
    }

    /**
     * One control of a bound container row. The arrows change where the container sits in the priority
     * order, the cross unbinds it.
     */
    private final class BoundButton extends GuiButton {
        private static final int UP = -1;
        private static final int DOWN = 1;
        private static final int REMOVE = 0;

        private final BoundContainerSourceFactory.Bound bound;
        private final int action;
        private final String tooltipKey;

        private BoundButton(int buttonId, int row, BoundContainerSourceFactory.Bound bound, int action) {
            super(buttonId, controlX(action), controlY(row), ROW_BUTTON_SIZE, ROW_BUTTON_SIZE, "");
            this.bound = bound;
            this.action = action;
            this.tooltipKey = key(action < 0 ? "gui.binding.priority_up" : action > 0 ? "gui.binding.priority_down" : "gui.binding.remove");
            this.displayString = action < 0 ? "↑" : action > 0 ? "↓" : "✕";
        }

        private String getTooltipKey() {
            return tooltipKey;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int background = !this.enabled ? 0x22000000 : this.hovered ? 0x66FFFFFF : 0x55000000;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, background);
            drawCenteredString(fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - TEXT_HEIGHT) / 2, this.enabled ? TEXT_COLOR : UNBOUND_COLOR);
        }
    }

    /**
     * Vanilla slider over the range the wand may use: one block on the low end, the configured
     * maximum of its tier on the high end.
     */
    private static final class LimitSlider extends GuiSlider {
        private final GuiWand owner;
        private final int configuredMax;

        private LimitSlider(GuiWand owner, int buttonId, int x, int y, int configuredMax, int current) {
            super(new SliderResponder(), buttonId, x, y, WandLimit.getKeyTranslation(), 1.0F, Math.max(2, configuredMax), Math.min(current, configuredMax),
                    (id, name, value) -> name + I18n.format(WandLimit.getValueTranslation(), Math.round(value)));
            this.owner = owner;
            this.configuredMax = configuredMax;
            // The vanilla slider is fixed at 150 pixels, the button column is narrower.
            this.width = BUTTON_WIDTH;
        }

        private int getBlockLimit() {
            return Math.max(1, Math.min(configuredMax, Math.round(getSliderValue())));
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            super.mouseReleased(mouseX, mouseY);
            owner.applySliderLimit(this);
        }
    }

    /**
     * The slider keeps its own label up to date while dragging, so nothing has to react to the
     * intermediate values; the wand is written when the drag ends.
     */
    private static final class SliderResponder implements GuiPageButtonList.GuiResponder {
        @Override
        public void setEntryValue(int id, boolean value) {
        }

        @Override
        public void setEntryValue(int id, float value) {
        }

        @Override
        public void setEntryValue(int id, String value) {
        }
    }
}
