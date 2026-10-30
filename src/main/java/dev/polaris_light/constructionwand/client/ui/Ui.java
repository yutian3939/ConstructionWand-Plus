package dev.polaris_light.constructionwand.client.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of the wand screen: a flat dark panel with one accent colour, drawn from plain rectangles.
 * <p>
 * Everything the screen shows goes through here, so the whole interface keeps one set of colours and
 * one set of shapes. The accent is the colour of the selected behaviour core, which ties the window to
 * the core the player is holding.
 */
public final class Ui
{
    public static final int FALLBACK_ACCENT = 0xFF29B6F6;

    // The palette. It is not final because the screen can be switched between the dark and the light look,
    // and every drawing helper reads these fields while it draws.
    public static int BORDER;
    public static int BACKGROUND;
    public static int HEADER;
    public static int FOOTER;
    public static int TRACK;
    public static int DIVIDER;
    public static int ROW_HOVER;
    public static int ROW_STRIPE;
    public static int TEXT;
    public static int TEXT_DIM;
    public static int TEXT_OFF;
    public static int TEXT_TITLE;
    public static int OK;
    public static int WARN;

    private static boolean light;

    static {
        applyDark();
    }

    /**
     * The dark look, which is what the screen was designed as: a flat near black panel.
     */
    private static void applyDark() {
        BORDER = 0xFF2A2D31;
        BACKGROUND = 0xF0141618;
        HEADER = 0xFF1B1E21;
        FOOTER = 0xFF101214;
        TRACK = 0xFF303438;
        DIVIDER = 0x1FFFFFFF;
        ROW_HOVER = 0x16FFFFFF;
        ROW_STRIPE = 0x06FFFFFF;
        TEXT = 0xFFE8EAED;
        TEXT_DIM = 0xFF9AA0A6;
        TEXT_OFF = 0xFF5F6368;
        TEXT_TITLE = 0xFFFFFFFF;
        OK = 0xFF66BB6A;
        WARN = 0xFFFFCA28;
    }

    /**
     * The light look: the same shapes and the same accent, on a white panel. The colours are not the dark
     * ones inverted, they are picked for a white background: the text is the near black of a document, the
     * separators are a light grey, and the green and the amber are darkened so they stay readable.
     */
    private static void applyLight() {
        // Taken from the palette of the AE2 terminal (assets/ae2/screens/common/palette.json): a soft blue
        // grey instead of white, and a dark blue grey instead of black. Nothing here is a pure black or a
        // pure white, which is what made the first light theme glare.
        BORDER = 0xFF9FA3B5;
        BACKGROUND = 0xF7E7E9F0;
        HEADER = 0xFFDCDFE9;
        FOOTER = 0xFFD5D9E5;
        TRACK = 0xFFBFC4D4;
        DIVIDER = 0x33413F54;
        ROW_HOVER = 0x1F413F54;
        ROW_STRIPE = 0x0D413F54;
        TEXT = 0xFF413F54;
        TEXT_DIM = 0xFF878FA5;
        TEXT_OFF = 0xFFAEB3C4;
        TEXT_TITLE = 0xFF2E2C3D;
        OK = 0xFF3F7D3A;
        WARN = 0xFF9A6700;
    }

    public static void setLight(boolean value) {
        if(light == value) return;
        light = value;
        if(light) applyLight();
        else applyDark();
    }

    public static boolean light() {
        return light;
    }

    /**
     * The accent to write with. On the light panel the core colours are too pale to read, so the ink is a
     * darkened accent while the fills keep the colour of the core itself.
     */
    public static int accentInk() {
        if(!light) return accent;
        return 0xFF000000
                | (int) (((accent >> 16) & 0xFF) * 0.55F) << 16
                | (int) (((accent >> 8) & 0xFF) * 0.55F) << 8
                | (int) ((accent & 0xFF) * 0.55F);
    }

    /**
     * A filled circle, drawn as one line per row of pixels. The panel is made of rectangles, and a circle is
     * the one shape the two theme icons need.
     */
    /**
     * Appends an arc to an outline.
     *
     * @param steps how finely it is sampled: a corner of a few pixels needs a handful of steps, a whole
     *              circle needs {@link #CIRCLE_STEPS} or it comes out a polygon
     */
    private static void arc(List<double[]> outline, double centreX, double centreY, double radius,
                            double fromDegrees, double toDegrees, int steps) {
        if(radius <= 0.0D) return;

        for(int step = 1; step <= steps; step++) {
            double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * step / steps);
            outline.add(new double[]{centreX + Math.cos(angle) * radius, centreY + Math.sin(angle) * radius});
        }
    }

    /**
     * The number of steps a quarter arc of the given radius is worth: enough that the corner looks round
     * and no more.
     */
    private static int cornerSteps(double radius) {
        return Math.max(2, (int) Math.ceil(radius));
    }

    /**
     * A filled shape that is convex around a middle point: every step of the outline becomes a triangle with
     * that middle, which is what a triangle strip wants.
     */
    private static void fan(double centreX, double centreY, List<double[]> outline, int color) {
        VertexConsumer mesh = open();
        if(mesh == null || outline.size() < 3) return;

        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        // One triangle per two outline points, closed around the end. Drawn as independent triangles and
        // not as a strip: a strip spends every other triangle on a degenerate one, which on a shape of a
        // few pixels leaves a hairline seam.
        for(int index = 0; index < outline.size(); index++) {
            double[] from = outline.get(index);
            double[] to = outline.get((index + 1) % outline.size());
            vertex(mesh, centreX, centreY, red, green, blue, alpha);
            vertex(mesh, from[0], from[1], red, green, blue, alpha);
            vertex(mesh, to[0], to[1], red, green, blue, alpha);
        }
        flush();
    }

    private static void vertex(VertexConsumer mesh, double x, double y, float red, float green, float blue,
                               float alpha) {
        mesh.addVertex(meshPose, (float) x, (float) y, 0.0F).setColor(red, green, blue, alpha);
    }

    /**
     * A shape between two curves that share their ends, such as a crescent: one quad per pair of points.
     */
    private static void strip(List<double[]> pairs, int color) {
        VertexConsumer mesh = open();
        if(mesh == null || pairs.size() < 2) return;

        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        for(int index = 0; index < pairs.size() - 1; index++) {
            double[] from = pairs.get(index);
            double[] to = pairs.get(index + 1);
            vertex(mesh, from[0], from[1], red, green, blue, alpha);
            vertex(mesh, from[2], from[3], red, green, blue, alpha);
            vertex(mesh, to[0], to[1], red, green, blue, alpha);

            vertex(mesh, from[2], from[3], red, green, blue, alpha);
            vertex(mesh, to[2], to[3], red, green, blue, alpha);
            vertex(mesh, to[0], to[1], red, green, blue, alpha);
        }
        flush();
    }

    public static void circle(GuiGraphics graphics, double centreX, double centreY, double radius, int color) {
        if(radius <= 0.0D) return;
        if(meshPose == null) {
            graphics.fill((int) (centreX - radius), (int) (centreY - radius),
                    (int) (centreX + radius), (int) (centreY + radius), color);
            return;
        }

        int steps = Math.max(6, Math.min(CIRCLE_STEPS, (int) Math.ceil(radius * 5.0D)));
        List<double[]> outline = new ArrayList<>(steps);
        arc(outline, centreX, centreY, radius, 0.0D, 360.0D, steps);
        // The arc ends where it started, so the closing point is left out and the fan wraps around.
        outline.remove(outline.size() - 1);
        fan(centreX, centreY, outline, color);
    }

    /**
     * The sun: a disc with eight short rays, drawn for the light theme.
     */
    public static void sun(GuiGraphics graphics, double centreX, double centreY, int color) {
        circle(graphics, centreX, centreY, 4.1D, color);
        for(int step = 0; step < 8; step++) {
            double angle = Math.PI / 4.0D * step;
            circle(graphics,
                    centreX + Math.cos(angle) * 6.7D,
                    centreY + Math.sin(angle) * 6.7D,
                    1.35D, color);
        }
    }

    /**
     * The moon: what is left of one disc when a second, offset disc is taken out of it. Both arcs are
     * sampled in floating point and joined into a single shape, which keeps the two points of the crescent
     * sharp while the curves between them stay smooth.
     */
    public static void moon(GuiGraphics graphics, double centreX, double centreY, int color) {
        double radius = 6.6D;
        double cutX = centreX + radius * 0.58D;
        double cutY = centreY - radius * 0.36D;
        double cut = radius * 0.95D;
        if(meshPose == null) {
            circle(graphics, centreX, centreY, radius, color);
            return;
        }

        double dx = cutX - centreX;
        double dy = cutY - centreY;
        double distance = Math.hypot(dx, dy);
        double base = Math.atan2(dy, dx);
        double cosine = (distance * distance + radius * radius - cut * cut) / (2.0D * distance * radius);
        double half = Math.acos(Math.max(-1.0D, Math.min(1.0D, cosine)));

        // The two crossings, and the arc of each circle that runs between them. The crescent keeps the part
        // of its own circle that the cutter never reaches, so that outline takes the long way round, while
        // the cutter's outline takes the way that lies inside the disc.
        double from = base + half;
        double sweep = Math.PI * 2.0D - 2.0D * half;

        double startX = centreX + Math.cos(from) * radius;
        double startY = centreY + Math.sin(from) * radius;
        double endX = centreX + Math.cos(from + sweep) * radius;
        double endY = centreY + Math.sin(from + sweep) * radius;

        double cutFrom = Math.atan2(startY - cutY, startX - cutX);
        double cutTo = Math.atan2(endY - cutY, endX - cutX);
        double cutSweep = cutTo - cutFrom;
        while(cutSweep <= -Math.PI) cutSweep += Math.PI * 2.0D;
        while(cutSweep > Math.PI) cutSweep -= Math.PI * 2.0D;

        // The short way is the one facing the disc. When its middle lands outside the disc, the long way is.
        double midAngle = cutFrom + cutSweep / 2.0D;
        double midX = cutX + Math.cos(midAngle) * cut;
        double midY = cutY + Math.sin(midAngle) * cut;
        if(Math.hypot(midX - centreX, midY - centreY) > radius) {
            cutSweep += cutSweep > 0.0D ? -Math.PI * 2.0D : Math.PI * 2.0D;
        }

        List<double[]> pairs = new ArrayList<>(CIRCLE_STEPS + 1);
        for(int step = 0; step <= CIRCLE_STEPS; step++) {
            double t = (double) step / CIRCLE_STEPS;
            double outerAngle = from + sweep * t;
            double innerAngle = cutFrom + cutSweep * t;
            pairs.add(new double[]{
                    centreX + Math.cos(outerAngle) * radius, centreY + Math.sin(outerAngle) * radius,
                    cutX + Math.cos(innerAngle) * cut, cutY + Math.sin(innerAngle) * cut});
        }
        strip(pairs, color);
    }
    /**
     * The accent the screen is currently drawn with. It follows the selected behaviour core, so the
     * window is tinted like the core the player holds; the screen sets it once per frame.
     */
    private static int accent = FALLBACK_ACCENT;

    private Ui() {
    }

    public static void setAccent(int color) {
        accent = color == 0 ? FALLBACK_ACCENT : color;
    }

    public static int accent() {
        return accent;
    }

    /**
     * The accent of a core colour. Core colours are written without an alpha channel, so it is forced
     * here rather than letting a fully transparent colour reach the screen.
     */
    public static int fromCoreColor(int coreColor) {
        if(coreColor == 0 || coreColor == -1) return FALLBACK_ACCENT;
        return 0xFF000000 | (coreColor & 0xFFFFFF);
    }

    public static int mix(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** The radius every rounded shape is drawn with. */
    public static final int CORNER = 3;
    /** How finely a full circle is sampled. Thirty two steps are finer than the screen can show. */
    private static final int CIRCLE_STEPS = 32;

    /**
     * The rounded shapes are drawn as real triangles with floating point corners, the way the switch of
     * UniversalToolBox is drawn, instead of being filled one row of pixels at a time: a curve built from
     * whole pixels can only be jagged or blurry, while a triangle is rasterised at the resolution of the
     * screen. One buffer holds every shape of the frame.
     */
    private static final RenderType MESH = RenderType.create("constructionwand_ui",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));

    /** The pose the screen draws in, taken once per frame. */
    @Nullable
    private static Matrix4f meshPose;

    /**
     * Takes the pose the shapes of this frame are drawn in. The screen calls it once before it draws; a
     * shape drawn without it falls back to a plain rectangle rather than disappearing.
     */
    public static void begin(GuiGraphics graphics) {
        meshPose = graphics.pose().last().pose();
    }

    public static void end() {
        meshPose = null;
    }

    /**
     * Opens the buffer of the shapes and hands it out. Every shape is flushed as soon as it is written, the
     * way the switch of UniversalToolBox does it: the whole window is drawn in one pass, so a shape that
     * waited until the end of the frame would land on top of the text drawn after it.
     */
    @Nullable
    private static VertexConsumer open() {
        if(meshPose == null) return null;
        return Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(MESH);
    }

    private static void flush() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(MESH);
    }

    /**
     * A rectangle with rounded corners, as one outline of straight edges and quarter arcs.
     */
    public static void rounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        rounded(graphics, x, y, width, height, color, CORNER);
    }

    public static void rounded(GuiGraphics graphics, int x, int y, int width, int height, int color, int radius) {
        if(width <= 0 || height <= 0) return;
        if(meshPose == null) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        double r = Math.max(0.0D, Math.min(radius, Math.min(width, height) / 2.0D));
        List<double[]> outline = new ArrayList<>(CIRCLE_STEPS + 4);

        outline.add(new double[]{x + r, y});
        outline.add(new double[]{x + width - r, y});
        arc(outline, x + width - r, y + r, r, -90.0D, 0.0D, cornerSteps(r));
        outline.add(new double[]{x + width, y + height - r});
        arc(outline, x + width - r, y + height - r, r, 0.0D, 90.0D, cornerSteps(r));
        outline.add(new double[]{x + r, y + height});
        arc(outline, x + r, y + height - r, r, 90.0D, 180.0D, cornerSteps(r));
        outline.add(new double[]{x, y + r});
        arc(outline, x + r, y + r, r, 180.0D, 270.0D, cornerSteps(r));

        fan(x + width / 2.0D, y + height / 2.0D, outline, color);
    }

    /**
     * A rectangle with no rounding at all, for the few places that need a hard edge.
     */
    public static void rect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        rounded(graphics, x - 1, y - 1, width + 2, height + 2, BORDER);
        rounded(graphics, x, y, width, height, BACKGROUND);
        // One accent line along the top edge, the only strong colour in the window.
        rounded(graphics, x, y, width, 3, accent, 2);
    }

    public static void header(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y, x + width - 1, y + height, HEADER);
        separator(graphics, x + 1, y + height, width - 2);
    }

    public static void footer(GuiGraphics graphics, int x, int y, int width, int height) {
        separator(graphics, x + 1, y, width - 2);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height, FOOTER);
    }

    public static void separator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, DIVIDER);
    }

    /**
     * The background of a row: a hover highlight, or the faint stripe of every other row, so a long list
     * stays readable without drawing lines between the entries.
     */
    /**
     * A border of one pixel that follows the rounded corners of the plate it surrounds.
     * <p>
     * The ring is painted first and its middle is cleared again with the colour of the window, so what is
     * left is a hairline ring of the rounded shape - drawing four straight lines instead would square off
     * the corners.
     */
    public static void outlineRounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if(width <= 2 || height <= 2) return;
        rounded(graphics, x, y, width, height, color);
        rounded(graphics, x + 1, y + 1, width - 2, height - 2, BACKGROUND);
    }
    public static void rowBackground(GuiGraphics graphics, int x, int y, int width, int height,
                                     boolean hovered, boolean striped) {
        if(hovered) {
            rounded(graphics, x, y, width, height, ROW_HOVER);
            return;
        }
        if(striped) rounded(graphics, x, y, width, height, ROW_STRIPE);
    }

    /**
     * The pill of a switch. The track is drawn inset by one pixel on the top and bottom row, which reads
     * as the rounded shape without any texture.
     */
    public static void toggle(GuiGraphics graphics, int x, int y, int width, int height, boolean on, int accent) {
        int track = on ? accent : TRACK;
        int radius = height / 2;
        rounded(graphics, x, y, width, height, track, radius);

        int knobSize = height - 4;
        int knobX = on ? x + width - knobSize - 2 : x + 2;
        int knob = on ? 0xFFFFFFFF : (light ? 0xFFECEEF4 : 0xFF9AA0A6);
        rounded(graphics, knobX, y + 2, knobSize, knobSize, knob, knobSize / 2);
    }

    /**
     * A slider track with the filled part in the accent colour and the handle where the value sits.
     */
    public static void slider(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int accent) {
        int trackHeight = 3;
        int middle = y + height / 2 - trackHeight / 2;
        rounded(graphics, x, middle, width, trackHeight, TRACK, trackHeight);

        int filled = (int) Math.round(width * Math.min(1.0F, Math.max(0.0F, fraction)));
        if(filled > 0) rounded(graphics, x, middle, filled, trackHeight, accent, trackHeight);

        // The handle is a disc, which is what the rounded helper is for.
        double knobX = x + width * Math.min(1.0F, Math.max(0.0F, fraction));
        int radius = height / 2;
        circle(graphics, knobX, y + height / 2.0D, radius, light ? 0xFFFFFFFF : 0xFFDADCE0);
        circle(graphics, knobX, y + height / 2.0D, radius - 1.5D, accent);
    }

    /**
     * A thin scroll indicator on the right edge of a list, with the handle proportional to the visible
     * part. Drawn only while the list does not fit.
     */
    public static void scrollBar(GuiGraphics graphics, int x, int y, int height, int total, int visible, int offset) {
        if(total <= visible) return;

        rounded(graphics, x, y, 3, height, light ? 0x22413F54 : 0x22FFFFFF, 1);
        int handle = Math.max(12, height * visible / total);
        int travel = height - handle;
        int handleY = y + (int) ((long) travel * offset / Math.max(1, total - visible));
        rounded(graphics, x - 1, handleY, 5, handle, light ? 0x88413F54 : 0x66FFFFFF, 2);
    }

    public static void text(GuiGraphics graphics, net.minecraft.client.gui.Font font, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
    }

    public static void text(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component value,
                            int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
    }

    /**
     * Writes a value at the right edge of a column, which is what lines up every control of the screen.
     */
    public static void textRight(GuiGraphics graphics, net.minecraft.client.gui.Font font, String value,
                                 int right, int y, int color) {
        graphics.drawString(font, value, right - font.width(value), y, color, false);
    }
}
