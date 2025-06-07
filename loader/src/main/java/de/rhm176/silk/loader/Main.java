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

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.SystemProperties;

// my god, I hate this
public final class Main {
    public static final Version VERSION;

    static {
        try {
            VERSION = Version.parse(Main.class.getPackage().getImplementationVersion());
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    // If the game can't be found by name, it will search all jars in the
    // cwd and if all the listed classes are found,
    // the jar is determined to be the game.
    private static final List<String> equilinoxClassFiles = List.of("main/MainApp.class", "main/FirstScreenUi.class");

    private static final List<String> JVM_ARG_BLACKLIST_PREFIXES = List.of(
            "-Djava.library.path=",
            "-Deqmodloader.loadedNatives",
            "-Xbootclasspath",
            "-javaagent",
            "-cp",
            "-classpath");

    @VisibleForTesting
    public static Optional<Path> findGameByName() {
        String currentJarName = null;
        try {
            Path runningJarPath = Paths.get(Main.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (runningJarPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                currentJarName = runningJarPath.getFileName().toString();
            }
        } catch (Exception e) {
            System.err.println("[Silk] Could not determine current running JAR name.");
            e.printStackTrace(System.err);
        }

        if (currentJarName != null) {
            final String finalCurrentJarName = currentJarName;
            try (Stream<Path> stream = Files.list(Paths.get("."))) {
                return stream.filter(p -> Files.isRegularFile(p)
                                && (p.getFileName().toString().startsWith("Equilinox")
                                        || p.getFileName().toString().equals("input.jar"))
                                && p.getFileName()
                                        .toString()
                                        .toLowerCase(Locale.ROOT)
                                        .endsWith(".jar")
                                && !p.getFileName().toString().equals(finalCurrentJarName))
                        .findFirst();
            } catch (Exception e) {
                System.err.println("[Silk] Error occurred while searching for game JAR in CWD.");
                e.printStackTrace(System.err);
            }
        }

        return Optional.empty();
    }

    public static Optional<Path> findGameByClasses() {
        try (Stream<Path> stream = Files.list(Paths.get("."))) {
            return stream.filter(Files::isRegularFile)
                    .filter(p ->
                            p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(jarPath -> {
                        try (FileSystem jarFs = FileSystems.newFileSystem(jarPath, Map.of())) {
                            for (String classEntry : equilinoxClassFiles) {
                                if (classEntry == null || classEntry.trim().isEmpty()) {
                                    System.err.println("[Silk] Encountered a null or empty class entry path.");
                                    continue;
                                }
                                Path pathInJar = jarFs.getPath(classEntry);

                                if (!Files.exists(pathInJar)) {
                                    return false;
                                }
                            }
                            return true;
                        } catch (IOException e) {
                            System.err.println("[Silk] IOException while checking JAR " + jarPath.getFileName()
                                    + " for entries: " + e.getMessage());
                        }
                        return false;
                    })
                    .findFirst();
        } catch (IOException e) {
            System.err.println("[Silk] Error occurred while searching for game JAR in CWD: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        return Optional.empty();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.SKIP_MC_PROVIDER, "true");

        if (!System.getProperties().containsKey(SystemProperties.GAME_JAR_PATH)) {
            findGameByName()
                    .or(Main::findGameByClasses)
                    .ifPresentOrElse(
                            (path) -> System.setProperty(
                                    SystemProperties.GAME_JAR_PATH,
                                    path.toAbsolutePath().toString()),
                            () -> {
                                System.err.println(
                                        "[Silk]: Could not find the Equilinox jar. Please set one manually using"
                                                + " the `-D" + SystemProperties.GAME_JAR_PATH
                                                + "=<...>` JVM Argument.");
                                System.exit(1);
                            });
        }
        System.out.println(
                "[Silk] Game was identified to be located at: " + System.getProperty(SystemProperties.GAME_JAR_PATH));

        if (System.getProperty("eqmodloader.loadedNatives") == null) {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            Path file = Paths.get(System.getProperty(SystemProperties.GAME_JAR_PATH));
            Path nativesDirectory = extractNatives(file);

            List<String> command = new ArrayList<>();
            command.add(ProcessHandle.current()
                    .info()
                    .command()
                    .orElse(Paths.get(bean.getSystemProperties().get("java.home"), "bin", "java")
                            .toAbsolutePath()
                            .toString()));

            command.addAll(bean.getInputArguments().stream()
                    .filter(i -> JVM_ARG_BLACKLIST_PREFIXES.stream().noneMatch(i::startsWith))
                    .toList());

            command.add("-cp");
            command.add(bean.getClassPath());

            command.add("-Deqmodloader.loadedNatives=true");
            command.add("-Djava.library.path=" + nativesDirectory.toAbsolutePath() + File.pathSeparator
                    + file.getParent().toAbsolutePath());
            command.add("-D" + SystemProperties.GAME_JAR_PATH + "=" + file.toAbsolutePath());
            command.add(Main.class.getName());

            command.addAll(Arrays.asList(args));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.redirectErrorStream(true);

            System.exit(builder.start().waitFor());
        }

        Knot.launch(args, EnvType.CLIENT);
    }

    public static Path extractNatives(Path gameJarPath) throws Exception {
        Path tempDir = Files.createTempDirectory("natives");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (Files.exists(tempDir)) {
                    try (Stream<Path> files = Files.walk(tempDir)) {
                        files.sorted(Comparator.reverseOrder()).forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                System.err.println(
                                        "[Silk] Failed to delete path in temp dir: " + path + " - " + e.getMessage());
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }));

        try (FileSystem jarFs = FileSystems.newFileSystem(gameJarPath, Map.of())) {
            for (Path rootDirInJar : jarFs.getRootDirectories()) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirInJar)) {
                    for (Path pathInJar : stream) {
                        if (Files.isRegularFile(pathInJar)) {
                            String entryName = pathInJar.getFileName().toString();
                            if (isNativeFile(entryName)) {
                                Path outputFile = tempDir.resolve(entryName);
                                Files.copy(pathInJar, outputFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
        }
        return tempDir;
    }

    public static boolean isNativeFile(String entryName) {
        String osName = System.getProperty("os.name");
        String name = entryName.toLowerCase(Locale.ROOT);
        if (osName.startsWith("Win")) {
            return name.endsWith(".dll");
        } else if (osName.startsWith("Linux")) {
            return name.endsWith(".so");
        } else
            return (osName.startsWith("Mac") || osName.startsWith("Darwin"))
                    && (name.endsWith(".jnilib") || name.endsWith(".dylib"));
    }
}
