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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SimpleClassPath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Utility class to look up the version information of Equilinox.
 * <p>
 * This class attempts to determine the game's display version string and
 * its Java class file version from the game's JAR file and a specified entrypoint class.
 * It reads the class file version directly from the class file's header and
 * attempts to find a specific static final String field (conventionally {@code VERSION_STRING})
 * within the entrypoint class to get the display version.
 */
public final class EquilinoxVersionLookup {
    private EquilinoxVersionLookup() {}

    // not sure if the first two are actually needed but adding them can't hurt.
    private static final Map<String, String> VERSION_NORMALIZER = Map.of(
            "^([\\w.-]+)rc(\\d+)$", "$1-rc.$2",
            "^([\\w.-]+)a$", "$1-alpha",
            "^([\\w.-]+)b$", "$1-beta");

    /**
     * Retrieves the version information from the specified Equilinox game JAR.
     * <p>
     * It reads the Java class file version from the header of the {@code entrypointClass}.
     * It also scans the fields of the {@code entrypointClass} for a
     * {@code public static final String VERSION_STRING} field to determine the game's display version.
     *
     * @param equilinoxJar    The {@link Path} to the Equilinox game JAR file. Must not be null.
     * @param entrypointClass The fully qualified name of a class within the game JAR
     * (e.g., {@code main.MainApp}) that is expected to contain
     * version information or from which the class version can be read. Must not be null.
     * @return An {@link EquilinoxVersion} object containing the determined version name and class version.
     * The version name might be an empty string if not found. The class version might be null if reading fails.
     * @throws ExceptionUtil.WrappedException if an {@link IOException} occurs while reading the JAR or class files.
     */
    public static EquilinoxVersion getVersion(Path equilinoxJar, String entrypointClass) {
        Integer classPathVersion = null;
        final String[] version = {""};

        try (SimpleClassPath cp = new SimpleClassPath(List.of(equilinoxJar))) {
            try (InputStream is = cp.getInputStream(LoaderUtil.getClassFileName(entrypointClass))) {
                DataInputStream dis = new DataInputStream(is);

                if (dis.readInt() == 0xCAFEBABE) {
                    dis.readUnsignedShort();
                    classPathVersion = dis.readUnsignedShort();
                }
            }

            try (InputStream is = cp.getInputStream(LoaderUtil.getClassFileName(entrypointClass))) {
                ClassReader classReader = new ClassReader(is);
                classReader.accept(
                        new ClassVisitor(FabricLoaderImpl.ASM_VERSION) {
                            @Override
                            public FieldVisitor visitField(
                                    int access, String name, String descriptor, String signature, Object value) {
                                boolean isPublic = (access & Opcodes.ACC_PUBLIC) != 0;
                                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                                boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;

                                if (isPublic
                                        && isStatic
                                        && isFinal
                                        && "VERSION_STRING".equals(name)
                                        && "Ljava/lang/String;".equals(descriptor)) {

                                    if (value instanceof String) {
                                        version[0] = ((String) value).replaceFirst("Version ", "");
                                    }
                                }
                                return super.visitField(access, name, descriptor, signature, value);
                            }
                        },
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            }
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        String rawVersionString = version[0];
        String displayVersionString = rawVersionString;

        for (String pattern : VERSION_NORMALIZER.keySet()) {
            if (rawVersionString.matches(pattern)) {
                displayVersionString = displayVersionString.replaceAll(pattern, VERSION_NORMALIZER.get(pattern));
                break;
            }
        }

        return new EquilinoxVersion(rawVersionString, displayVersionString, classPathVersion);
    }
}
