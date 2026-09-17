package io.onedev.server.traceability;

/**
 * Representa los 7 elementos de configuración (ConfigItems) según el estándar de
 * trazabilidad integral y sincronización automática.
 */
public enum ConfigItemType {

    SOURCE_CODE("Código Fuente", "code", "Lógica de negocio y archivos de código fuente"),
    ARCHITECTURE_WIKI("Documentación de Arquitectura", "wiki", "Wikis y diagramas de arquitectura"),
    DATA_MODEL_ERD("Modelo de Datos (ERD)", "database", "Esquemas de bases de datos, migraciones y diagramas entidad-relación"),
    REQUIREMENT("Requisito Funcional", "list-ol", "Especificaciones de requisitos y matriz de trazabilidad (RTM)"),
    PROJECT_TASK("Plan de Proyecto", "task", "Tareas, tickets y elementos de trabajo en el plan"),
    INFRASTRUCTURE_IAC("Infraestructura (IaC)", "server", "Plantillas de infraestructura como código, Docker y Kubernetes"),
    ADR("Decisión Técnica (ADR)", "file-text", "Architecture Decision Records bajo estándar docs/adr/");

    private final String displayName;
    private final String icon;
    private final String description;

    ConfigItemType(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}