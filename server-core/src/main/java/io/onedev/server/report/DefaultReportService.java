package io.onedev.server.report;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.chat.ChatModel;
import io.onedev.server.model.Project;
import io.onedev.server.report.collector.*;
import io.onedev.server.report.export.CsvReportExporter;
import io.onedev.server.report.export.PdfReportExporter;
import io.onedev.server.service.SettingService;

/**
 * Implementación del servicio de reportes dinámicos.
 * Orquesta: IA → JSON → Collector → Exporter → bytes.
 */
@Singleton
public class DefaultReportService implements ReportService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultReportService.class);

    private final SettingService settingService;
    private final Map<ReportDomain, ReportDataCollector> collectors;

    @Inject
    public DefaultReportService(SettingService settingService) {
        this.settingService = settingService;
        this.collectors = new HashMap<>();
        collectors.put(ReportDomain.BRANCHES, new BranchReportCollector());
        collectors.put(ReportDomain.COMMITS, new CommitReportCollector());
        collectors.put(ReportDomain.ISSUES, new IssueReportCollector());
        collectors.put(ReportDomain.PULL_REQUESTS, new PullRequestReportCollector());
        collectors.put(ReportDomain.CONFIG_ITEMS, new ConfigItemReportCollector());
        collectors.put(ReportDomain.TRACEABILITY_MATRIX, new TraceabilityReportCollector());
        collectors.put(ReportDomain.USERS, new UserReportCollector());
        collectors.put(ReportDomain.AUDIT_EVENTS, new AuditEventReportCollector());
        collectors.put(ReportDomain.BUILDS, new BuildReportCollector());
    }

    @Override
    public ReportRequest translateQuery(Project project, String naturalLanguageQuery) {
        ChatModel model = getChatModel();
        if (model != null) {
            try {
                ReportQueryTranslator translator = new ReportQueryTranslator(model);
                return translator.translate(naturalLanguageQuery);
            } catch (Exception e) {
                logger.warn("Error traduciendo consulta con IA, usando heurística: {}", e.getMessage());
            }
        }
        return translateHeuristically(naturalLanguageQuery);
    }

    @Override
    public ReportResult collectData(Project project, ReportRequest request) {
        ReportDataCollector collector = collectors.get(request.getDomain());
        if (collector == null) {
            return new ReportResult(request.getTitle(), request.getDomain(),
                "Dominio de reporte no soportado: " + request.getDomain());
        }
        return collector.collect(project, request);
    }

    @Override
    public byte[] exportReport(ReportResult result, ReportRequest request) {
        switch (request.getFormat()) {
            case PDF:
                return PdfReportExporter.export(result);
            case CSV:
            default:
                return CsvReportExporter.export(result);
        }
    }

    @Nullable
    private ChatModel getChatModel() {
        if (settingService != null && settingService.getAiSetting() != null) {
            return settingService.getAiSetting().getLiteModel();
        }
        return null;
    }

    private ReportRequest translateHeuristically(String query) {
        ReportRequest request = new ReportRequest();
        String q = query.toLowerCase();

        if (q.contains("rama") || q.contains("branch")) request.setDomain(ReportDomain.BRANCHES);
        else if (q.contains("commit")) request.setDomain(ReportDomain.COMMITS);
        else if (q.contains("issue") || q.contains("problema")) request.setDomain(ReportDomain.ISSUES);
        else if (q.contains("pull request") || q.contains("pr")) request.setDomain(ReportDomain.PULL_REQUESTS);
        else if (q.contains("trazabilidad") || q.contains("matriz")) request.setDomain(ReportDomain.TRACEABILITY_MATRIX);
        else if (q.contains("elemento") || q.contains("configuraci")) request.setDomain(ReportDomain.CONFIG_ITEMS);
        else if (q.contains("usuario")) request.setDomain(ReportDomain.USERS);
        else if (q.contains("auditor")) request.setDomain(ReportDomain.AUDIT_EVENTS);
        else if (q.contains("build")) request.setDomain(ReportDomain.BUILDS);
        else request.setDomain(ReportDomain.COMMITS);

        if (q.contains("pdf")) request.setFormat(ReportRequest.ExportFormat.PDF);
        else if (q.contains("excel") || q.contains("xlsx")) request.setFormat(ReportRequest.ExportFormat.EXCEL);
        else request.setFormat(ReportRequest.ExportFormat.CSV);

        request.setTitle("Informe de " + request.getDomain().getDisplayName());
        return request;
    }
}
