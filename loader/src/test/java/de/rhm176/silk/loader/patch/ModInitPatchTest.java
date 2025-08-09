/*
 * Copyright 2025 Silk Loader
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.rhm176.silk.loader.patch;

import static com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatCallsSystemExit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class ModInitPatchTest {

    private ModInitPatch patch;

    @Mock
    private FabricLauncher launcherMock;

    @Mock
    private Function<String, ClassNode> classSourceMock;

    @Mock
    private Consumer<ClassNode> classEmitterMock;

    private MockedStatic<Log> logMock;
    private MockedStatic<FabricLoaderImpl> fabricLoaderImplMock;
    private MockedStatic<FabricLoader> fabricLoaderApiMock;

    private static final String MIP_TARGET_CLASS_INTERNAL_NAME = "main/MainApp";
    private static final String MIP_TARGET_METHOD_NAME = "main";
    private static final String MIP_TARGET_METHOD_DESCRIPTOR = "([Ljava/lang/String;)V";
    private static final String MIP_PATCH_CLASS_INTERNAL_NAME =
            ModInitPatch.class.getName().replace('.', '/');
    private static final String MIP_INIT_METHOD_NAME = "init";
    private static final String MIP_BEFORE_TARGET_OWNER_INTERNAL_NAME = "gameManaging/GameManager";
    private static final String MIP_BEFORE_TARGET_METHOD_NAME = "init";
    private static final String MIP_BEFORE_TARGET_METHOD_DESCRIPTOR = "()V";

    @BeforeEach
    void setUp() {
        patch = new ModInitPatch();
        logMock = Mockito.mockStatic(Log.class);
        fabricLoaderImplMock = Mockito.mockStatic(FabricLoaderImpl.class);
        fabricLoaderApiMock = Mockito.mockStatic(FabricLoader.class);
    }

    @AfterEach
    void tearDown() {
        logMock.close();
        fabricLoaderImplMock.close();
        fabricLoaderApiMock.close();
    }

    private ClassNode createTestClassNode(
            String dotFormattedClassName, String methodName, String methodDesc, InsnList methodInstructions) {
        ClassNode classNode = new ClassNode();
        classNode.name = dotFormattedClassName.replace('.', '/');
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.superName = "java/lang/Object";

        if (methodName != null) {
            MethodNode methodNode =
                    new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, methodDesc, null, null);
            if (methodInstructions != null) {
                methodNode.instructions = methodInstructions;
            } else {
                if (methodDesc.endsWith("V")) {
                    methodNode.instructions.add(new InsnNode(Opcodes.RETURN));
                } else {
                    methodNode.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    methodNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                }
            }
            classNode.methods.add(methodNode);
        }
        return classNode;
    }

    private InsnList createInstructionsWithInjectionPoint() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                MIP_BEFORE_TARGET_OWNER_INTERNAL_NAME,
                MIP_BEFORE_TARGET_METHOD_NAME,
                MIP_BEFORE_TARGET_METHOD_DESCRIPTOR,
                false));
        instructions.add(new InsnNode(Opcodes.RETURN));
        return instructions;
    }

    private InsnList createInstructionsWithoutInjectionPoint() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new LdcInsnNode("Some other call"));
        instructions.add(new InsnNode(Opcodes.RETURN));
        return instructions;
    }

    @Test
    void process_targetFoundAndPatchedSuccessfully() {
        String dotFormattedTargetClassName = MIP_TARGET_CLASS_INTERNAL_NAME.replace('/', '.');

        ClassNode testClassNode = createTestClassNode(
                dotFormattedTargetClassName,
                MIP_TARGET_METHOD_NAME,
                MIP_TARGET_METHOD_DESCRIPTOR,
                createInstructionsWithInjectionPoint());
        assertEquals(MIP_TARGET_CLASS_INTERNAL_NAME, testClassNode.name);

        when(classSourceMock.apply(dotFormattedTargetClassName)).thenReturn(testClassNode);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        ArgumentCaptor<ClassNode> captor = ArgumentCaptor.forClass(ClassNode.class);
        verify(classEmitterMock).accept(captor.capture());
        ClassNode modifiedClassNode = captor.getValue();

        assertNotNull(modifiedClassNode);
        assertEquals(MIP_TARGET_CLASS_INTERNAL_NAME, modifiedClassNode.name);

        MethodNode mainMethod = modifiedClassNode.methods.stream()
                .filter(m -> m.name.equals(MIP_TARGET_METHOD_NAME) && m.desc.equals(MIP_TARGET_METHOD_DESCRIPTOR))
                .findFirst()
                .orElse(null);
        assertNotNull(mainMethod);

        List<AbstractInsnNode> instructions =
                Arrays.stream(mainMethod.instructions.toArray()).toList();
        boolean foundNew = false;
        boolean foundDup = false;
        boolean foundInvokeSpecial = false;
        boolean foundPatchInitCall = false;
        boolean foundInjectionPoint = false;
        int patchInitCallIndex = -1;
        int injectionPointIndex = -1;

        String expectedOwnerForNewAndInvokeSpecial = MIP_TARGET_CLASS_INTERNAL_NAME;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW) {
                if (((TypeInsnNode) insn).desc.equals(expectedOwnerForNewAndInvokeSpecial)) foundNew = true;
            }
            if (insn.getOpcode() == Opcodes.DUP) foundDup = true;
            if (insn instanceof MethodInsnNode methodInsn && insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                if (methodInsn.owner.equals(expectedOwnerForNewAndInvokeSpecial) && methodInsn.name.equals("<init>"))
                    foundInvokeSpecial = true;
            }
            if (insn instanceof MethodInsnNode methodInsn && insn.getOpcode() == Opcodes.INVOKESTATIC) {
                if (methodInsn.owner.equals(MIP_PATCH_CLASS_INTERNAL_NAME)
                        && methodInsn.name.equals(MIP_INIT_METHOD_NAME)) {
                    foundPatchInitCall = true;
                    patchInitCallIndex = i;
                }
                if (methodInsn.owner.equals(MIP_BEFORE_TARGET_OWNER_INTERNAL_NAME)
                        && methodInsn.name.equals(MIP_BEFORE_TARGET_METHOD_NAME)) {
                    foundInjectionPoint = true;
                    injectionPointIndex = i;
                }
            }
        }

        assertTrue(foundNew, "NEW instruction for " + expectedOwnerForNewAndInvokeSpecial + " not found.");
        assertTrue(foundDup, "DUP instruction not found.");
        assertTrue(
                foundInvokeSpecial, "INVOKESPECIAL <init> for " + expectedOwnerForNewAndInvokeSpecial + " not found.");
        assertTrue(foundPatchInitCall, "INVOKESTATIC call to ModInitPatch.init not found.");
        assertTrue(
                foundInjectionPoint,
                "Original injection point (" + MIP_BEFORE_TARGET_OWNER_INTERNAL_NAME + ".init) not found.");
        assertTrue(
                patchInitCallIndex < injectionPointIndex && patchInitCallIndex != -1,
                "ModInitPatch.init call was not before injection point call.");

        logMock.verify(() -> Log.debug(
                eq(LogCategory.GAME_PATCH),
                anyString(),
                eq(MIP_TARGET_CLASS_INTERNAL_NAME),
                eq(MIP_TARGET_METHOD_NAME),
                eq(MIP_TARGET_METHOD_DESCRIPTOR)));
    }

    @Test
    void process_targetMethodFound_injectionPointNotFound_logsWarning() {
        String dotFormattedTargetClassName = MIP_TARGET_CLASS_INTERNAL_NAME.replace('/', '.');
        ClassNode testClassNode = createTestClassNode(
                dotFormattedTargetClassName,
                MIP_TARGET_METHOD_NAME,
                MIP_TARGET_METHOD_DESCRIPTOR,
                createInstructionsWithoutInjectionPoint());
        assertEquals(MIP_TARGET_CLASS_INTERNAL_NAME, testClassNode.name);
        when(classSourceMock.apply(dotFormattedTargetClassName)).thenReturn(testClassNode);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        verify(classEmitterMock, never()).accept(any());
        logMock.verify(() -> Log.warn(
                eq(LogCategory.GAME_PATCH),
                anyString(),
                eq(MIP_TARGET_CLASS_INTERNAL_NAME),
                eq(MIP_TARGET_METHOD_NAME),
                eq(MIP_TARGET_METHOD_DESCRIPTOR)));
    }

    @Test
    void process_targetMethodNotFound_logsError() {
        String dotFormattedTargetClassName = MIP_TARGET_CLASS_INTERNAL_NAME.replace('/', '.');
        ClassNode testClassNode = createTestClassNode(dotFormattedTargetClassName, "otherMethod", "()V", null);
        assertEquals(MIP_TARGET_CLASS_INTERNAL_NAME, testClassNode.name);
        when(classSourceMock.apply(dotFormattedTargetClassName)).thenReturn(testClassNode);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        verify(classEmitterMock, never()).accept(any());
        logMock.verify(() -> Log.error(
                eq(LogCategory.GAME_PATCH),
                eq("ModInitPatch: Target method %s::%s%s not found in class %s. Patch not applied."),
                eq(MIP_TARGET_METHOD_NAME),
                eq(MIP_TARGET_METHOD_DESCRIPTOR),
                eq(MIP_TARGET_CLASS_INTERNAL_NAME),
                eq(MIP_TARGET_CLASS_INTERNAL_NAME)));
    }

    @Test
    void process_targetClassNotFound_logsErrorAndExits() throws Exception {
        String dotFormattedTargetClassName = MIP_TARGET_CLASS_INTERNAL_NAME.replace('/', '.');
        when(classSourceMock.apply(dotFormattedTargetClassName)).thenReturn(null);

        assertThatCallsSystemExit(() -> patch.process(launcherMock, classSourceMock, classEmitterMock))
                .withExitCode(1);

        verify(classEmitterMock, never()).accept(any());
        logMock.verify(() -> Log.error(eq(LogCategory.GAME_PATCH), eq("Could not find main class for mod init hook.")));
    }
}
