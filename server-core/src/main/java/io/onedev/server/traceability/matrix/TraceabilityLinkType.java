package io.onedev.server.traceability.matrix;

/**
 * Tipo semántico del enlace o relación de trazabilidad entre dos Elementos de Configuración.
 */
public enum TraceabilityLinkType {
    IMPLEMENTS("Implementa"),
    SPECIFIES("Especifica"),
    DECIDES("Decide arquitectónicamente"),
    MODELS("Modela datos"),
    DEPLOYS("Despliega infraestructura"),
    RELATED_TO("Relacionado");

    private final String displayName;

    TraceabilityLinkType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}