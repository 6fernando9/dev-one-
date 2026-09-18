package io.onedev.server.report.collector;

import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;

public class ConfigItemReportCollector implements ReportDataCollector {

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            TraceabilityMatrixService matrixService = OneDev.getInstance(TraceabilityMatrixService.class);
            TraceabilityMatrix matrix = matrixService.buildMatrix(project, null);

            String typeFilter = request.getFilters().getOrDefault("type", null);

            for (ConfigItem item : matrix.getAllItems()) {
                if (typeFilter != null && !item.getType().name().equalsIgnoreCase(typeFilter)
                    && !item.getType().getDisplayName().toLowerCase().contains(typeFilter.toLowerCase())) {
                    continue;
                }

                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "path": row.add(item.getPath()); break;
                        case "type": row.add(item.getType().getDisplayName()); break;
                        case "identifier": row.add(item.getIdentifier()); break;
                        case "title": row.add(item.getTitle()); break;
                        default: row.add("-");
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
}
