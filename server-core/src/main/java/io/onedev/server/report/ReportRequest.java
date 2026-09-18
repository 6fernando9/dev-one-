package io.onedev.server.report;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Representa una petición de reporte parseada del JSON que genera la IA.
 * Nunca contiene SQL directo — solo referencias declarativas a dominios y filtros.
 */
public class ReportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum ExportFormat {
        CSV, PDF, EXCEL
    }

    private ReportDomain domain;
    private Map<String, String> filters;
    private @Nullable String groupBy;
    private @Nullable List<String> columns;
    private ExportFormat format;
    private String title;
    private @Nullable String description;
    private int maxResults = 500;

    public ReportRequest() {
        this.filters = new HashMap<>();
        this.format = ExportFormat.CSV;
        this.title = "Informe";
    }

    public ReportDomain getDomain() {
        return domain;
    }

    public void setDomain(ReportDomain domain) {
        this.domain = domain;
    }

    public Map<String, String> getFilters() {
        return filters != null ? filters : Collections.emptyMap();
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters;
    }

    @Nullable
    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(@Nullable String groupBy) {
        this.groupBy = groupBy;
    }

    @Nullable
    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(@Nullable List<String> columns) {
        this.columns = columns;
    }

    public List<String> getEffectiveColumns() {
        if (columns != null && !columns.isEmpty()) {
            return columns;
        }
        return domain != null ? domain.getAvailableColumns() : Collections.emptyList();
    }

    public ExportFormat getFormat() {
        return format;
    }

    public void setFormat(ExportFormat format) {
        this.format = format;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = Math.min(maxResults, 5000);
    }

    private String engine = "Motor Semántico";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}

