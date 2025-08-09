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

import de.rhm176.silk.loader.patch.ModInitPatch;
import de.rhm176.silk.loader.patch.WindowTitlePatch;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.SystemProperties;

public class EquilinoxGameProvider implements GameProvider {
    private final GameTransformer transformer = new GameTransformer(new WindowTitlePatch(this), new ModInitPatch());
    private final Set<String> gameClasses;

    private List<Path> classPath;

    private Arguments arguments;

    private String entryClass;
    private EquilinoxVersion version;

    public EquilinoxGameProvider() {
        gameClasses = new HashSet<>();
        String gameJar = System.getProperty(SystemProperties.GAME_JAR_PATH);

        if (gameJar != null) {
            try (JarFile jarFile = new JarFile(System.getProperty(SystemProperties.GAME_JAR_PATH))) {
                jarFile.stream()
                        .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                        .forEach(entry -> {
                            String className = entry.getName()
                                    .replace('/', '.')
                                    .replace('\\', '.')
                                    .replace(".class", "");
                            gameClasses.add(className);
                        });
            } catch (IOException e) {
                throw ExceptionUtil.wrap(new RuntimeException("Failed to collect Equilinox game classes", e));
            }
        }
    }

    @Override
    public String getGameId() {
        return "equilinox";
    }

    @Override
    public String getGameName() {
        return "Equilinox";
    }

    @Override
    public String getRawGameVersion() {
        return version.rawName();
    }

    @Override
    public String getNormalizedGameVersion() {
        return version.displayName();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        return List.of(
                new BuiltinMod(classPath, new EquilinoxMetadata(getNormalizedGameVersion())),
                new BuiltinMod(classPath, new SilkMetadata()));
    }

    @Override
    public String getEntrypoint() {
        return entryClass;
    }

    @Override
    public Path getLaunchDirectory() {
        try {
            return Paths.get(".").toRealPath();
        } catch (IOException e) {
            throw ExceptionUtil.wrap(new RuntimeException("Failed to resolve launch dir", e));
        }
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher fabricLauncher, String[] args) {
        this.arguments = new Arguments();
        arguments.parse(args);

        var codeSource = EquilinoxGameProvider.class.getProtectionDomain().getCodeSource();
        Path codePath;
        try {
            codePath = Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw ExceptionUtil.wrap(
                    new RuntimeException("Failed to find source of " + EquilinoxGameProvider.class.getName() + "?", e));
        }

        Path basePath;
        try {
            basePath = Paths.get(System.getProperty(SystemProperties.GAME_JAR_PATH))
                    .toRealPath();
        } catch (IOException e) {
            throw ExceptionUtil.wrap(new RuntimeException("Failed to find base", e));
        }

        entryClass = "main.MainApp";
        version = EquilinoxVersionLookup.getVersion(basePath, getEntrypoint());
        classPath = List.of(codePath, basePath);

        return true;
    }

    @Override
    public void initialize(FabricLauncher fabricLauncher) {
        var parentClassPath = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .map((path) -> {
                    try {
                        return path.toRealPath();
                    } catch (IOException e) {
                        throw ExceptionUtil.wrap(new RuntimeException("Failed to get real path of " + path, e));
                    }
                })
                .filter((path) -> !classPath.contains(path))
                .toList();

        fabricLauncher.setValidParentClassPath(parentClassPath);

        transformer.locateEntrypoints(fabricLauncher, classPath);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher fabricLauncher) {
        classPath.forEach(fabricLauncher::addToClassPath);
    }

    @Override
    public void launch(ClassLoader classLoader) {
        MethodHandle invoker;
        try {
            Class<?> target = classLoader.loadClass(getEntrypoint());
            invoker = MethodHandles.lookup()
                    .findStatic(target, "main", MethodType.methodType(void.class, String[].class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw ExceptionUtil.wrap(new RuntimeException("Failed to find entry point", e));
        }

        try {
            //noinspection ConfusingArgumentToVarargsMethod
            invoker.invokeExact(arguments.toArray());
        } catch (Throwable e) {
            throw ExceptionUtil.wrap(new RuntimeException("Failed to launch", e));
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        // There are no sensitive arguments, so I can just pass it as an array without removing any.
        return arguments.toArray();
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        return gameClasses.contains(className) ? Set.of(BuiltinTransform.CLASS_TWEAKS) : Set.of();
    }
}
