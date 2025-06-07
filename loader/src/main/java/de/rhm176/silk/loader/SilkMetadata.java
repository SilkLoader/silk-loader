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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.*;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.metadata.ContactInformationImpl;

public class SilkMetadata implements ModMetadata {
    @Override
    public String getType() {
        return AbstractModMetadata.TYPE_BUILTIN;
    }

    @Override
    public String getId() {
        return "silkloader";
    }

    @Override
    public Collection<String> getProvides() {
        return List.of();
    }

    @Override
    public Version getVersion() {
        return Main.VERSION;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return ModEnvironment.UNIVERSAL;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return List.of();
    }

    @Override
    public String getName() {
        return "Silk Loader";
    }

    @Override
    public String getDescription() {
        return "GameProvider and Launcher for Equilinox.";
    }

    @Override
    public Collection<Person> getAuthors() {
        return List.of(new Person() {
            @Override
            public String getName() {
                return "Equilinox Modding Team";
            }

            @Override
            public ContactInformation getContact() {
                return null;
            }
        });
    }

    @Override
    public Collection<Person> getContributors() {
        return List.of();
    }

    @Override
    public ContactInformation getContact() {
        return new ContactInformationImpl(Map.of(
                "sources", "https://github.com/SilkLoader/silk-loader",
                "issues", "https://github.com/SilkLoader/silk-loader/issues"));
    }

    @Override
    public Collection<String> getLicense() {
        return List.of("Apache-2.0");
    }

    @Override
    public Optional<String> getIconPath(int size) {
        return Optional.of("assets/silkloader/icon.png");
    }

    @Override
    public boolean containsCustomValue(String key) {
        return false;
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return null;
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return Map.of();
    }

    @Override
    public boolean containsCustomElement(String key) {
        return false;
    }
}
