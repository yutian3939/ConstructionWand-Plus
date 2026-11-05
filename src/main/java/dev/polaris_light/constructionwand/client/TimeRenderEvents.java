package dev.polaris_light.constructionwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.plus.basics.TimeAcceleration;
import dev.polaris_light.constructionwand.plus.basics.TimeCoreLevels;
import dev.polaris_light.constructionwand.plus.basics.TimeTarget;
import dev.polaris_light.constructionwand.plus.items.ItemCoreTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws what the time core is doing, the way the time wand of JustDireThings does it: the block keeps a
 * highlight around it, and its six faces carry the speed and the time left together with two progress
 * bars, so the numbers sit on the block itself instead of floating in front of the player.
 * <p>
 * The faces are drawn in the block's own frame: each one is turned so that its local space has {@code +Z}
 * pointing out of the face and {@code +Y} running up along it, and the text and the bars are written in
 * that space a hundredth of a block per font pixel.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT)
public final class TimeRenderEvents
{
    /**
     * How far away an accelerated block is still marked. The blocks inside it are found by walking the list
     * the server sent, which is short, so the distance only has to keep the far ones from cluttering the
     * view.
     */
    private static final double VISIBLE_RANGE = 64.0D;
    /**
     * One font pixel, in blocks. The labels of a block face are small, but they are read from up close.
     */
    private static final float TEXT_SCALE = 0.01F;
    /**
     * One face of a block, ready to be written on.
     * <p>
     * The pose stack is moved to the bottom centre of the block, so the numbers here are measured from
     * there: {@code surface} is how far the face is from that point along the face's own normal, which is
     * half a block for the four sides, a whole block for the top and nothing for the bottom. The two rows
     * are heights for the sides and offsets across the face for the top and the bottom, because a face that
     * points up has no "height" to speak of.
     *
     * @param rotation turns the local space so that {@code +Z} points out of the face and {@code +Y} runs up
     *                 along it
     * @param normal   the outward normal in world space, to tell whether the face is turned towards the player
     * @param surface  distance of the face from the bottom centre of the block, plus a hair to stand off it
     * @param rateRow  where the speed row sits: a height on the sides, an offset across the top and bottom
     * @param timeRow  where the time row sits
     */
    private record Face(Quaternionf rotation, Vector3f normal, float surface, float rateRow, float timeRow) {
    }

    private static final float SIDE = 0.502F;
    private static final float TOP = 1.002F;
    private static final float BOTTOM = -0.002F;

    /**
     * The four upright faces of a block, in the order south, north, east, west. They never move, so a block
     * can be read from any of its sides.
     */
    private static final List<Face> SIDE_FACES = List.of(
            new Face(new Quaternionf(), new Vector3f(0.0F, 0.0F, 1.0F), SIDE, 0.32F, 0.62F),
            new Face(new Quaternionf().rotateY((float) Math.PI), new Vector3f(0.0F, 0.0F, -1.0F), SIDE, 0.32F, 0.62F),
            new Face(new Quaternionf().rotateY((float) Math.PI / 2.0F), new Vector3f(1.0F, 0.0F, 0.0F), SIDE, 0.32F, 0.62F),
            new Face(new Quaternionf().rotateY((float) -Math.PI / 2.0F), new Vector3f(-1.0F, 0.0F, 0.0F), SIDE, 0.32F, 0.62F));

    /**
     * The faces of a block, the top and the bottom turned to follow the player.
     * <p>
     * Text written flat on the ground can only read upright from one direction, and from the other it reads
     * upside down, which looks like a mistake. The two horizontal faces are therefore turned about their own
     * normal with the heading the block was accelerated from, so the readout on its top is upright for the
     * player who placed it there and stays put while the player walks around it.
     *
     * @param heading the yaw the effect was applied with, in radians
     */
    private static List<Face> faces(TimeAcceleration.Entry entry) {
        // Only the four sides are used, so a heading stored by an older version still lands on one of them.
        float heading = (float) Math.toRadians(TimeAcceleration.cardinal(entry.heading()));
        List<Face> faces = new ArrayList<>(SIDE_FACES);

        Quaternionf top = new Quaternionf().rotateY((float) Math.PI - heading);
        top.mul(new Quaternionf().rotateX((float) -Math.PI / 2.0F));
        faces.add(new Face(top, new Vector3f(0.0F, 1.0F, 0.0F), TOP, 0.24F, -0.16F));

        Quaternionf bottom = new Quaternionf().rotateY((float) Math.PI - heading);
        bottom.mul(new Quaternionf().rotateX((float) Math.PI / 2.0F));
        faces.add(new Face(bottom, new Vector3f(0.0F, -1.0F, 0.0F), BOTTOM, -0.16F, 0.24F));

        return faces;
    }

    /** How tall a bar is, and how long it can get. */
    private static final float BAR_HEIGHT = 0.07F;
    private static final float BAR_START = -0.38F;
    private static final float BAR_LENGTH = 0.76F;

    private TimeRenderEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        TimeClientState.tick();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        var entries = TimeClientState.of(minecraft.level);
        if(entries.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        List<TimeAcceleration.Entry> visible = entries.stream()
                .filter(entry -> inSight(minecraft, camera, event, entry))
                .toList();
        if(visible.isEmpty()) return;

        // The three kinds of geometry are drawn one after the other, each with its own buffer closed
        // before the next kind is asked for: asking for a second render type finishes the first batch,
        // and adding to a finished one throws.
        int colour = ItemCoreTime.CORE_COLOR;
        float red = ((colour >> 16) & 0xFF) / 255.0F;
        float green = ((colour >> 8) & 0xFF) / 255.0F;
        float blue = (colour & 0xFF) / 255.0F;

        // The top and the bottom keep the heading the effect was applied with, so the readout on the ground
        // reads upright from where it was made and does not turn while the player walks around.'

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for(TimeAcceleration.Entry entry : visible) {
            // A machine is outlined as the whole machine: the block that was clicked is only one of its
            // blocks, and a box around that one block says nothing about what is being sped up.
            LevelRenderer.renderLineBox(poseStack, lines, box(entry).inflate(0.002D), red, green, blue, 0.85F);
        }
        buffers.endBatch(RenderType.lines());

        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for(TimeAcceleration.Entry entry : visible) {
            bars(poseStack, quads, entry, camera, faces(entry));
        }
        buffers.endBatch(RenderType.debugQuads());

        for(TimeAcceleration.Entry entry : visible) {
            labels(minecraft, poseStack, buffers, entry, camera, faces(entry));
        }
        buffers.endBatch();

        poseStack.popPose();
    }

    /**
     * The two progress bars of a block, on each of its six faces: green for how far up the speed ladder the
     * setting is, red for how much of the time is left.
     */
    private static void bars(PoseStack poseStack, VertexConsumer quads, TimeAcceleration.Entry entry, Vec3 camera,
                             List<Face> faces) {
        float rateProgress = (float) entry.rate() / TimeCoreLevels.LADDER[TimeCoreLevels.LADDER.length - 1];
        // The red bar is the part of the granted time that is left, the way the time wand shows it.
        float timeProgress = entry.remaining() == TimeAcceleration.PERMANENT || entry.total() <= 0 ? 1.0F
                : Math.min(1.0F, (float) entry.remaining() / entry.total());

        poseStack.pushPose();
        poseStack.translate(entry.pos().getX() + 0.5D, entry.pos().getY(), entry.pos().getZ() + 0.5D);

        for(Face face : faces) {
            if(!facesPlayer(entry, face.normal(), camera)) continue;

            poseStack.pushPose();
            poseStack.mulPose(face.rotation());

            bar(poseStack, quads, rateProgress, face.rateRow(), face.surface(), 0.35F, 0.85F, 0.35F);
            bar(poseStack, quads, timeProgress, face.timeRow(), face.surface(), 0.85F, 0.30F, 0.30F);

            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * The speed and the time left, written on each of the six faces of the block.
     */
    private static void labels(Minecraft minecraft, PoseStack poseStack, MultiBufferSource buffers,
                               TimeAcceleration.Entry entry, Vec3 camera, List<Face> faces) {
        Component rate = Component.translatable(ConstructionWand.MODID + ".time.face_rate", entry.rate());
        Component time = entry.remaining() == TimeAcceleration.PERMANENT
                ? Component.translatable(ConstructionWand.MODID + ".time.face_permanent")
                : Component.translatable(ConstructionWand.MODID + ".time.face_time", Math.max(0, entry.remaining() / 20));

        poseStack.pushPose();
        poseStack.translate(entry.pos().getX() + 0.5D, entry.pos().getY(), entry.pos().getZ() + 0.5D);

        for(Face face : faces) {
            if(!facesPlayer(entry, face.normal(), camera)) continue;

            poseStack.pushPose();
            poseStack.mulPose(face.rotation());

            text(minecraft, poseStack, buffers, rate, face.rateRow(), face.surface());
            text(minecraft, poseStack, buffers, time, face.timeRow(), face.surface());

            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * One progress bar on the face the pose stack is currently turned to.
     */
    private static void bar(PoseStack poseStack, VertexConsumer quads, float progress, float row, float surface,
                            float red, float green, float blue) {
        if(progress <= 0.0F) return;

        float end = BAR_START + BAR_LENGTH * Math.min(1.0F, progress);
        Matrix4f matrix = poseStack.last().pose();
        quad(quads, matrix, BAR_START, row, end, row + BAR_HEIGHT, surface, red, green, blue, 0.75F);
    }

    private static void quad(VertexConsumer quads, Matrix4f matrix, float x1, float y1, float x2, float y2,
                             float surface, float red, float green, float blue, float alpha) {
        quads.addVertex(matrix, x1, y1, surface).setColor(red, green, blue, alpha);
        quads.addVertex(matrix, x2, y1, surface).setColor(red, green, blue, alpha);
        quads.addVertex(matrix, x2, y2, surface).setColor(red, green, blue, alpha);
        quads.addVertex(matrix, x1, y2, surface).setColor(red, green, blue, alpha);
    }

    /**
     * One label, centred on the face and standing just off it.
     */
    private static void text(Minecraft minecraft, PoseStack poseStack, MultiBufferSource buffers,
                             Component value, float row, float surface) {
        poseStack.pushPose();
        poseStack.translate(0.0F, row, surface);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        Matrix4f matrix = poseStack.last().pose();
        minecraft.font.drawInBatch(value, -minecraft.font.width(value) / 2.0F, 0.0F, 0xFFFFFFFF, false, matrix,
                buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        poseStack.popPose();
    }

    /**
     * @return whether a face of the block is turned towards the camera
     */
    private static boolean facesPlayer(TimeAcceleration.Entry entry, Vector3f normal, Vec3 camera) {
        double x = camera.x - (entry.pos().getX() + 0.5D);
        double y = camera.y - (entry.pos().getY() + 0.5D);
        double z = camera.z - (entry.pos().getZ() + 0.5D);
        // Every face that is turned towards the player is written on, the top and the bottom included: a
        // block in a build is as often looked at from above as from the side, and its faces are the only
        // place the readout belongs. A face the player cannot see is skipped so the far side does not shine
        // through the block.
        return normal.x() * x + normal.y() * y + normal.z() * z > 0.0D;
    }

    /**
     * The box that is outlined: the machine an entry stands for, or the block itself for everything that is
     * not a machine.
     */
    private static AABB box(TimeAcceleration.Entry entry) {
        TimeTarget target = entry.target();
        return target.machine() ? AABB.encapsulatingFullBlocks(target.min(), target.max())
                : new AABB(entry.pos());
    }

    private static boolean inSight(Minecraft minecraft, Vec3 camera, RenderLevelStageEvent event,
                                   TimeAcceleration.Entry entry) {
        if(!minecraft.level.isLoaded(entry.target().tick())) return false;
        AABB box = box(entry);
        // Measured to the box rather than to its middle, so a machine the player stands next to is marked
        // even though its middle is further away than the reach of the readout.
        if(box.distanceToSqr(camera) > VISIBLE_RANGE * VISIBLE_RANGE) return false;
        return event.getFrustum().isVisible(box.inflate(0.5D));
    }
}
