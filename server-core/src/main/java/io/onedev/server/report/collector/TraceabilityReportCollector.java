package io.onedev.server.report.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemType;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;
import io.onedev.server.traceability.matrix.TraceabilityRow;

public class TraceabilityReportCollector implements ReportDataCollector {

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            TraceabilityMatrixService matrixService = OneDev.getInstance(TraceabilityMatrixService.class);
            TraceabilityMatrix matrix = matrixService.buildMatrix(project, null);

            for (TraceabilityRow trRow : matrix.getRows()) {
                if (trRow.getPrimaryItem().getType() != ConfigItemType.REQUIREMENT) continue;

                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "requirementId": row.add(trRow.getPrimaryItem().getIdentifier()); break;
                        case "title": row.add(trRow.getPrimaryItem().getTitle()); break;
                        case "status": row.add(trRow.getStatus().getDisplayName()); break;
                        case "sourceFiles": row.add(trRow.getSourceFiles().stream().map(ConfigItem::getPath).collect(Collectors.joining("; "))); break;
                        case "adrs": row.add(trRow.getAdrs().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining("; "))); break;
                        case "dataModels": row.add(trRow.getDataModels().stream().map(ConfigItem::getPath).collect(Collectors.joining("; "))); break;
                        case "tasks": row.add(trRow.getTasks().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining("; "))); break;
                        case "notes": row.add(trRow.getNotes()); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.TRACEABILITY_MATRIX,
                "Error al obtener la matriz de trazabilidad: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.TRACEABILITY_MATRIX, headers, rows);
    }
}
