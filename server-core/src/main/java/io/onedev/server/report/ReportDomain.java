package io.onedev.server.report;

import static io.onedev.server.web.translation.Translation._T;

import java.util.Arrays;
import java.util.List;

/**
 * Enum de dominios consultables para informes dinámicos.
 * Cada dominio define las columnas disponibles y soporte de traducción dinámica (_T).
 */
public enum ReportDomain {

    CONFIG_ITEMS("Configuration Items", Arrays.asList("identifier", "title", "type", "path")),
    TRACEABILITY_MATRIX("Traceability Matrix", Arrays.asList("requirementId", "title", "status", "sourceFiles", "adrs", "dataModels", "tasks", "notes")),
    BRANCHES("Branches", Arrays.asList("name", "lastCommitHash", "lastCommitAuthor", "lastCommitDate", "lastCommitMessage")),
    COMMITS("Commits", Arrays.asList("hash", "author", "date", "message", "filesChanged")),
    ISSUES("Issues", Arrays.asList("number", "title", "state", "submitter", "assignees", "createDate", "updateDate", "priority")),
    PULL_REQUESTS("Pull Requests", Arrays.asList("number", "title", "status", "submitter", "targetBranch", "sourceBranch", "createDate", "updateDate")),
    USERS("Users and Roles", Arrays.asList("name", "fullName", "type", "email")),
    AUDIT_EVENTS("Audit Events", Arrays.asList("date", "severity", "action", "actor", "ipAddress", "project", "summary")),
    BUILDS("Builds and CI/CD", Arrays.asList("number", "jobName", "status", "submitDate", "finishDate", "refName", "version"));

    private final String displayNameKey;
    private final List<String> availableColumns;

    ReportDomain(String displayNameKey, List<String> availableColumns) {
        this.displayNameKey = displayNameKey;
        this.availableColumns = availableColumns;
    }

    public String getDisplayName() {
        return _T(displayNameKey);
    }

    public String getRawDisplayName() {
        return displayNameKey;
    }

    public List<String> getAvailableColumns() {
        return availableColumns;
    }

    public static ReportDomain fromString(String value) {
        if (value == null) return CONFIG_ITEMS;
        String val = value.trim();
        try {
            return valueOf(val.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (ReportDomain domain : values()) {
                if (domain.displayNameKey.equalsIgnoreCase(val) || 
                    domain.getDisplayName().equalsIgnoreCase(val) ||
                    domain.name().equalsIgnoreCase(val)) {
                    return domain;
                }
            }
            String lower = val.toLowerCase();
            if (lower.contains("elemento") || lower.contains("config") || lower.contains("requisito") || lower.contains("ci")) return CONFIG_ITEMS;
            if (lower.contains("trazab") || lower.contains("rtm") || lower.contains("matriz")) return TRACEABILITY_MATRIX;
            if (lower.contains("rama") || lower.contains("branch")) return BRANCHES;
            if (lower.contains("commit")) return COMMITS;
            if (lower.contains("issue") || lower.contains("problema") || lower.contains("tarea")) return ISSUES;
            if (lower.contains("pull") || lower.contains("solicitud") || lower.matches(".*\\bprs?\\b.*")) return PULL_REQUESTS;
            if (lower.contains("user") || lower.contains("usuario") || lower.contains("rol")) return USERS;
            if (lower.contains("audit") || lower.contains("auditor") || lower.contains("log")) return AUDIT_EVENTS;
            if (lower.contains("build") || lower.contains("compilac") || lower.contains("pipeline")) return BUILDS;
            return CONFIG_ITEMS;
        }
    }
}
