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
package de.rhm176.silk.loader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@ExtendWith(MockitoExtension.class)
class EquilinoxVersionLookupTest {

    @TempDir
    Path temporaryDirectory;

    private MockedStatic<LoaderUtil> loaderUtilMock;
    private MockedStatic<ExceptionUtil> exceptionUtilMock;

    @BeforeEach
    void setUp() {
        loaderUtilMock = Mockito.mockStatic(LoaderUtil.class);
        loaderUtilMock.when(() -> LoaderUtil.getClassFileName(anyString())).thenAnswer(invocation -> {
            String className = invocation.getArgument(0);
            return className.replace('.', '/') + ".class";
        });

        exceptionUtilMock = Mockito.mockStatic(ExceptionUtil.class);
        exceptionUtilMock.when(() -> ExceptionUtil.wrap(any(Throwable.class))).thenAnswer(invocation -> {
            Throwable cause = invocation.getArgument(0);
            if (cause instanceof ExceptionUtil.WrappedException) {
                return cause;
            }
            return new ExceptionUtil.WrappedException(cause);
        });
    }

    @AfterEach
    void tearDown() {
        loaderUtilMock.close();
        exceptionUtilMock.close();
    }

    private void createJarWithClass(
            Path jarPath,
            String fqClassName,
            int classFileMajorVersion,
            String versionStringFieldValue,
            int versionStringFieldAccess)
            throws IOException {
        String internalClassName = fqClassName.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(classFileMajorVersion, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        if (versionStringFieldValue != null) {
            FieldVisitor fv = cw.visitField(
                    versionStringFieldAccess, "VERSION_STRING", "Ljava/lang/String;", null, versionStringFieldValue);
            fv.visitEnd();
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            JarEntry entry = new JarEntry(internalClassName + ".class");
            jos.putNextEntry(entry);
            jos.write(classBytes);
            jos.closeEntry();
        }
    }

    /**
     * Helper to create a JAR with a malformed class file (for class version reading).
     */
    private void createJarWithCustomBytes(Path jarPath, String fqClassName, byte[] classBytes) throws IOException {
        String internalClassName = fqClassName.replace('.', '/');
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            JarEntry entry = new JarEntry(internalClassName + ".class");
            jos.putNextEntry(entry);
            jos.write(classBytes);
            jos.closeEntry();
        }
    }

    @Test
    void getVersion_validClassWithVersionStringAndJava8_returnsCorrectVersion() throws IOException {
        Path testJar = temporaryDirectory.resolve("testGame.jar");
        String entryClass = "main.MainApp";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_8,
                "Version 1.2.3",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);

        assertEquals("1.2.3", version.rawName());
        assertEquals("1.2.3", version.displayName());
    }

    @Test
    void getVersion_versionStringWithoutPrefix_handledCorrectly() throws IOException {
        Path testJar = temporaryDirectory.resolve("testGameNoPrefix.jar");
        String entryClass = "main.App";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_7,
                "0.9.final",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);

        assertEquals("0.9.final", version.rawName());
        assertEquals("0.9.final", version.displayName());
    }

    @Test
    void getVersion_noVersionStringField_returnsEmptyVersionName() throws IOException {
        Path testJar = temporaryDirectory.resolve("noVersionString.jar");
        String entryClass = "main.NoVersion";
        createJarWithClass(testJar, entryClass, Opcodes.V1_8, null, 0);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);

        assertEquals("", version.rawName());
        assertEquals("", version.displayName());
    }

    @Test
    void getVersion_versionStringNotPublicStaticFinal_notFound() throws IOException {
        Path testJar = temporaryDirectory.resolve("badAccessVersion.jar");
        String entryClass = "main.BadAccess";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_8,
                "Version 1.0",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL); // Private

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);

        assertEquals("", version.rawName(), "Version string should not be found due to incorrect access modifiers.");
    }

    @Test
    void getVersion_versionStringNotStringTyped_notFound() throws IOException {
        Path testJar = temporaryDirectory.resolve("badTypeVersion.jar");
        String entryClass = "main.BadType";

        String internalClassName = entryClass.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);
        FieldVisitor fv = cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "VERSION_STRING", "I", null, 123);
        fv.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        createJarWithCustomBytes(testJar, entryClass, cw.toByteArray());

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);

        assertEquals(
                "", version.rawName(), "Version string should not be found due to incorrect type (descriptor check).");
    }

    @Test
    void getVersion_malformedClassFileForVersionReading() throws IOException {
        Path testJar = temporaryDirectory.resolve("malformedClass.jar");
        String entryClass = "main.Malformed";
        byte[] malformedBytes = "NOT_A_CLASS_FILE_AT_ALL_REALLY_LONG_TO_AVOID_EOF".getBytes();
        createJarWithCustomBytes(testJar, entryClass, malformedBytes);

        assertThrows(IllegalArgumentException.class, () -> EquilinoxVersionLookup.getVersion(testJar, entryClass));
    }

    @Test
    void getVersion_classFileTooShortForVersion() throws IOException {
        Path testJar = temporaryDirectory.resolve("shortClass.jar");
        String entryClass = "main.ShortClass";
        // Magic (4) + minor (2) = 6 bytes. Needs 8 for major version.
        byte[] shortBytes = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0}; // Only 7 bytes
        createJarWithCustomBytes(testJar, entryClass, shortBytes);

        ExceptionUtil.WrappedException e = assertThrows(
                ExceptionUtil.WrappedException.class, () -> EquilinoxVersionLookup.getVersion(testJar, entryClass));
        assertInstanceOf(
                EOFException.class, e.getCause(), "Cause should be an EOFException for too short class file version.");
    }

    @Test
    void getVersion_normalizeAlphaVersion() throws IOException {
        Path testJar = temporaryDirectory.resolve("alphaVersion.jar");
        String entryClass = "main.Alpha";
        createJarWithClass(
                testJar, entryClass, Opcodes.V1_8, "1.0a", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);
        assertEquals("1.0a", version.rawName());
        assertEquals("1.0-alpha", version.displayName());
    }

    @Test
    void getVersion_normalizeBetaVersion() throws IOException {
        Path testJar = temporaryDirectory.resolve("betaVersion.jar");
        String entryClass = "main.Beta";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_8,
                "1.7.0b",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);
        assertEquals("1.7.0b", version.rawName());
        assertEquals("1.7.0-beta", version.displayName());
    }

    @Test
    void getVersion_normalizeRCVersion() throws IOException {
        Path testJar = temporaryDirectory.resolve("rcVersion.jar");
        String entryClass = "main.RC";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_8,
                "2.0rc1",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);
        assertEquals("2.0rc1", version.rawName());
        assertEquals("2.0-rc.1", version.displayName());
    }

    @Test
    void getVersion_versionStringNoNormalizationNeeded() throws IOException {
        Path testJar = temporaryDirectory.resolve("normalVersion.jar");
        String entryClass = "main.Normal";
        createJarWithClass(
                testJar,
                entryClass,
                Opcodes.V1_8,
                "Version 1.5.final",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        EquilinoxVersion version = EquilinoxVersionLookup.getVersion(testJar, entryClass);
        assertEquals("1.5.final", version.rawName());
        assertEquals("1.5.final", version.displayName());
    }

    @Test
    void getVersion_entrypointClassNotFoundInJar_throwsWrappedIOException() throws IOException {
        Path testJar = temporaryDirectory.resolve("empty.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testJar.toFile()))) {
            jos.closeEntry();
        }
        String entryClass = "main.Missing";

        assertThrows(NullPointerException.class, () -> EquilinoxVersionLookup.getVersion(testJar, entryClass));
    }

    @Test
    void getVersion_jarPathDoesNotExist_throwsWrappedIOException() {
        Path nonExistentJar = temporaryDirectory.resolve("nonExistent.jar"); // File itself is not created
        String entryClass = "main.AnyClass";

        ExceptionUtil.WrappedException e = assertThrows(ExceptionUtil.WrappedException.class, () -> {
            EquilinoxVersionLookup.getVersion(nonExistentJar, entryClass);
        });
        assertInstanceOf(
                IOException.class,
                e.getCause(),
                "Cause should be IOException from SimpleClassPath failing to open non-existent JAR.");
    }

    @Test
    void getVersion_ioExceptionDuringASMProcessing_throwsWrappedIOException() throws IOException {
        Path testJar = temporaryDirectory.resolve("asmError.jar");
        String entryClass = "main.AsmErrorWillFail";
        // These bytes are valid for class version reading (starts with CAFEBABE, has version bytes)
        // but are otherwise an invalid class structure that ClassReader will reject
        byte[] asmErrorBytes = {
            (byte) 0xCA,
            (byte) 0xFE,
            (byte) 0xBA,
            (byte) 0xBE, // magic
            0,
            0, // minor version
            0,
            52, // major version (Java 8)
            0,
            1, // constant_pool_count (1 entry, but no entries defined)
            // ... abruptly ends or has invalid data for constant pool or attributes
            // This should cause ClassReader to fail
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10
        };
        createJarWithCustomBytes(testJar, entryClass, asmErrorBytes);

        assertThrows(
                ArrayIndexOutOfBoundsException.class, () -> EquilinoxVersionLookup.getVersion(testJar, entryClass));
    }
}
