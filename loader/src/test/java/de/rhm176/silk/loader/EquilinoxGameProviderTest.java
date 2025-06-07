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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.SystemProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.SystemStubs;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class EquilinoxGameProviderTest {
    static class TestMainClass {
        public static volatile boolean mainCalled = false;
        public static volatile String[] mainArgs = null;

        public static void main(String[] args) {
            mainCalled = true;
            mainArgs = args;
        }

        public static void reset() {
            mainCalled = false;
            mainArgs = null;
        }
    }

    private EquilinoxGameProvider gameProvider;

    @Mock
    private FabricLauncher mockLauncher;

    @Mock
    private EquilinoxVersion mockEquilinoxVersion;

    private MockedStatic<EquilinoxVersionLookup> mockStaticVersionLookup;
    private MockedStatic<Paths> mockStaticPathsForErrorCases;

    @TempDir
    Path tempDir;

    private Path dummyGameJar;
    private final String[] defaultTestArgs = new String[] {"--testArg", "testValue"};

    @BeforeEach
    void setUp() throws IOException {
        gameProvider = new EquilinoxGameProvider();

        mockStaticVersionLookup = mockStatic(EquilinoxVersionLookup.class);
        mockStaticVersionLookup
                .when(() -> EquilinoxVersionLookup.getVersion(any(Path.class), eq("main.MainApp")))
                .thenReturn(mockEquilinoxVersion);

        dummyGameJar = tempDir.resolve("dummy-equilinox.jar");
        byte[] dummyMainAppClassBytes = new byte[] {
            (byte) 0xca,
            (byte) 0xfe,
            (byte) 0xba,
            (byte) 0xbe,
            0x00,
            0x00,
            0x00,
            0x34,
            0x00,
            0x0a,
            0x07,
            0x00,
            0x02,
            0x01,
            0x00,
            0x09,
            0x6d,
            0x61,
            0x69,
            0x6e,
            0x2f,
            0x4d,
            0x61,
            0x69,
            0x6e,
            0x41,
            0x70,
            0x70,
            0x07,
            0x00,
            0x04,
            0x01,
            0x00,
            0x10,
            0x6a,
            0x61,
            0x76,
            0x61,
            0x2f,
            0x6c,
            0x61,
            0x6e,
            0x67,
            0x2f,
            0x4f,
            0x62,
            0x6a,
            0x65,
            0x63,
            0x74,
            0x01,
            0x00,
            0x06,
            0x3c,
            0x69,
            0x6e,
            0x69,
            0x74,
            0x3e,
            0x01,
            0x00,
            0x03,
            0x28,
            0x29,
            0x56,
            0x01,
            0x00,
            0x04,
            0x43,
            0x6f,
            0x64,
            0x65,
            0x00,
            0x21,
            0x00,
            0x01,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x05,
            0x00,
            0x06,
            0x00,
            0x01,
            0x00,
            0x07,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x1d,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x05,
            0x2a,
            (byte) 0xb7,
            0x00,
            0x08,
            (byte) 0xb1,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00
        };
        try (OutputStream fos = Files.newOutputStream(dummyGameJar);
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry("main/MainApp.class");
            zos.putNextEntry(entry);
            zos.write(dummyMainAppClassBytes);
            zos.closeEntry();
        }
    }

    @AfterEach
    void tearDown() {
        mockStaticVersionLookup.close();
        if (mockStaticPathsForErrorCases != null) {
            mockStaticPathsForErrorCases.close();
        }
        TestMainClass.reset();
    }

    private void prepareGameProviderForLaunch(String[] args) throws Exception {
        SystemStubs.restoreSystemProperties(() -> {
            System.setProperty(SystemProperties.GAME_JAR_PATH, dummyGameJar.toString());
            assertTrue(gameProvider.locateGame(mockLauncher, args), "locateGame should return true on success");
        });
    }

    private void prepareGameProviderForLaunch() throws Exception {
        prepareGameProviderForLaunch(defaultTestArgs);
    }

    private void setGameProviderField(String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = EquilinoxGameProvider.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(gameProvider, value);
    }

    @Test
    void getGameId_returnsCorrectId() {
        assertEquals("equilinox", gameProvider.getGameId());
    }

    @Test
    void getGameName_returnsCorrectName() {
        assertEquals("Equilinox", gameProvider.getGameName());
    }

    @Test
    void isObfuscated_returnsFalse() {
        assertFalse(gameProvider.isObfuscated());
    }

    @Test
    void requiresUrlClassLoader_returnsFalse() {
        assertFalse(gameProvider.requiresUrlClassLoader());
    }

    @Test
    void isEnabled_returnsTrue() {
        assertTrue(gameProvider.isEnabled());
    }

    @Test
    void locateGame_successfullyLocatesGame() throws Exception {
        when(mockEquilinoxVersion.rawName()).thenReturn("0.0.0-test");
        when(mockEquilinoxVersion.displayName()).thenReturn("Test Equilinox v0");

        String[] customArgs = {"--custom", "val"};
        prepareGameProviderForLaunch(customArgs);

        assertEquals("main.MainApp", gameProvider.getEntrypoint());
        assertNotNull(gameProvider.getArguments());
        assertArrayEquals(customArgs, gameProvider.getArguments().toArray());
        assertEquals("0.0.0-test", gameProvider.getRawGameVersion());
        assertEquals("Test Equilinox v0", gameProvider.getNormalizedGameVersion());
    }

    @Test
    void locateGame_throwsNullPointerException_whenGameJarPathPropertyNotSet() throws Exception {
        SystemStubs.restoreSystemProperties(() -> {
            System.clearProperty(SystemProperties.GAME_JAR_PATH);
            assertThrows(
                    NullPointerException.class,
                    () -> gameProvider.locateGame(mockLauncher, defaultTestArgs),
                    "Expected locateGame to throw NullPointerException when game jar path is not set.");
        });
    }

    @Test
    void locateGame_throwsRuntimeException_whenGameJarPathIsInvalid() throws Exception {
        Path nonExistentJar = tempDir.resolve("non-existent.jar");
        SystemStubs.restoreSystemProperties(() -> {
            System.setProperty(SystemProperties.GAME_JAR_PATH, nonExistentJar.toString());
            RuntimeException thrown =
                    assertThrows(RuntimeException.class, () -> gameProvider.locateGame(mockLauncher, defaultTestArgs));
            assertEquals("Failed to find base", thrown.getMessage());
            assertInstanceOf(
                    IOException.class, thrown.getCause(), "Cause should be IOException for non-existent real path.");
        });
    }

    @Test
    void getRawGameVersion_returnsVersionAfterLocate() throws Exception {
        when(mockEquilinoxVersion.rawName()).thenReturn("0.0.0-test");
        prepareGameProviderForLaunch();
        assertEquals("0.0.0-test", gameProvider.getRawGameVersion());
    }

    @Test
    void getNormalizedGameVersion_returnsVersionAfterLocate() throws Exception {
        when(mockEquilinoxVersion.displayName()).thenReturn("Test Equilinox v0");
        prepareGameProviderForLaunch();
        assertEquals("Test Equilinox v0", gameProvider.getNormalizedGameVersion());
    }

    @Test
    void getEntrypoint_returnsEntrypointAfterLocate() throws Exception {
        prepareGameProviderForLaunch();
        assertEquals("main.MainApp", gameProvider.getEntrypoint());
    }

    @Test
    void getArguments_returnsArgsAfterLocate() throws Exception {
        prepareGameProviderForLaunch();
        assertArrayEquals(defaultTestArgs, gameProvider.getArguments().toArray());
    }

    @Test
    void getLaunchArguments_returnsArgsAfterLocate() throws Exception {
        prepareGameProviderForLaunch();
        assertArrayEquals(defaultTestArgs, gameProvider.getLaunchArguments(false));
        assertArrayEquals(defaultTestArgs, gameProvider.getLaunchArguments(true));
    }

    @Test
    void getBuiltinMods_returnsCorrectMod() throws Exception {
        when(mockEquilinoxVersion.displayName()).thenReturn("Test Equilinox v0");
        prepareGameProviderForLaunch();
        Collection<GameProvider.BuiltinMod> builtinMods = gameProvider.getBuiltinMods();
        assertNotNull(builtinMods);
        assertEquals(2, builtinMods.size());
        GameProvider.BuiltinMod mod = builtinMods.iterator().next();
        assertNotNull(mod.metadata);
        assertEquals("equilinox", mod.metadata.getId());
        assertEquals("Test Equilinox v0", mod.metadata.getVersion().getFriendlyString());
    }

    @Test
    void getLaunchDirectory_returnsCurrentDirectory() throws IOException {
        Path expectedPath = Paths.get(".").toRealPath();
        assertEquals(expectedPath, gameProvider.getLaunchDirectory());
    }

    @Test
    void getLaunchDirectory_wrapsIOException() throws Exception {
        mockStaticPathsForErrorCases = mockStatic(Paths.class, Mockito.RETURNS_DEEP_STUBS);
        Path mockPath = mock(Path.class);
        when(Paths.get(".")).thenReturn(mockPath);
        IOException causeException = new IOException("Test exception for toRealPath");
        when(mockPath.toRealPath()).thenThrow(causeException);

        RuntimeException thrown = assertThrows(RuntimeException.class, gameProvider::getLaunchDirectory);
        assertEquals("Failed to resolve launch dir", thrown.getMessage());
        assertEquals(causeException, thrown.getCause());
    }

    @Test
    void initialize_wrapsIOException_whenResolvingSystemClasspathEntry() throws Exception {
        prepareGameProviderForLaunch();
        Path nonExistentPath = tempDir.resolve("non-existent-sys-cp.jar");
        String systemClasspath = nonExistentPath.toString();

        SystemStubs.restoreSystemProperties(() -> {
            System.setProperty("java.class.path", systemClasspath);
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> gameProvider.initialize(mockLauncher));
            assertEquals("Failed to get real path of " + nonExistentPath, thrown.getMessage());
            assertInstanceOf(IOException.class, thrown.getCause());
        });
    }

    @Test
    void getEntrypointTransformer_returnsTransformer() {
        assertNotNull(gameProvider.getEntrypointTransformer());
        assertInstanceOf(GameTransformer.class, gameProvider.getEntrypointTransformer());
    }

    @Test
    void unlockClassPath_addsToLauncherClasspath() throws Exception {
        prepareGameProviderForLaunch();
        Path gameJarRealPath = dummyGameJar.toRealPath();
        Path codeSourcePath = Paths.get(EquilinoxGameProvider.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .toRealPath();

        gameProvider.unlockClassPath(mockLauncher);

        verify(mockLauncher).addToClassPath(gameJarRealPath);
        verify(mockLauncher).addToClassPath(codeSourcePath);
        verify(mockLauncher, times(2)).addToClassPath(any(Path.class));
    }

    @Test
    void launch_successfullyInvokesMain() throws Throwable {
        prepareGameProviderForLaunch();
        setGameProviderField("entryClass", TestMainClass.class.getName());

        TestMainClass.reset();
        assertFalse(TestMainClass.mainCalled);
        gameProvider.launch(this.getClass().getClassLoader());
        assertTrue(TestMainClass.mainCalled);
        assertArrayEquals(defaultTestArgs, TestMainClass.mainArgs);
    }

    @Test
    void launch_wrapsClassNotFoundException() throws Throwable {
        prepareGameProviderForLaunch();
        setGameProviderField("entryClass", "non.existent.class.ForTest");
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> gameProvider.launch(this.getClass().getClassLoader()));
        assertEquals("Failed to find entry point", thrown.getMessage());
        assertInstanceOf(ClassNotFoundException.class, thrown.getCause());
    }

    static class NoMainMethodClass {}

    @Test
    void launch_wrapsNoSuchMethodException() throws Throwable {
        prepareGameProviderForLaunch();
        setGameProviderField("entryClass", NoMainMethodClass.class.getName());
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> gameProvider.launch(this.getClass().getClassLoader()));
        assertEquals("Failed to find entry point", thrown.getMessage());
        assertInstanceOf(NoSuchMethodException.class, thrown.getCause());
    }

    static class PrivateMainMethodClass {
        @SuppressWarnings("ConfusingMainMethod")
        private static void main(String[] args) {}
    }

    @Test
    void launch_wrapsIllegalAccessException() throws Throwable {
        prepareGameProviderForLaunch();
        setGameProviderField("entryClass", PrivateMainMethodClass.class.getName());
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> gameProvider.launch(this.getClass().getClassLoader()));
        assertEquals("Failed to find entry point", thrown.getMessage());
        assertInstanceOf(IllegalAccessException.class, thrown.getCause());
    }

    static class ThrowingMainMethodClass {
        public static void main(String[] args) {
            throw new IllegalStateException("Test Boom");
        }
    }

    @Test
    void launch_wrapsExceptionFromMainMethod() throws Throwable {
        prepareGameProviderForLaunch();
        setGameProviderField("entryClass", ThrowingMainMethodClass.class.getName());
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> gameProvider.launch(this.getClass().getClassLoader()));
        assertEquals("Failed to launch", thrown.getMessage());
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertEquals("Test Boom", thrown.getCause().getMessage());
    }
}
