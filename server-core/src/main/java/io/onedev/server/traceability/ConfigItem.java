package io.onedev.server.traceability;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Representa una instancia concreta de un Elemento de Configuración identificado.
 */
public class ConfigItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConfigItemType type;
    private final String identifier;
    private final String path;
    private final String title;
    
    @Nullable
    private final String hash;

    public ConfigItem(ConfigItemType type, String identifier, String path, String title, @Nullable String hash) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.identifier = Objects.requireNonNull(identifier, "identifier cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.hash = hash;
    }

    public ConfigItem(ConfigItemType type, String identifier, String path, String title) {
        this(type, identifier, path, title, null);
    }

    public ConfigItemType getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getPath() {
        return path;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigItem that = (ConfigItem) o;
        return type == that.type && Objects.equals(identifier, that.identifier) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifier, path);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", type, identifier, path);
    }
}