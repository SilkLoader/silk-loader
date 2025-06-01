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

/**
 * Represents the version information for Equilinox.
 * <p>
 * This record holds the display name of the version (e.g., "1.7.4") and
 * an optional Java class file version number (e.g., 52 for Java 8).
 *
 * @param rawName The raw name of the game version (e.g., "1.7.0b"). This should not be null.
 * @param displayName The display name of the game version (e.g., "1.7.0-beta"). This should not be null.
 * @param classVersion The Java class file major version number associated with this game version, if known.
 * This can be null if the class version is not determined or not applicable.
 * Common values: 52 (Java 8).
 */
public record EquilinoxVersion(String rawName, String displayName, Integer classVersion) {
    /**
     * Canonical constructor for the EquilinoxVersion record.
     *
     * @param rawName The raw name of the game version. Must not be null
     * @param displayName The display name of the game version. Must not be null.
     * @param classVersion The Java class file major version, or null if not applicable/unknown.
     */
    public EquilinoxVersion {}

    /**
     * Gets the raw name of the game version.
     *
     * @return The non-null raw name of the version.
     */
    @Override
    public String rawName() {
        return rawName;
    }

    /**
     * Gets the display name of the game version.
     *
     * @return The non-null display name of the version.
     */
    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Gets the Java class file major version number for this game version.
     *
     * @return The class version as an {@link Integer}, or {@code null} if not determined or applicable.
     */
    @Override
    public Integer classVersion() {
        return classVersion;
    }
}
