package io.onedev.server.report.collector;

import io.onedev.server.model.Project;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

/**
 * Interfaz para los colectores de datos de cada dominio de reporte.
 */
public interface ReportDataCollector {

    /**
     * Recolecta datos del dominio correspondiente, aplicando filtros y permisos.
     */
    ReportResult collect(Project project, ReportRequest request);
}
