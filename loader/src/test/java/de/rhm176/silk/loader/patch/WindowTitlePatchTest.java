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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.rhm176.silk.loader.EquilinoxGameProvider;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
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

@ExtendWith(MockitoExtension.class)
class WindowTitlePatchTest {
    private WindowTitlePatch patch;

    @Mock
    private EquilinoxGameProvider gameProviderMock;

    @Mock
    private FabricLauncher launcherMock;

    @Mock
    private Function<String, ClassNode> classSourceMock;

    @Mock
    private Consumer<ClassNode> classEmitterMock;

    private MockedStatic<Log> logMock;

    @BeforeEach
    void setUp() {
        patch = new WindowTitlePatch(gameProviderMock);
        logMock = Mockito.mockStatic(Log.class);
    }

    @AfterEach
    void tearDown() {
        logMock.close();
    }

    private ClassNode createDisplayManagerClassNode(InsnList createDisplayInstructions) {
        ClassNode classNode = new ClassNode();
        classNode.name = WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME;
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.superName = "java/lang/Object";

        MethodNode methodNode =
                new MethodNode(Opcodes.ACC_PUBLIC, WindowTitlePatch.TARGET_METHOD_NAME, "()V", null, null);
        if (createDisplayInstructions != null) {
            methodNode.instructions = createDisplayInstructions;
        } else {
            methodNode.instructions.add(new InsnNode(Opcodes.RETURN));
        }
        classNode.methods.add(methodNode);
        return classNode;
    }

    private InsnList createStandardPatternInstructions(int gameTextArg) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new InsnNode(Opcodes.ICONST_0));

        if (gameTextArg == 1) {
            instructions.add(new InsnNode(Opcodes.ICONST_1));
        } else {
            instructions.add(new LdcInsnNode(gameTextArg));
        }
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WindowTitlePatch.GAME_TEXT_CLASS_INTERNAL_NAME,
                WindowTitlePatch.GET_TEXT_METHOD_NAME,
                WindowTitlePatch.GET_TEXT_METHOD_DESCRIPTOR,
                false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WindowTitlePatch.DISPLAY_CLASS_INTERNAL_NAME,
                WindowTitlePatch.SET_TITLE_METHOD_NAME,
                WindowTitlePatch.SET_TITLE_METHOD_DESCRIPTOR,
                false));
        instructions.add(new InsnNode(Opcodes.RETURN));
        return instructions;
    }

    @Test
    void process_targetPatternFound_injectsTitleModification() {
        String mockGameVersion = "1.2.3";
        when(gameProviderMock.getRawGameVersion()).thenReturn(mockGameVersion);

        String actualFabricVersionSuffix = FabricLoaderImpl.INSTANCE
                .getModContainer("fabricloader")
                .map(ModContainer::getMetadata)
                .map(ModMetadata::getVersion)
                .map(Version::getFriendlyString)
                .orElse(FabricLoaderImpl.VERSION);
        String expectedLdcString = " " + mockGameVersion + " - Fabric Loader " + actualFabricVersionSuffix;

        ClassNode displayManagerClass = createDisplayManagerClassNode(createStandardPatternInstructions(1));
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(displayManagerClass);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        ArgumentCaptor<ClassNode> classNodeCaptor = ArgumentCaptor.forClass(ClassNode.class);
        verify(classEmitterMock).accept(classNodeCaptor.capture());
        ClassNode modifiedClass = classNodeCaptor.getValue();

        MethodNode createDisplayMethod = modifiedClass.methods.stream()
                .filter(m -> m.name.equals(WindowTitlePatch.TARGET_METHOD_NAME))
                .findFirst()
                .orElse(null);
        assertNotNull(createDisplayMethod);

        List<AbstractInsnNode> instructions =
                Arrays.stream(createDisplayMethod.instructions.toArray()).toList();
        boolean foundLdc = false;
        boolean foundConcat = false;
        int setTitleCallIndex = -1;
        int ldcIndex = -1;
        int concatIndex = -1;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof LdcInsnNode ldc) {
                if (ldc.cst instanceof String && ldc.cst.equals(expectedLdcString)) {
                    foundLdc = true;
                    ldcIndex = i;
                }
            }
            if (insn instanceof MethodInsnNode methodInsn) {
                if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && methodInsn.owner.equals("java/lang/String")
                        && methodInsn.name.equals("concat")) {
                    foundConcat = true;
                    concatIndex = i;
                }
                if (methodInsn.owner.equals(WindowTitlePatch.DISPLAY_CLASS_INTERNAL_NAME)
                        && methodInsn.name.equals(WindowTitlePatch.SET_TITLE_METHOD_NAME)) {
                    setTitleCallIndex = i;
                }
            }
        }

        assertTrue(
                foundLdc,
                "LDC instruction with expected title suffix not found. Expected: \"" + expectedLdcString + "\"");
        assertTrue(foundConcat, "String.concat call not found.");
        assertTrue(
                ldcIndex != -1 && setTitleCallIndex != -1 && ldcIndex < setTitleCallIndex,
                "LDC was not before setTitle.");
        assertTrue(concatIndex != -1 && concatIndex == ldcIndex + 1, "Concat call was not immediately after LDC.");
        assertEquals(concatIndex, setTitleCallIndex - 1, "Concat call was not immediately before setTitle.");

        logMock.verify(() -> Log.debug(
                eq(LogCategory.GAME_PATCH),
                eq("Applying window title hook to %s::%s"),
                eq(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME),
                eq(WindowTitlePatch.TARGET_METHOD_NAME)));
    }

    @Test
    void process_targetPattern_argNotOne_noInjection() {
        ClassNode displayManagerClass = createDisplayManagerClassNode(createStandardPatternInstructions(2));
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(displayManagerClass);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        verify(classEmitterMock, never()).accept(any());
    }

    @Test
    void process_setTitleCallNotFound_noInjection() {
        InsnList instructions = new InsnList();
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WindowTitlePatch.GAME_TEXT_CLASS_INTERNAL_NAME,
                WindowTitlePatch.GET_TEXT_METHOD_NAME,
                WindowTitlePatch.GET_TEXT_METHOD_DESCRIPTOR,
                false));
        instructions.add(new InsnNode(Opcodes.RETURN));
        ClassNode displayManagerClass = createDisplayManagerClassNode(instructions);
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(displayManagerClass);

        patch.process(launcherMock, classSourceMock, classEmitterMock);
        verify(classEmitterMock, never()).accept(any());
    }

    @Test
    void process_getTextCallNotFound_noInjection() {
        InsnList instructions = new InsnList();
        instructions.add(new LdcInsnNode("Some Title"));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WindowTitlePatch.DISPLAY_CLASS_INTERNAL_NAME,
                WindowTitlePatch.SET_TITLE_METHOD_NAME,
                WindowTitlePatch.SET_TITLE_METHOD_DESCRIPTOR,
                false));
        instructions.add(new InsnNode(Opcodes.RETURN));
        ClassNode displayManagerClass = createDisplayManagerClassNode(instructions);
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(displayManagerClass);

        patch.process(launcherMock, classSourceMock, classEmitterMock);
        verify(classEmitterMock, never()).accept(any());
    }

    @Test
    void process_targetMethodNotFound_noAction() {
        ClassNode displayManagerClass = new ClassNode();
        displayManagerClass.name = WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME;
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(displayManagerClass);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        verify(classEmitterMock, never()).accept(any());
        logMock.verify(
                () -> Log.debug(
                        eq(LogCategory.GAME_PATCH),
                        anyString(),
                        eq(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME),
                        anyString()),
                times(0));
    }

    @Test
    void process_targetClassNotFound_noAction() {
        when(classSourceMock.apply(WindowTitlePatch.TARGET_CLASS_INTERNAL_NAME.replace('/', '.')))
                .thenReturn(null);

        patch.process(launcherMock, classSourceMock, classEmitterMock);

        verify(classEmitterMock, never()).accept(any());
        logMock.verifyNoInteractions();
    }

    @Test
    void isArgOne_ICONST_1_returnsTrue() {
        assertTrue(WindowTitlePatch.isArgOne(new InsnNode(Opcodes.ICONST_1)));
    }

    @Test
    void isArgOne_LdcInsnNode_Integer1_returnsTrue() {
        assertTrue(WindowTitlePatch.isArgOne(new LdcInsnNode(1)));
    }

    @Test
    void isArgOne_ICONST_0_returnsFalse() {
        assertFalse(WindowTitlePatch.isArgOne(new InsnNode(Opcodes.ICONST_0)));
    }

    @Test
    void isArgOne_LdcInsnNode_Integer0_returnsFalse() {
        assertFalse(WindowTitlePatch.isArgOne(new LdcInsnNode(0)));
    }

    @Test
    void isArgOne_LdcInsnNode_String_returnsFalse() {
        assertFalse(WindowTitlePatch.isArgOne(new LdcInsnNode("1")));
    }

    @Test
    void isArgOne_OtherInsnNode_returnsFalse() {
        assertFalse(WindowTitlePatch.isArgOne(new VarInsnNode(Opcodes.ALOAD, 0)));
    }
}
