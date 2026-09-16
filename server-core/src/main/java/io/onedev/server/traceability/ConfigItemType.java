package io.onedev.server.traceability;

/**
 * Representa los elementos de configuración (ConfigItems) según el estándar de
 * trazabilidad integral y sincronización automática (RF1).
 */
public enum ConfigItemType {

    REQUIREMENT("Requisitos Funcionales", "list-ol", "Especificaciones de requisitos y matriz de trazabilidad (RTM)"),
    SOURCE_CODE("Código Fuente", "code", "Lógica de negocio y archivos de código fuente"),
    TEST_SPEC("Pruebas y Tests", "beaker", "Pruebas unitarias, de integración y especificaciones de prueba"),
    INFRASTRUCTURE_IAC("Infraestructura como Código", "server", "Plantillas de infraestructura como código, Docker y Kubernetes"),
    ADR("Decisiones Arquitectónicas", "file-text", "Architecture Decision Records bajo estándar docs/adr/"),
    DATA_MODEL_ERD("Bases de Datos y Esquemas", "database", "Esquemas de bases de datos, scripts SQL, migraciones y modelos ERD"),
    ARCHITECTURE_WIKI("Documentación de Arquitectura", "wiki", "Wikis y diagramas de arquitectura"),
    PROJECT_TASK("Plan de Proyecto", "task", "Tareas, tickets y elementos de trabajo en el plan");

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
