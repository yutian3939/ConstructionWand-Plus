package com.xinyihl.constructionwandlegacy.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ChunkLightingTransformer implements IClassTransformer {
    private static final String CHUNK_CLASS = "net.minecraft.world.chunk.Chunk";
    private static final String CHUNK_INTERNAL = "net/minecraft/world/chunk/Chunk";
    private static final String ACCESS_INTERNAL = "com/xinyihl/constructionwandlegacy/lighting/ChunkLightingAccess";
    private static final String HOOKS_INTERNAL = "com/xinyihl/constructionwandlegacy/lighting/DeferredLightingBatch";
    private static final Set<String> RELIGHT_NAMES = new HashSet<>(Arrays.asList("relightBlock", "func_76615_h"));
    private static final Set<String> OCCLUSION_NAMES = new HashSet<>(Arrays.asList("propagateSkylightOcclusion", "func_76595_e"));
    private static volatile boolean enabled;

    static void setEnabled(boolean enabled) {
        ChunkLightingTransformer.enabled = enabled;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!enabled || basicClass == null || !CHUNK_CLASS.equals(transformedName)) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        reader.accept(classNode, 0);
        if (classNode.interfaces.contains(ACCESS_INTERNAL)) {
            return basicClass;
        }

        MethodNode relight = findMethod(classNode, RELIGHT_NAMES, "(III)V");
        MethodNode occlusion = findMethod(classNode, OCCLUSION_NAMES, "(II)V");
        if (relight == null || occlusion == null) {
            throw new IllegalStateException("Could not locate Chunk lighting methods; relight=" + (relight != null) + ", occlusion=" + (occlusion != null));
        }

        injectDeferral(relight, "deferRelight", "(L" + CHUNK_INTERNAL + ";III)Z", 3);
        injectDeferral(occlusion, "deferSkylightOcclusion", "(L" + CHUNK_INTERNAL + ";II)Z", 2);
        classNode.interfaces.add(ACCESS_INTERNAL);
        classNode.methods.add(createBridge("constructionwandlegacy$relightBlock", relight.name, "(III)V", 3));
        classNode.methods.add(createBridge("constructionwandlegacy$propagateSkylightOcclusion", occlusion.name, "(II)V", 2));

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findMethod(ClassNode classNode, Set<String> names, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (names.contains(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static void injectDeferral(MethodNode target, String hookName, String hookDescriptor, int argumentCount) {
        LabelNode continueLabel = new LabelNode(new Label());
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (int index = 1; index <= argumentCount; index++) {
            instructions.add(new VarInsnNode(Opcodes.ILOAD, index));
        }
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_INTERNAL, hookName, hookDescriptor, false));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        instructions.add(new InsnNode(Opcodes.RETURN));
        instructions.add(continueLabel);
        target.instructions.insert(instructions);
    }

    private static MethodNode createBridge(String bridgeName, String targetName, String descriptor, int argumentCount) {
        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, bridgeName, descriptor, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (int index = 1; index <= argumentCount; index++) {
            bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, index));
        }
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CHUNK_INTERNAL, targetName, descriptor, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        return bridge;
    }
}
