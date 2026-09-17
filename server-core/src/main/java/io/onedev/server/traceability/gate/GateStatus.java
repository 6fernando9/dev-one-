package io.onedev.server.traceability.gate;

/**
 * Estado de evaluación de la puerta de bloqueo de despliegues (Deployment Gate).
 */
public enum GateStatus {
    PASSED("Aprobado", "El proyecto cumple con todas las políticas de calidad y trazabilidad requeridas para el despliegue seguro."),
    BLOCKED("Bloqueado", "El despliegue ha sido denegado debido a inconsistencias críticas, deriva o cobertura insuficiente."),
    WARNING("Advertencia", "Se detectaron desfases menores pero el despliegue está permitido en modo no bloqueante.");

    private final String displayName;
    private final String description;

    GateStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
