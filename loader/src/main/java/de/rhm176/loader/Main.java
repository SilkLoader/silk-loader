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
package de.rhm176.loader;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.SystemProperties;

// my god, I hate this
public final class Main {
    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.SKIP_MC_PROVIDER, "true");

        if (System.getProperty("eqmodloader.loadedNatives") == null) {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            File file = Paths.get(System.getProperty(SystemProperties.GAME_JAR_PATH))
                    .toRealPath()
                    .toFile();
            Path nativesDirectory = extractNatives(file);

            List<String> command = new ArrayList<>();
            command.add(Paths.get(bean.getSystemProperties().get("java.home"), "bin", "java")
                    .toAbsolutePath()
                    .toString());

            final List<String> blackListedArgs = List.of(
                    "-Djava.library.path=",
                    "-Deqmodloader.loadedNatives",
                    "-Xbootclasspath",
                    "-javaagent",
                    "-cp",
                    "-classpath");
            command.addAll(bean.getInputArguments().stream()
                    .filter(i -> blackListedArgs.stream().noneMatch(i::startsWith))
                    .toList());

            command.add("-cp");
            command.add(bean.getClassPath());

            command.add("-Deqmodloader.loadedNatives=true");
            command.add("-Djava.library.path=" + nativesDirectory.toAbsolutePath());
            command.add(Main.class.getName());

            command.addAll(Arrays.asList(args));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try {
                System.exit(process.waitFor());
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
                System.exit(-1);
            }
        }

        if (!System.getProperties().containsKey(SystemProperties.GAME_JAR_PATH)) {
            System.out.println(SystemProperties.GAME_JAR_PATH + " not set. Attempting to automatically "
                    + "detect Equilinox game JAR.");
            String currentJarName = null;
            try {
                Path runningJarPath = Paths.get(Main.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
                if (runningJarPath.getFileName().toString().toLowerCase().endsWith(".jar")) {
                    currentJarName = runningJarPath.getFileName().toString();
                }
            } catch (Exception e) {
                System.out.println("Could not determine current running JAR name.");
                e.printStackTrace();
            }

            Path cwd = Paths.get(".");
            if (currentJarName != null) {
                final String finalCurrentJarName = currentJarName;
                try (Stream<Path> stream = Files.list(cwd)) {
                    stream.filter(p -> Files.isRegularFile(p)
                                    && p.getFileName().toString().startsWith("Equilinox")
                                    && p.getFileName().toString().toLowerCase().endsWith(".jar")
                                    && !p.getFileName().toString().equals(finalCurrentJarName))
                            .findFirst()
                            .ifPresent(foundJar -> {
                                try {
                                    System.setProperty(
                                            SystemProperties.GAME_JAR_PATH,
                                            foundJar.toRealPath().toString());
                                    System.out.println("Found game at: " + foundJar.toRealPath());
                                } catch (Exception e) {
                                    System.out.println("Could not get real path for found JAR " + foundJar
                                            + " or set system property.");
                                    e.printStackTrace();
                                }
                            });
                } catch (Exception e) {
                    System.out.println("Error occurred while searching for game JAR in CWD.");
                    e.printStackTrace();
                }
            }
        }

        if (!System.getProperties().containsKey(SystemProperties.GAME_JAR_PATH)) {
            System.out.println("Could not find the Equilinox jar. Please set one manually using" + " the `-D"
                    + SystemProperties.GAME_JAR_PATH + "=<...>` JVM Argument.");
            System.exit(1);
        }

        Knot.launch(args, EnvType.CLIENT);
    }

    public static Path extractNatives(File file) throws Exception {
        File tempDir = Files.createTempDirectory("natives").toFile();
        tempDir.deleteOnExit();

        try (JarFile jarFile = new JarFile(file, false)) {
            Enumeration<JarEntry> entities = jarFile.entries();

            while (entities.hasMoreElements()) {
                JarEntry entry = entities.nextElement();
                if (!entry.isDirectory()
                        && !entry.getName().contains("/")
                        && !entry.getName().contains("\\")
                        && isNativeFile(entry.getName())) {
                    File outputFile = new File(tempDir, entry.getName());
                    try (InputStream in = jarFile.getInputStream(entry);
                            OutputStream out = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }

        return tempDir.toPath();
    }

    public static boolean isNativeFile(String entryName) {
        String osName = System.getProperty("os.name");
        String name = entryName.toLowerCase();
        if (osName.startsWith("Win")) {
            return name.endsWith(".dll");
        } else if (osName.startsWith("Linux")) {
            return name.endsWith(".so");
        } else
            return (osName.startsWith("Mac") || osName.startsWith("Darwin"))
                    && (name.endsWith(".jnilib") || name.endsWith(".dylib"));
    }
}
