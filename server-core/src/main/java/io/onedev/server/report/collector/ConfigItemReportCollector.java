package io.onedev.server.report.collector;

import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemType;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;

/**
 * Colector de Elementos de Configuración (CIs).
 * Extrae requisitos (RFs), especificaciones, ADRs, esquemas de BD, código y pruebas del proyecto.
 * Soporta filtrado inteligente por tipo (REQUIREMENT, ADR, DATA_MODEL_ERD, etc.) y búsqueda textual.
 */
public class ConfigItemReportCollector implements ReportDataCollector {

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        if (headers == null || headers.isEmpty()) {
            headers = ReportDomain.CONFIG_ITEMS.getAvailableColumns();
        }
        List<List<String>> rows = new ArrayList<>();

        try {
            TraceabilityMatrixService matrixService = OneDev.getInstance(TraceabilityMatrixService.class);
            TraceabilityMatrix matrix = matrixService.buildMatrix(project, null);

            String typeFilter = request.getFilters().getOrDefault("type", null);
            String queryFilter = request.getFilters().getOrDefault("query", null);

            for (ConfigItem item : matrix.getAllItems()) {
                if (!matchesType(item, typeFilter)) {
                    continue;
                }

                if (queryFilter != null && !queryFilter.trim().isEmpty()) {
                    String q = queryFilter.toLowerCase().trim();
                    boolean match = (item.getTitle() != null && item.getTitle().toLowerCase().contains(q))
                        || (item.getIdentifier() != null && item.getIdentifier().toLowerCase().contains(q))
                        || (item.getPath() != null && item.getPath().toLowerCase().contains(q));
                    if (!match) continue;
                }

                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col.toLowerCase()) {
                        case "identifier":
                            row.add(item.getIdentifier() != null ? item.getIdentifier() : "-");
                            break;
                        case "title":
                            row.add(item.getTitle() != null ? item.getTitle() : "-");
                            break;
                        case "type":
                            row.add(item.getType() != null ? item.getType().getDisplayName() : "-");
                            break;
                        case "path":
                            row.add(item.getPath() != null ? item.getPath() : "-");
                            break;
                        case "description":
                            row.add(item.getType() != null ? item.getType().getDescription() : "-");
                            break;
                        default:
                            row.add("-");
                            break;
                    }
                }
                rows.add(row);

                if (rows.size() >= request.getMaxResults()) break;
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.CONFIG_ITEMS,
                "Error al obtener elementos de configuración: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.CONFIG_ITEMS, headers, rows);
    }

    private boolean matchesType(ConfigItem item, String filter) {
        if (filter == null || filter.trim().isEmpty()) return true;
        String f = filter.toLowerCase().trim();
        String name = item.getType().name().toLowerCase();
        String disp = item.getType().getDisplayName().toLowerCase();

        if (name.equalsIgnoreCase(f) || disp.equalsIgnoreCase(f)) return true;
        if (name.contains(f) || disp.contains(f)) return true;

        if ((f.contains("requisi") || f.contains("rf") || f.contains("req") || f.contains("especifica"))
                && item.getType() == ConfigItemType.REQUIREMENT) {
            return true;
        }
        if ((f.contains("adr") || f.contains("arquitec") || f.contains("decis"))
                && item.getType() == ConfigItemType.ADR) {
            return true;
        }
        if ((f.contains("base") || f.contains("datos") || f.contains("erd") || f.contains("sql") || f.contains("tabla") || f.contains("model"))
                && item.getType() == ConfigItemType.DATA_MODEL_ERD) {
            return true;
        }
        if ((f.contains("test") || f.contains("prueba"))
                && item.getType() == ConfigItemType.TEST_SPEC) {
            return true;
        }
        if ((f.contains("codig") || f.contains("fuente") || f.contains("source") || f.contains("java"))
                && item.getType() == ConfigItemType.SOURCE_CODE) {
            return true;
        }
        if ((f.contains("infra") || f.contains("iac") || f.contains("docker") || f.contains("k8s"))
                && item.getType() == ConfigItemType.INFRASTRUCTURE_IAC) {
            return true;
        }
        return false;
    }
}
