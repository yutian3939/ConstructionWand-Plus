package dev.polaris_light.constructionwand.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * One line of the wand screen.
 * <p>
 * The screen builds a list of these for the tab it is showing, lays them out and hands them the mouse.
 * A row therefore knows nothing about the wand: it is given its label, its value and what to run when it
 * is clicked, which is what lets a core with settings of its own simply add rows instead of needing a
 * place in a fixed grid.
 */
public abstract class WandRow
{
    public static final int ROW_HEIGHT = 22;
    public static final int TALL_HEIGHT = 34;
    /** Width of the control column on the right, shared by every row so the labels line up. */
    public static final int CONTROL_WIDTH = 132;

    public int x;
    public int y;
    public int width;

    private boolean hovered;

    public int height() {
        return ROW_HEIGHT;
    }

    /**
     * A row that cannot be used is dimmed and swallows clicks; the reason belongs in its tooltip.
     */
    public boolean enabled() {
        return true;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        hovered = contains(mouseX, mouseY) && enabled();
        if(background()) Ui.rowBackground(graphics, x, y, width, height(), hovered, striped());
        renderRow(graphics, font, enabled() ? Ui.TEXT : Ui.TEXT_OFF);
    }

    /**
     * @return whether the row paints the plate behind its content. A row that is a block of the page
     * paints its own parts instead, so the whole block does not sit in one big plate.
     */
    protected boolean background() {
        return true;
    }

    /**
     * @return whether the row took the wheel, which the screen asks before it scrolls the page
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    protected abstract void renderRow(GuiGraphics graphics, Font font, int labelColor);

    /**
     * @return whether the row is drawn with the faint stripe, which the screen turns on for every other
     * row so a list of read only lines stays readable
     */
    protected boolean striped() {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Nullable
    public Component tooltip() {
        return null;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    public boolean isHovered() {
        return hovered;
    }

    // Layout helpers for the subclasses

    /**
     * Where the text of a row starts. The icon column is always kept, whether or not the row has an icon:
     * a row without one must not move its text, or the names and the descriptions of two rows of the same
     * list no longer stand above each other.
     */
    protected int labelX(@Nullable ItemStack icon) {
        return x + 32;
    }

    protected int controlX() {
        return x + width - 8 - CONTROL_WIDTH;
    }

    protected int right() {
        return x + width - 8;
    }

    protected int labelY(int height) {
        return y + (height - 8) / 2;
    }

    protected void renderIcon(GuiGraphics graphics, @Nullable ItemStack icon, int height) {
        if(icon == null || icon.isEmpty()) return;
        graphics.renderItem(icon, x + 8, y + (height - 16) / 2);
    }

    /**
     * Cuts a text to the width it has, with a mark at the end when something was left out: a hard cut
     * reads like a rendering fault, an ellipsis reads like a shortened text.
     */
    protected static String trim(Font font, Component text, int available) {
        if(available <= 0) return "";
        String value = text.getString();
        if(font.width(value) <= available) return value;
        return font.plainSubstrByWidth(value, Math.max(0, available - 6)) + "\u2026";
    }

    /**
     * A block of the page split in two, with a divider between them: the entries to pick from on the left,
     * the description of what is picked on the right.
     * <p>
     * The two halves are independent: the left one is a list of its own with a fixed height, scrolled by its
     * own bar just left of the divider, so a wand with many cores does not push the description around. Every
     * entry is a plate of its own, and the picked one is drawn brighter than the rest.
     */
    public static class TwoColumn extends WandRow
    {
        /**
         * @param label    what the entry is called
         * @param selected whether it is the one in use
         */
        public record Entry(Component label, boolean selected) {
        }

        private static final int ENTRY_HEIGHT = 20;
        private static final int ENTRY_GAP = 4;
        private static final int TEXT_LINE = 10;
        /** How many entries the left list shows before it starts scrolling on its own. */
        private static final int VISIBLE_ENTRIES = 4;

        private final List<Entry> entries;
        private final Component description;
        private final IntConsumer onSelect;

        private int leftScroll;
        private boolean scrolledToSelection;

        public TwoColumn(List<Entry> entries, Component description, IntConsumer onSelect) {
            this.entries = entries;
            this.description = description;
            this.onSelect = onSelect;
        }

        private int columnWidth() {
            return Math.max(60, (width - 28) / 2);
        }

        private int dividerX() {
            return x + 12 + columnWidth() + 8;
        }

        private int descriptionX() {
            return dividerX() + 10;
        }

        private int descriptionWidth() {
            return Math.max(40, right() - descriptionX());
        }

        private List<FormattedCharSequence> descriptionLines(Font font) {
            return font.split(description, descriptionWidth());
        }

        private int lineStep() {
            return ENTRY_HEIGHT + ENTRY_GAP;
        }

        private int listHeight() {
            return entries.size() * ENTRY_HEIGHT + Math.max(0, entries.size() - 1) * ENTRY_GAP;
        }

        private int viewport() {
            int rows = Math.min(entries.size(), VISIBLE_ENTRIES);
            return Math.max(ENTRY_HEIGHT, rows * ENTRY_HEIGHT + Math.max(0, rows - 1) * ENTRY_GAP);
        }

        private int maxScroll() {
            return Math.max(0, listHeight() - viewport());
        }

        @Override
        public int height() {
            Font font = Minecraft.getInstance().font;
            return Math.max(viewport(), descriptionLines(font).size() * TEXT_LINE) + 8;
        }

        @Override
        protected boolean background() {
            return false;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int top = y + 4;
            if(!scrolledToSelection) {
                // Open the list on the entry in use, so the picked one is not hidden below the window.
                scrolledToSelection = true;
                for(int index = 0; index < entries.size(); index++) {
                    if(!entries.get(index).selected()) continue;
                    leftScroll = Mth.clamp(index * lineStep() + ENTRY_HEIGHT - viewport(), 0, maxScroll());
                }
            }
            leftScroll = Math.min(leftScroll, maxScroll());

            // The list has a window of its own: entries scroll inside it, the description does not move.
            graphics.enableScissor(x + 10, top, dividerX() - 6, top + viewport());
            for(int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                int entryY = top + index * lineStep() - leftScroll;
                if(entryY + ENTRY_HEIGHT < top || entryY > top + viewport()) continue;

                // Every entry carries a plate; the picked one is drawn brighter than the rest.
                Ui.rounded(graphics, x + 12, entryY, columnWidth(), ENTRY_HEIGHT,
                        entry.selected() ? 0x2EFFFFFF : 0x12FFFFFF);
                Ui.text(graphics, font, trim(font, entry.label(), columnWidth() - 12), x + 18, entryY + 6,
                        entry.selected() ? Ui.OK : Ui.TEXT);
            }
            graphics.disableScissor();

            if(maxScroll() > 0) {
                Ui.scrollBar(graphics, dividerX() - 6, top, viewport(), listHeight(), viewport(), leftScroll);
            }

            // The divider that keeps the two columns apart.
            Ui.rounded(graphics, dividerX(), y + 2, 1, height() - 4, Ui.DIVIDER);

            int descriptionTop = y + 4;
            for(FormattedCharSequence line : descriptionLines(font)) {
                graphics.drawString(font, line, descriptionX(), descriptionTop, Ui.TEXT_DIM, false);
                descriptionTop += TEXT_LINE;
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if(maxScroll() <= 0) return false;
            if(mouseX < x + 10 || mouseX >= dividerX() - 6) return false;
            if(mouseY < y + 4 || mouseY > y + 4 + viewport()) return false;

            int next = Mth.clamp(leftScroll - (int) Math.round(delta * lineStep()), 0, maxScroll());
            if(next == leftScroll) return false;
            leftScroll = next;
            return true;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !contains(mouseX, mouseY)) return false;
            // Only the left column is a list, the right one is the description.
            if(mouseX >= dividerX()) return false;

            int offsetY = (int) (mouseY - (y + 4)) + leftScroll;
            if(offsetY < 0) return false;

            int index = offsetY / lineStep();
            if(index < 0 || index >= entries.size()) return false;
            // A click in the gap between two entries belongs to neither of them.
            if(offsetY % lineStep() > ENTRY_HEIGHT) return false;
            // A click below the window is outside the list that is drawn.
            if(mouseY > y + 4 + viewport()) return false;

            onSelect.accept(index);
            return true;
        }
    }
    /**
     * A section heading inside the list, with the remaining width underlined once.
     */
    public static class Header extends WandRow
    {
        private final Component title;

        public Header(Component title) {
            this.title = title;
        }

        @Override
        public int height() {
            return 26;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int baseline = y + height() - 12;
            Ui.text(graphics, font, title, x + 4, baseline, Ui.TEXT_DIM);
            int textEnd = x + 6 + font.width(title);
            Ui.separator(graphics, textEnd, baseline + 4, Math.max(0, right() - textEnd));
        }
    }

    /**
     * A sentence that is allowed to take as many lines as it needs, which is what the description of a core
     * is: cutting it to one row with an ellipsis leaves the player guessing what the core does.
     */
    public static class Paragraph extends WandRow
    {
        private final Component label;
        private final Component text;

        public Paragraph(Component label, Component text) {
            this.label = label;
            this.text = text;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        private List<net.minecraft.util.FormattedCharSequence> lines(Font font) {
            return font.split(label.copy().append(text), Math.max(40, width - 20));
        }

        @Override
        public int height() {
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            return Math.max(1, lines(font).size()) * 10 + 4;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int top = y + 2;
            for(net.minecraft.util.FormattedCharSequence line : lines(font)) {
                graphics.drawString(font, line, x + 10, top, Ui.TEXT_DIM, false);
                top += 10;
            }
        }
    }
    /**
     * A line that only reports something: the binding of a core, the ladder of the time core, or the
     * sentence that stands in for an empty list.
     */
    public static class Text extends WandRow
    {
        private final Component label;
        @Nullable
        private final Component value;
        private final int valueColor;
        @Nullable
        private final Component tooltip;
        private final boolean striped;

        public Text(Component label, @Nullable Component value, int valueColor, boolean striped, @Nullable Component tooltip) {
            this.label = label;
            this.value = value;
            this.valueColor = valueColor;
            this.striped = striped;
            this.tooltip = tooltip;
        }

        public static Text plain(Component text) {
            return new Text(text, null, Ui.TEXT_DIM, false, null);
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        protected boolean striped() {
            return striped;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int available = right() - labelX(null);

            // A row without a label is a caption: the value is the whole line, left aligned.
            if(label.getString().isEmpty()) {
                if(value != null) {
                    Ui.text(graphics, font, trim(font, value, available), labelX(null), labelY(height()), valueColor);
                }
                return;
            }

            // A line that only reports something is not bound to the control column: the value may use
            // whatever the label leaves over, which is what a long value like the time ladder needs.
            String valueText = value == null ? null : value.getString();
            int valueWidth = valueText == null ? 0
                    : Math.min(font.width(valueText), Math.max(CONTROL_WIDTH, available - 60));

            String labelText = trim(font, label, Math.max(20, available - (valueText == null ? 0 : valueWidth + 10)));
            // A report row is not clickable, which the base class takes as "disabled" and greys out. What it
            // reports is not a disabled setting though, so a row with a label reads as normal text; only a
            // caption without one stays dim.
            Ui.text(graphics, font, labelText, labelX(null), labelY(height()), Ui.TEXT);

            if(valueText == null) return;
            Ui.textRight(graphics, font, trim(font, value, Math.max(0, available - font.width(labelText) - 10)),
                    right(), labelY(height()), valueColor);
        }

        @Nullable
        @Override
        public Component tooltip() {
            return tooltip;
        }
    }

    /**
     * A setting with a fixed set of values. The whole row cycles, and the value is drawn between two
     * arrows so it is obvious that it does.
     */
    public static class Cycle extends WandRow
    {
        @Nullable
        private final ItemStack icon;
        private final Component label;
        @Nullable
        private final Component subtitle;
        private final Component value;
        private final boolean enabled;
        @Nullable
        private final Component tooltip;
        private final java.util.function.Consumer<Boolean> onCycle;
        private final boolean striped;
        private final boolean arrows;

        public Cycle(@Nullable ItemStack icon, Component label, @Nullable Component subtitle, Component value,
                     boolean enabled, @Nullable Component tooltip, boolean striped,
                     java.util.function.Consumer<Boolean> onCycle) {
            this(icon, label, subtitle, value, enabled, tooltip, striped, true, onCycle);
        }

        /**
         * @param arrows whether the value sits in a box with the two arrows. A row that is a list entry
         *               instead of a setting keeps its value plain, so nothing suggests stepping through it.
         */
        public Cycle(@Nullable ItemStack icon, Component label, @Nullable Component subtitle, Component value,
                     boolean enabled, @Nullable Component tooltip, boolean striped, boolean arrows,
                     java.util.function.Consumer<Boolean> onCycle) {
            this.icon = icon;
            this.label = label;
            this.subtitle = subtitle;
            this.value = value;
            this.enabled = enabled;
            this.tooltip = tooltip;
            this.striped = striped;
            this.arrows = arrows;
            this.onCycle = onCycle;
        }

        @Override
        public int height() {
            return subtitle == null ? ROW_HEIGHT : TALL_HEIGHT;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        protected boolean striped() {
            return striped;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int height = height();
            renderIcon(graphics, icon, height);
            int labelX = labelX(icon);

            int labelSpace = controlX() - labelX - 8;
            if(subtitle == null) {
                Ui.text(graphics, font, trim(font, label, labelSpace), labelX, labelY(height), labelColor);
            }
            else {
                Ui.text(graphics, font, trim(font, label, labelSpace), labelX, y + 6, labelColor);
                int color = enabled ? Ui.TEXT_DIM : Ui.TEXT_OFF;
                Ui.text(graphics, font, trim(font, subtitle, labelSpace), labelX, y + 18, color);
            }
            renderValue(graphics, font, enabled, value, controlX(), right(), y + (height - 14) / 2, arrows);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !enabled || !contains(mouseX, mouseY)) return false;

            // The two arrows of the value box do what they look like they do: the left one goes back, the
            // right one forward. Anywhere else on the row moves forward, as it always has.
            boolean inControl = mouseX >= controlX();
            boolean back = inControl && mouseX < (controlX() + right()) / 2.0;
            onCycle.accept(back);
            return true;
        }

        @Nullable
        @Override
        public Component tooltip() {
            return tooltip;
        }
    }

    /**
     * Draws a value box with the two arrows, shared by the rows that cycle through values.
     */
    static void renderValue(GuiGraphics graphics, Font font, boolean enabled, Component value,
                            int left, int right, int y, boolean arrows) {
        int height = 14;
        int accent = enabled ? Ui.accent() : Ui.TEXT_OFF;
        Ui.rounded(graphics, left, y, right - left, height, 0x22FFFFFF);
        if(arrows) {
            Ui.textRight(graphics, font, "\u203a", right - 5, y + 3, accent);
            Ui.text(graphics, font, "\u2039", left + 5, y + 3, accent);
        }

        String text = trim(font, value, right - left - (arrows ? 22 : 12));
        Ui.text(graphics, font, text, (left + right) / 2 - font.width(text) / 2, y + 3,
                enabled ? Ui.TEXT : Ui.TEXT_OFF);
    }

    /**
     * A setting that is simply on or off.
     */
    public static class Toggle extends WandRow
    {
        @Nullable
        private final ItemStack icon;
        private final Component label;
        @Nullable
        private final Component subtitle;
        private final boolean on;
        private final boolean enabled;
        @Nullable
        private final Component tooltip;
        private final Runnable onToggle;

        public Toggle(@Nullable ItemStack icon, Component label, @Nullable Component subtitle, boolean on,
                      boolean enabled, @Nullable Component tooltip, Runnable onToggle) {
            this.icon = icon;
            this.label = label;
            this.subtitle = subtitle;
            this.on = on;
            this.enabled = enabled;
            this.tooltip = tooltip;
            this.onToggle = onToggle;
        }

        @Override
        public int height() {
            return subtitle == null ? ROW_HEIGHT : TALL_HEIGHT;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            int height = height();
            renderIcon(graphics, icon, height);
            int labelX = labelX(icon);
            int labelSpace = controlX() - labelX - 8;

            // With a second line under it the label sits at the top of the row. Centring it leaves only a
            // few pixels to the subtitle and the two lines overlap.
            int labelY = subtitle == null ? y + (height - 8) / 2 : y + 6;
            Ui.text(graphics, font, trim(font, label, labelSpace), labelX, labelY, labelColor);
            if(subtitle != null) {
                Ui.text(graphics, font, trim(font, subtitle, labelSpace), labelX, y + 18,
                        enabled ? Ui.TEXT_DIM : Ui.TEXT_OFF);
            }

            boolean state = on && enabled;
            Ui.toggle(graphics, right() - 32, y + (height - 14) / 2, 32, 14, state,
                    state ? Ui.accent() : Ui.TRACK);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !enabled || !contains(mouseX, mouseY)) return false;
            onToggle.run();
            return true;
        }

        @Nullable
        @Override
        public Component tooltip() {
            return tooltip;
        }
    }

    /**
     * A number in a range: the label and the value on one line, the track across the full width below.
     */
    public static class Slider extends WandRow
    {
        private final Component label;
        private final java.util.function.IntFunction<Component> valueText;
        private final int min;
        private final int max;
        private final boolean enabled;
        @Nullable
        private final Component tooltip;
        private final IntConsumer onCommit;

        private int value;
        private boolean dragging;

        public Slider(Component label, java.util.function.IntFunction<Component> valueText, int min, int max,
                      int value, boolean enabled, @Nullable Component tooltip, IntConsumer onCommit) {
            this.label = label;
            this.valueText = valueText;
            this.min = min;
            this.max = max;
            this.value = value;
            this.enabled = enabled;
            this.tooltip = tooltip;
            this.onCommit = onCommit;
        }

        @Override
        public int height() {
            return TALL_HEIGHT;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        private int trackX() {
            return x + 10;
        }

        private int trackWidth() {
            return width - 20;
        }

        private float fraction() {
            return max <= min ? 0.0F : (float) (value - min) / (float) (max - min);
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            Ui.text(graphics, font, trim(font, label, width - 90), x + 10, y + 5, labelColor);
            // The text is built from the value of this moment: a fixed component would keep showing the
            // number the row was created with until the mouse is let go.
            Ui.textRight(graphics, font, valueText.apply(value).getString(), right() - 2, y + 5,
                    enabled ? Ui.TEXT : Ui.TEXT_OFF);
            Ui.slider(graphics, trackX(), y + 21, trackWidth(), 10, fraction(),
                    enabled ? Ui.accent() : Ui.TEXT_OFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !enabled || !contains(mouseX, mouseY)) return false;
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if(!dragging) return false;
            updateFromMouse(mouseX);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if(!dragging) return false;
            dragging = false;
            onCommit.accept(value);
            return true;
        }

        private void updateFromMouse(double mouseX) {
            float fraction = (float) ((mouseX - trackX()) / Math.max(1.0, trackWidth()));
            fraction = net.minecraft.util.Mth.clamp(fraction, 0.0F, 1.0F);
            value = net.minecraft.util.Mth.clamp(min + Math.round(fraction * (max - min)), min, max);
        }

        @Nullable
        @Override
        public Component tooltip() {
            return tooltip;
        }
    }

    /**
     * A line with a button on the right, for an action that is not a setting.
     */
    public static class Action extends WandRow
    {
        /**
         * How long an armed button stays armed. Long enough to read the question, short enough that a
         * forgotten click does not fire minutes later.
         */
        private static final long CONFIRM_MILLIS = 5000L;

        private final Component label;
        private final Component buttonLabel;
        private final boolean enabled;
        @Nullable
        private final Component tooltip;
        private final Runnable action;
        /**
         * The question a button asks before doing something that cannot be taken back, or {@code null} for
         * a button that just acts.
         */
        @Nullable
        private final String confirmKey;
        private long armedAt;

        public Action(Component label, Component buttonLabel, boolean enabled, @Nullable Component tooltip, Runnable action) {
            this(label, buttonLabel, enabled, tooltip, action, null);
        }

        public Action(Component label, Component buttonLabel, boolean enabled, @Nullable Component tooltip,
                      Runnable action, @Nullable String confirmKey) {
            this.label = label;
            this.buttonLabel = buttonLabel;
            this.enabled = enabled;
            this.tooltip = tooltip;
            this.action = action;
            this.confirmKey = confirmKey;
        }

        private boolean armed() {
            return confirmKey != null && armedAt > 0L && System.currentTimeMillis() - armedAt < CONFIRM_MILLIS;
        }

        /**
         * @return the seconds left to answer the question, rounded up so it never shows zero
         */
        private int secondsLeft() {
            long left = CONFIRM_MILLIS - (System.currentTimeMillis() - armedAt);
            return (int) Math.max(1L, (left + 999L) / 1000L);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            Ui.text(graphics, font, trim(font, label, controlX() - labelX(null) - 8), labelX(null), labelY(height()), labelColor);

            int buttonWidth = CONTROL_WIDTH;
            int buttonX = right() - buttonWidth;
            int buttonY = y + (height() - 14) / 2;
            boolean hover = isHovered() && enabled;
            boolean armed = armed();

            Ui.rounded(graphics, buttonX, buttonY, buttonWidth, 14,
                    armed ? (hover ? 0x66FFAA00 : 0x55FFAA00) : (hover ? 0x3DFFFFFF : 0x22FFFFFF));

            Component shown = armed && confirmKey != null
                    ? Component.translatable(confirmKey, secondsLeft())
                    : buttonLabel;
            String text = trim(font, shown, buttonWidth - 8);
            Ui.text(graphics, font, text, buttonX + buttonWidth / 2 - font.width(text) / 2, buttonY + 3,
                    enabled ? (armed ? Ui.WARN : Ui.TEXT) : Ui.TEXT_OFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !enabled || !contains(mouseX, mouseY)) return false;

            // A button that asks first needs the second click to be the answer to its own question.
            if(confirmKey != null) {
                if(!armed()) {
                    armedAt = System.currentTimeMillis();
                    return true;
                }
                armedAt = 0L;
            }

            action.run();
            return true;
        }

        @Nullable
        @Override
        public Component tooltip() {
            return armed() && confirmKey != null
                    ? Component.translatable(confirmKey, secondsLeft())
                    : tooltip;
        }
    }

    /**
     * A bound container. The name is grabbable and is what the screen drags to reorder the list; the cross
     * on the right unbinds it.
     */
    public static class Binding extends WandRow
    {
        public final int index;
        @Nullable
        public final net.minecraft.core.BlockPos pos;
        private final Component name;
        private final Component coords;
        @Nullable
        private final Component tooltip;
        private final Runnable onRemove;

        public Binding(int index, @Nullable net.minecraft.core.BlockPos pos, Component name, Component coords,
                       @Nullable Component tooltip, Runnable onRemove) {
            this.index = index;
            this.pos = pos;
            this.name = name;
            this.coords = coords;
            this.tooltip = tooltip;
            this.onRemove = onRemove;
        }

        private int removeX() {
            return right() - 12;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Font font, int labelColor) {
            String prefix = (index + 1) + ".";
            Ui.text(graphics, font, prefix, x + 8, labelY(height()), Ui.TEXT_DIM);

            int textX = x + 8 + font.width(prefix) + 6;
            int available = removeX() - 8 - textX;
            String nameText = trim(font, name, Math.max(0, available - font.width(coords) - 6));
            String coordText = trim(font, coords, Math.max(0, available - font.width(nameText) - 6));

            Ui.text(graphics, font, nameText.isEmpty() ? coordText : nameText, textX, labelY(height()), labelColor);
            if(!nameText.isEmpty()) {
                Ui.textRight(graphics, font, coordText, removeX() - 8, labelY(height()), Ui.TEXT_DIM);
            }

            boolean hover = isHovered();
            Ui.text(graphics, font, "\u2715", removeX(), labelY(height()), hover ? Ui.WARN : Ui.TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(button != 0 || !contains(mouseX, mouseY)) return false;
            if(mouseX >= removeX() - 2) {
                onRemove.run();
                return true;
            }
            return false;
        }

        @Nullable
        @Override
        public Component tooltip() {
            return tooltip;
        }
    }
}
