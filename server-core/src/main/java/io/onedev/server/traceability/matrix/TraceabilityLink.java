package io.onedev.server.traceability.matrix;

import java.io.Serializable;
import java.util.Objects;
import io.onedev.server.traceability.ConfigItem;

/**
 * Representa una arista o enlace directo entre dos ConfigItems dentro del grafo de trazabilidad.
 */
public class TraceabilityLink implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ConfigItem source;
    private final ConfigItem target;
    private final TraceabilityLinkType linkType;
    private final String description;

    public TraceabilityLink(ConfigItem source, ConfigItem target, TraceabilityLinkType linkType, String description) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.linkType = Objects.requireNonNull(linkType, "linkType cannot be null");
        this.description = description != null ? description : "";
    }

    public ConfigItem getSource() {
        return source;
    }

    public ConfigItem getTarget() {
        return target;
    }

    public TraceabilityLinkType getLinkType() {
        return linkType;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceabilityLink that = (TraceabilityLink) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               linkType == that.linkType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, linkType);
    }

    @Override
    public String toString() {
        return String.format("%s --[%s]--> %s", source.getIdentifier(), linkType, target.getIdentifier());
    }
}