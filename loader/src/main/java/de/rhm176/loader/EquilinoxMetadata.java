package de.rhm176.loader;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.*;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EquilinoxMetadata implements ModMetadata {
    private final Version version;

    EquilinoxMetadata(@NotNull String version) {
        try {
            this.version = Version.parse(version);
        } catch(VersionParsingException e) {
            throw ExceptionUtil.wrap(new RuntimeException("Failed to parse version", e));
        }
    }

    @Override
    public String getType() {
        return AbstractModMetadata.TYPE_BUILTIN;
    }

    @Override
    public String getId() {
        return "equilinox";
    }

    @Override
    public Collection<String> getProvides() {
        return List.of();
    }

    @Override
    public Version getVersion() {
        return version;
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
        return "Equilinox";
    }

    @Override
    public String getDescription() {
        return "Equilinox Game";
    }

    @Override
    public Collection<Person> getAuthors() {
        return List.of();
    }

    @Override
    public Collection<Person> getContributors() {
        return List.of();
    }

    @Override
    public ContactInformation getContact() {
        return ContactInformation.EMPTY;
    }

    @Override
    public Collection<String> getLicense() {
        return List.of();
    }

    @Override
    public Optional<String> getIconPath(int size) {
        return Optional.empty();
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
