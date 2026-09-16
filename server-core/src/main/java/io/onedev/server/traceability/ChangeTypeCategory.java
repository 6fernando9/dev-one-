package io.onedev.server.traceability;

/**
 * Categoría semántica del cambio introducido por un commit o pull request.
 */
public enum ChangeTypeCategory {

    FEATURE("Nueva Característica", "Introduce nueva funcionalidad o comportamiento"),
    FIX("Corrección de Error", "Soluciona un bug o defecto existente"),
    REFACTOR("Refactorización", "Modifica la estructura interna de código sin alterar su comportamiento"),
    DOCUMENTATION("Documentación", "Modifica documentación, wikis o diagramas"),
    DATA_MODEL("Modelo de Datos", "Modifica esquemas, migraciones o entidades de base de datos"),
    INFRASTRUCTURE("Infraestructura", "Modifica configuración de contenedores, CI/CD o despliegue"),
    CHORE("Mantenimiento", "Tareas rutinarias de mantenimiento o dependencias");

    private final String displayName;
    private final String description;

    ChangeTypeCategory(String displayName, String description) {
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