package io.onedev.server.traceability.matrix;

/**
 * Estado de sincronización y salud de trazabilidad para un elemento o fila en la matriz.
 */
public enum TraceabilitySyncStatus {
    SYNCHRONIZED("Sincronizado", "badge-success"),
    PARTIAL("Parcial", "badge-warning"),
    DRIFT_UNLINKED("Desfase / Huérfano", "badge-danger");

    private final String displayName;
    private final String badgeClass;

    TraceabilitySyncStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}