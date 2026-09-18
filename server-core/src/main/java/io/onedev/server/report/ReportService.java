package io.onedev.server.report;

import io.onedev.server.model.Project;

/**
 * Servicio principal de reportes dinámicos.
 * Orquesta: IA → JSON → Collector → Exporter → bytes.
 */
public interface ReportService {

    /**
     * Traduce una consulta en lenguaje natural a un ReportRequest usando la IA.
     */
    ReportRequest translateQuery(Project project, String naturalLanguageQuery);

    /**
     * Recolecta datos del dominio especificado en el request.
     */
    ReportResult collectData(Project project, ReportRequest request);

    /**
     * Exporta un ReportResult al formato indicado en el request.
     * @return bytes del archivo exportado
     */
    byte[] exportReport(ReportResult result, ReportRequest request);
}
