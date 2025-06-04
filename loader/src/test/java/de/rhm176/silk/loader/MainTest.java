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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.SystemStubs;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class MainTest {

    private FileSystem jimfs;
    private Path cwd;

    @Mock
    private RuntimeMXBean mockRuntimeMXBean;

    @Mock
    private ProcessHandle mockProcessHandle;

    @Mock
    private ProcessHandle.Info mockProcessInfo;

    @Mock
    private Process mockProcess;

    private MockedStatic<ManagementFactory> staticManagementFactory;
    private MockedStatic<ProcessHandle> staticProcessHandle;
    private MockedStatic<Knot> staticKnot;
    private MockedStatic<Paths> staticPaths;
    private MockedStatic<Files> staticFiles;

    private Properties originalSystemProperties;

    @BeforeEach
    void setUp() throws IOException {
        originalSystemProperties = (Properties) System.getProperties().clone();

        jimfs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix")
                .build());
        cwd = jimfs.getPath("/work");
        Files.createDirectories(cwd);

        staticPaths = mockStatic(Paths.class, Mockito.CALLS_REAL_METHODS);

        staticPaths.when(() -> Paths.get(".")).thenReturn(cwd);

        staticPaths.when(() -> Paths.get(anyString(), ArgumentMatchers.<String>any())).thenAnswer(invocation -> {
            Object[] allArgs = invocation.getArguments();
            String firstArg = (String) allArgs[0];
            String[] moreArgs;

            if (allArgs.length == 1) {
                moreArgs = new String[0];
            } else if (allArgs[1] instanceof String[]) {
                moreArgs = (String[]) allArgs[1];
            } else {
                System.err.println("Unexpected args structure in Paths.get mock: " + Arrays.toString(allArgs));
                return invocation.callRealMethod();
            }

            if (firstArg.equals(".") && moreArgs.length == 0) return cwd;

            Path jimfsCwdRoot = cwd.getRoot();
            if (jimfsCwdRoot != null && firstArg.startsWith(jimfs.getSeparator())) {
                Path firstAsPathInJimfs;
                try {
                    firstAsPathInJimfs = jimfs.getPath(firstArg);
                } catch (InvalidPathException ipe) {
                    return invocation.callRealMethod();
                }

                if (firstAsPathInJimfs.getRoot() != null
                        && jimfsCwdRoot
                                .toString()
                                .equals(firstAsPathInJimfs.getRoot().toString())) {
                    if (firstArg.startsWith(cwd.toString()) || firstArg.equals(jimfsCwdRoot.toString())) {
                        try {
                            return jimfs.getPath(firstArg, moreArgs);
                        } catch (InvalidPathException e) {
                            /* Fall through */
                        }
                    }
                }
            }
            return (Path) invocation.callRealMethod();
        });

        staticPaths.when(() -> Paths.get(any(URI.class))).thenAnswer(InvocationOnMock::callRealMethod);

        staticManagementFactory = mockStatic(ManagementFactory.class);
        staticProcessHandle = mockStatic(ProcessHandle.class);
        staticKnot = mockStatic(Knot.class);

        lenient().when(ManagementFactory.getRuntimeMXBean()).thenReturn(mockRuntimeMXBean);
        lenient().when(ProcessHandle.current()).thenReturn(mockProcessHandle);
        lenient().when(mockProcessHandle.info()).thenReturn(mockProcessInfo);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (jimfs != null) {
            jimfs.close();
        }
        staticManagementFactory.close();
        staticProcessHandle.close();
        staticKnot.close();
        staticPaths.close();
        if (staticFiles != null) {
            staticFiles.close();
            staticFiles = null;
        }
        System.setProperties(originalSystemProperties);
    }

    private Path createDummyJar(String name, List<String> entries) throws IOException {
        Path jarPath = cwd.resolve(name);
        Files.createDirectories(jarPath.getParent());
        try (OutputStream os = Files.newOutputStream(jarPath);
                JarOutputStream jos = new JarOutputStream(os)) {
            if (entries != null) {
                for (String entry : entries) {
                    if (entry == null || entry.trim().isEmpty()) continue;
                    JarEntry jarEntry = new JarEntry(entry);
                    jos.putNextEntry(jarEntry);
                    if (!entry.endsWith("/")) {
                        jos.write(("content of " + entry).getBytes(StandardCharsets.UTF_8));
                    }
                    jos.closeEntry();
                }
            } else {
                JarEntry dummyEntry = new JarEntry("dummy.txt");
                jos.putNextEntry(dummyEntry);
                jos.write("dummy content".getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jarPath;
    }

    private void simulateRunningFromJimfsJar(String loaderJarNameInCwd) throws Exception {
        Path loaderJarPath = cwd.resolve(loaderJarNameInCwd);
        createDummyJar(loaderJarNameInCwd, List.of("silkloaderinternal/Dummy.class"));

        URI realMainClassUri =
                Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        staticPaths.when(() -> Paths.get(eq(realMainClassUri))).thenReturn(loaderJarPath);
    }

    @Test
    void findGameByName_noJars_currentJarNameNull() throws Exception {
        new SystemProperties().execute(() -> {
            assertEquals(Optional.empty(), Main.findGameByName());
        });
    }

    @Test
    void findGameByName_equilinoxJarPresent() throws Exception {
        createDummyJar("Equilinox-1.2.3.jar", null);
        simulateRunningFromJimfsJar("loader.jar");

        new SystemProperties().execute(() -> {
            Optional<Path> gameJar = Main.findGameByName();
            assertTrue(gameJar.isPresent(), "Game JAR should be found");
            assertEquals(
                    cwd.resolve("Equilinox-1.2.3.jar").toAbsolutePath().toString(),
                    gameJar.get().toAbsolutePath().toString());
        });
    }

    @Test
    void findGameByName_inputJarPresent() throws Exception {
        createDummyJar("input.jar", null);
        simulateRunningFromJimfsJar("loader.jar");
        new SystemProperties().execute(() -> {
            Optional<Path> gameJar = Main.findGameByName();
            assertTrue(gameJar.isPresent(), "Game JAR 'input.jar' should be found");
            assertEquals(
                    cwd.resolve("input.jar").toAbsolutePath().toString(),
                    gameJar.get().toAbsolutePath().toString());
        });
    }

    @Test
    void findGameByName_otherJarPresent_notFound() throws Exception {
        createDummyJar("someOtherGame.jar", null);
        simulateRunningFromJimfsJar("loader.jar");
        new SystemProperties().execute(() -> {
            assertEquals(Optional.empty(), Main.findGameByName());
        });
    }

    @Test
    void findGameByName_equilinoxJarIsCurrentJar_notFound() throws Exception {
        simulateRunningFromJimfsJar("Equilinox-game.jar");
        new SystemProperties().execute(() -> assertEquals(Optional.empty(), Main.findGameByName()));
    }

    @Test
    void findGameByClasses_noJars() throws Exception {
        new SystemProperties().execute(() -> assertEquals(Optional.empty(), Main.findGameByClasses()));
    }

    @Test
    void findGameByClasses_jarWithRequiredClasses() throws Exception {
        createDummyJar("game.jar", List.of("main/MainApp.class", "main/FirstScreenUi.class"));
        new SystemProperties().execute(() -> {
            Optional<Path> gameJar = Main.findGameByClasses();
            assertTrue(gameJar.isPresent(), "Game JAR with required classes should be found");
            assertEquals(
                    cwd.resolve("game.jar").toAbsolutePath().toString(),
                    gameJar.get().toAbsolutePath().toString());
        });
    }

    @Test
    void findGameByClasses_jarMissingOneClass() throws Exception {
        createDummyJar("incomplete.jar", List.of("main/MainApp.class"));
        new SystemProperties().execute(() -> {
            assertEquals(Optional.empty(), Main.findGameByClasses());
        });
    }

    @Test
    void isNativeFile_windows() throws Exception {
        Properties props = new Properties();
        props.put("os.name", "Windows 10");
        new SystemProperties(props).execute(() -> {
            assertTrue(Main.isNativeFile("test.dll"));
            assertFalse(Main.isNativeFile("test.so"));
        });
    }

    @Test
    void isNativeFile_linux() throws Exception {
        Properties props = new Properties();
        props.put("os.name", "Linux");
        new SystemProperties(props).execute(() -> {
            assertTrue(Main.isNativeFile("test.so"));
            assertFalse(Main.isNativeFile("test.dll"));
        });
    }

    @Test
    void isNativeFile_mac() throws Exception {
        Properties props = new Properties();
        props.put("os.name", "Mac OS X");
        new SystemProperties(props).execute(() -> {
            assertTrue(Main.isNativeFile("test.jnilib"));
            assertTrue(Main.isNativeFile("test.dylib"));
        });
    }

    @Test
    void extractNatives_extractsCorrectFiles() throws Exception {
        Path gameJar = createDummyJar(
                "gameWithNatives.jar",
                List.of("native.dll", "native.so", "native.jnilib", "data.txt", "some/path/native2.dll"));

        staticFiles = mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        Path nativesTempDir = cwd.resolve("native_temp_dir");
        Files.createDirectories(nativesTempDir);
        staticFiles.when(() -> Files.createTempDirectory(eq("natives"))).thenReturn(nativesTempDir);

        Properties props = new Properties();
        props.put("os.name", "Windows 10");
        new SystemProperties(props).execute(() -> {
            Path extractedDir = Main.extractNatives(gameJar);
            assertEquals(
                    nativesTempDir.toAbsolutePath().toString(),
                    extractedDir.toAbsolutePath().toString());
            assertTrue(Files.exists(extractedDir.resolve("native.dll")));
            assertFalse(Files.exists(extractedDir.resolve("native.so")));
            assertFalse(Files.exists(extractedDir.resolve("native.jnilib")));
            assertFalse(Files.exists(extractedDir.resolve("data.txt")));
            assertFalse(Files.exists(extractedDir.resolve("native2.dll")));
        });
    }

    private void setupMainTestMocks() {
        when(ManagementFactory.getRuntimeMXBean()).thenReturn(mockRuntimeMXBean);
        when(ProcessHandle.current()).thenReturn(mockProcessHandle);
        when(mockProcessHandle.info()).thenReturn(mockProcessInfo);

        when(mockRuntimeMXBean.getInputArguments()).thenReturn(Collections.emptyList());
        when(mockRuntimeMXBean.getClassPath())
                .thenReturn(cwd.resolve("dummycp.jar").toString());
        when(mockRuntimeMXBean.getSystemProperties()).thenReturn(Map.of("java.home", "/fake/java/home"));
        when(mockProcessInfo.command()).thenReturn(Optional.of("/fake/java/home/bin/java"));
    }

    @Test
    void main_gameJarPathAlreadySet_noRelaunch_knotLaunches() throws Exception {
        Path dummyGameJar = createDummyJar("mygame.jar", List.of("main/MainApp.class", "main/FirstScreenUi.class"));

        new EnvironmentVariables(Map.of("DISABLE_FORK", "true")).execute(() -> {
            Properties props = new Properties();
            props.put(
                    net.fabricmc.loader.impl.util.SystemProperties.GAME_JAR_PATH,
                    dummyGameJar.toAbsolutePath().toString());
            props.put("eqmodloader.loadedNatives", "true");

            new SystemProperties(props).execute(() -> {
                Main.main(new String[] {"--arg1", "val1"});
            });
        });

        staticKnot.verify(() -> Knot.launch(eq(new String[] {"--arg1", "val1"}), eq(EnvType.CLIENT)));
        staticManagementFactory.verify(ManagementFactory::getRuntimeMXBean, Mockito.never());
    }

    /* TODO:
    @Test
    void main_discoverySucceeds_relaunchLogic() throws Exception {
        setupMainTestMocks();
        Path discoveredGameJar = createDummyJar(
                "EquilinoxAdventures.jar", List.of("main/MainApp.class", "main/FirstScreenUi.class", "native.dll"));
        simulateRunningFromJimfsJar("silk-loader.jar");

        staticFiles = mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        Path nativesTempDir = cwd.resolve("silk_natives_temp");
        Files.createDirectories(nativesTempDir);
        staticFiles.when(() -> Files.createTempDirectory(eq("natives"))).thenReturn(nativesTempDir);

        try (MockedConstruction<ProcessBuilder> mockedBuilder =
                mockConstruction(ProcessBuilder.class, (mock, context) -> {
                    when(mock.start()).thenReturn(mockProcess);
                    when(mockProcess.waitFor()).thenReturn(0);
                })) {
            int exitCode = SystemStubs.catchSystemExit(() -> {
                Properties props = new Properties();
                props.put("os.name", "Windows 10");
                new SystemProperties(props).execute(() -> {
                    Main.main(new String[] {"--gameArg"});
                });
            });
            assertEquals(0, exitCode);

            ProcessBuilder constructedBuilder = mockedBuilder.constructed().get(0);
            List<String> command = constructedBuilder.command();

            assertTrue(command.contains("-Deqmodloader.loadedNatives=true"));
            assertTrue(command.stream()
                    .anyMatch(s -> s.startsWith(
                            "-Djava.library.path=" + nativesTempDir.toAbsolutePath() + File.pathSeparator)));
            assertTrue(command.contains("-D" + net.fabricmc.loader.impl.util.SystemProperties.GAME_JAR_PATH + "="
                    + discoveredGameJar.toAbsolutePath()));
            assertTrue(command.contains(Main.class.getName()));
            assertTrue(command.contains("--gameArg"));
        }
        new SystemProperties()
                .execute(() -> assertEquals(
                        discoveredGameJar.toAbsolutePath().toString(),
                        System.getProperty(net.fabricmc.loader.impl.util.SystemProperties.GAME_JAR_PATH)));
    }
     */

    @Test
    void main_discoveryFails_exitsWithError() throws Exception {
        simulateRunningFromJimfsJar("silk-loader.jar");

        String errText = SystemStubs.tapSystemErrAndOut(() -> {
            int exitCode =
                    SystemStubs.catchSystemExit(() -> new SystemProperties().execute(() -> Main.main(new String[] {})));
            assertEquals(1, exitCode);
        });

        assertTrue(errText.contains("[Silk]: Could not find the Equilinox jar."));
        new SystemProperties().execute(() -> {
            assertNull(System.getProperty(net.fabricmc.loader.impl.util.SystemProperties.GAME_JAR_PATH));
        });
    }

    /* TODO:
    @Test
    void main_relaunchFiltersJvmArgs() throws Exception {
        setupMainTestMocks();
        Path discoveredGameJar = createDummyJar(
                "Equilinox.jar", List.of("main/MainApp.class", "main/FirstScreenUi.class", "native.dll"));
        simulateRunningFromJimfsJar("silk-loader.jar");

        when(mockRuntimeMXBean.getInputArguments())
                .thenReturn(List.of(
                        "-Xmx1G",
                        "-Djava.library.path=/old/path",
                        "-Deqmodloader.loadedNatives=false",
                        "-javaagent:someagent.jar",
                        "-cp",
                        "oldcp.jar",
                        "-classpath",
                        "anotheroldcp.jar",
                        "-Duser.prop=test"));

        staticFiles = mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        Path nativesTempDir = cwd.resolve("silk_natives_temp_jvm_args");
        Files.createDirectories(nativesTempDir);
        staticFiles.when(() -> Files.createTempDirectory(eq("natives"))).thenReturn(nativesTempDir);

        try (MockedConstruction<ProcessBuilder> mockedBuilder =
                mockConstruction(ProcessBuilder.class, (mock, context) -> {
                    when(mock.start()).thenReturn(mockProcess);
                    when(mockProcess.waitFor()).thenReturn(0);
                })) {
            SystemStubs.catchSystemExit(() -> {
                Properties props = new Properties();
                props.put("os.name", "Windows 10");
                new SystemProperties(props).execute(() -> Main.main(new String[] {}));
            });

            ProcessBuilder pb = mockedBuilder.constructed().get(0);
            List<String> command = pb.command();

            assertTrue(command.contains("-Xmx1G"));
            assertTrue(command.contains("-Duser.prop=test"));
            assertFalse(command.stream().anyMatch(s -> s.equals("-Djava.library.path=/old/path")));
            assertFalse(command.stream().anyMatch(s -> s.equals("-Deqmodloader.loadedNatives=false")));
            assertFalse(command.stream().anyMatch(s -> s.startsWith("-javaagent:")));
            assertFalse(command.contains("oldcp.jar"));
            assertFalse(command.contains("anotheroldcp.jar"));

            assertTrue(command.stream()
                    .anyMatch(s -> s.startsWith("-Djava.library.path=" + nativesTempDir.toAbsolutePath())));
            assertTrue(command.contains("-Deqmodloader.loadedNatives=true"));

            long cpCount = command.stream().filter(s -> s.equals("-cp")).count();
            assertEquals(1, cpCount, "Should only contain one -cp argument for the new classpath");
            assertTrue(command.contains(cwd.resolve("dummycp.jar").toString()));
        }
    }
     */
}
