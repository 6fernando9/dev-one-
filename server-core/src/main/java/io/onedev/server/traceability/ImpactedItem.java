package io.onedev.server.traceability;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Representa un Elemento de Configuración secundario impactado por un cambio en el código,
 * junto con la justificación y la acción recomendada por la IA.
 */
public class ImpactedItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConfigItemType targetType;
    private final String suggestedPath;
    private final String reason;
    private final String suggestedAction;
    
    @Nullable
    private final String proposedContent;

    public ImpactedItem(ConfigItemType targetType, String suggestedPath, String reason, 
                        String suggestedAction, @Nullable String proposedContent) {
        this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null");
        this.suggestedPath = Objects.requireNonNull(suggestedPath, "suggestedPath cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.suggestedAction = Objects.requireNonNull(suggestedAction, "suggestedAction cannot be null");
        this.proposedContent = proposedContent;
    }

    public ImpactedItem(ConfigItemType targetType, String suggestedPath, String reason, String suggestedAction) {
        this(targetType, suggestedPath, reason, suggestedAction, null);
    }

    public ConfigItemType getTargetType() {
        return targetType;
    }

    public String getSuggestedPath() {
        return suggestedPath;
    }

    public String getReason() {
        return reason;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    @Nullable
    public String getProposedContent() {
        return proposedContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImpactedItem that = (ImpactedItem) o;
        return targetType == that.targetType && Objects.equals(suggestedPath, that.suggestedPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetType, suggestedPath);
    }

    @Override
    public String toString() {
        return String.format("[%s -> %s]: %s (%s)", targetType, suggestedPath, reason, suggestedAction);
    }
}