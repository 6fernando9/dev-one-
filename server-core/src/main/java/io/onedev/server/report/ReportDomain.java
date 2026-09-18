package io.onedev.server.report;

import java.util.Arrays;
import java.util.List;

/**
 * Enum de dominios consultables para informes dinámicos.
 * Cada dominio define las columnas disponibles que la IA puede solicitar.
 */
public enum ReportDomain {

    BRANCHES("Ramas", Arrays.asList("name", "lastCommitHash", "lastCommitAuthor", "lastCommitDate", "lastCommitMessage")),
    COMMITS("Commits", Arrays.asList("hash", "author", "date", "message", "filesChanged")),
    ISSUES("Issues / Problemas", Arrays.asList("number", "title", "state", "submitter", "assignees", "createDate", "updateDate", "priority")),
    PULL_REQUESTS("Pull Requests", Arrays.asList("number", "title", "status", "submitter", "targetBranch", "sourceBranch", "createDate", "updateDate")),
    CONFIG_ITEMS("Elementos de Configuración", Arrays.asList("path", "type", "identifier", "title")),
    TRACEABILITY_MATRIX("Matriz de Trazabilidad", Arrays.asList("requirementId", "title", "status", "sourceFiles", "adrs", "dataModels", "tasks", "notes")),
    USERS("Usuarios y Roles", Arrays.asList("name", "fullName", "type", "email")),
    AUDIT_EVENTS("Eventos de Auditoría", Arrays.asList("date", "severity", "action", "actor", "ipAddress", "project", "summary")),
    BUILDS("Builds / Compilaciones", Arrays.asList("number", "jobName", "status", "submitDate", "finishDate", "refName", "version"));

    private final String displayName;
    private final List<String> availableColumns;

    ReportDomain(String displayName, List<String> availableColumns) {
        this.displayName = displayName;
        this.availableColumns = availableColumns;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getAvailableColumns() {
        return availableColumns;
    }

    public static ReportDomain fromString(String value) {
        try {
            return valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (ReportDomain domain : values()) {
                if (domain.displayName.equalsIgnoreCase(value)) {
                    return domain;
                }
            }
            return COMMITS;
        }
    }
}
