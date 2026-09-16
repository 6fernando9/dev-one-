package io.onedev.server.traceability.matrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.onedev.server.traceability.ConfigItem;

/**
 * Fila en la matriz de trazabilidad que agrupa un elemento primario
 * (usualmente un REQUIREMENT o código huérfano) con todas sus dependencias relacionadas.
 */
public class TraceabilityRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ConfigItem primaryItem;
    private final List<ConfigItem> tasks;
    private final List<ConfigItem> sourceFiles;
    private final List<ConfigItem> adrs;
    private final List<ConfigItem> dataModels;
    private final List<ConfigItem> architectureDocs;
    private final List<ConfigItem> infrastructure;
    private final TraceabilitySyncStatus status;
    private final String notes;

    public TraceabilityRow(ConfigItem primaryItem,
                           List<ConfigItem> tasks,
                           List<ConfigItem> sourceFiles,
                           List<ConfigItem> adrs,
                           List<ConfigItem> dataModels,
                           List<ConfigItem> architectureDocs,
                           List<ConfigItem> infrastructure,
                           TraceabilitySyncStatus status,
                           String notes) {
        this.primaryItem = Objects.requireNonNull(primaryItem, "primaryItem cannot be null");
        this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        this.sourceFiles = sourceFiles != null ? new ArrayList<>(sourceFiles) : new ArrayList<>();
        this.adrs = adrs != null ? new ArrayList<>(adrs) : new ArrayList<>();
        this.dataModels = dataModels != null ? new ArrayList<>(dataModels) : new ArrayList<>();
        this.architectureDocs = architectureDocs != null ? new ArrayList<>(architectureDocs) : new ArrayList<>();
        this.infrastructure = infrastructure != null ? new ArrayList<>(infrastructure) : new ArrayList<>();
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.notes = notes != null ? notes : "";
    }

    public ConfigItem getPrimaryItem() {
        return primaryItem;
    }

    public List<ConfigItem> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public List<ConfigItem> getSourceFiles() {
        return Collections.unmodifiableList(sourceFiles);
    }

    public List<ConfigItem> getAdrs() {
        return Collections.unmodifiableList(adrs);
    }

    public List<ConfigItem> getDataModels() {
        return Collections.unmodifiableList(dataModels);
    }

    public List<ConfigItem> getArchitectureDocs() {
        return Collections.unmodifiableList(architectureDocs);
    }

    public List<ConfigItem> getInfrastructure() {
        return Collections.unmodifiableList(infrastructure);
    }

    public TraceabilitySyncStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public boolean hasSourceCode() {
        return !sourceFiles.isEmpty();
    }

    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    public boolean hasAdrs() {
        return !adrs.isEmpty();
    }

    public boolean hasDataModels() {
        return !dataModels.isEmpty();
    }

    public boolean hasArchitectureDocs() {
        return !architectureDocs.isEmpty();
    }

    public boolean hasInfrastructure() {
        return !infrastructure.isEmpty();
    }
}