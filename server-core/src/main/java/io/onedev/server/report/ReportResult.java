package io.onedev.server.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Resultado tabular de un reporte dinámico.
 * Contiene los headers (columnas), filas de datos, motor utilizado (IA / Heurística) y filtros aplicados.
 */
public class ReportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String title;
    private @Nullable String description;
    private final ReportDomain domain;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final Date generatedAt;
    private final @Nullable String errorMessage;
    private String engine = "Semantic Engine";
    private @Nullable String filterSummary = null;

    public ReportResult(String title, @Nullable String description, ReportDomain domain,
                        List<String> headers, List<List<String>> rows) {
        this.title = title;
        this.description = description;
        this.domain = domain;
        this.headers = headers;
        this.rows = rows;
        this.generatedAt = new Date();
        this.errorMessage = null;
    }

    public ReportResult(String title, ReportDomain domain, String errorMessage) {
        this.title = title;
        this.description = null;
        this.domain = domain;
        this.headers = Collections.emptyList();
        this.rows = Collections.emptyList();
        this.generatedAt = new Date();
        this.errorMessage = errorMessage;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public ReportDomain getDomain() {
        return domain;
    }

    public List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    public List<List<String>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public Date getGeneratedAt() {
        return generatedAt;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null;
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return headers.size();
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    @Nullable
    public String getFilterSummary() {
        return filterSummary;
    }

    public void setFilterSummary(@Nullable String filterSummary) {
        this.filterSummary = filterSummary;
    }

    public List<List<String>> getPreviewRows(int maxRows) {
        if (rows.size() <= maxRows) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, maxRows));
    }
}
