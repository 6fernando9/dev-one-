package io.onedev.server.report;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.report.collector.*;
import io.onedev.server.report.export.CsvReportExporter;
import io.onedev.server.report.export.PdfReportExporter;
import io.onedev.server.service.SettingService;
import io.onedev.server.service.UserService;

/**
 * Implementación robusta del servicio de reportes dinámicos.
 * Orquesta: Detección inteligente de IA (LM Studio / AI Users / System) →
 * Motor Semántico Heurístico de Alta Precisión → Colectores → Exportadores.
 */
@Singleton
public class DefaultReportService implements ReportService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultReportService.class);

    private final SettingService settingService;
    private final UserService userService;
    private final Map<ReportDomain, ReportDataCollector> collectors;

    @Inject
    public DefaultReportService(SettingService settingService, UserService userService) {
        this.settingService = settingService;
        this.userService = userService;
        this.collectors = new HashMap<>();
        collectors.put(ReportDomain.CONFIG_ITEMS, new ConfigItemReportCollector());
        collectors.put(ReportDomain.TRACEABILITY_MATRIX, new TraceabilityReportCollector());
        collectors.put(ReportDomain.BRANCHES, new BranchReportCollector());
        collectors.put(ReportDomain.COMMITS, new CommitReportCollector());
        collectors.put(ReportDomain.ISSUES, new IssueReportCollector());
        collectors.put(ReportDomain.PULL_REQUESTS, new PullRequestReportCollector());
        collectors.put(ReportDomain.USERS, new UserReportCollector());
        collectors.put(ReportDomain.AUDIT_EVENTS, new AuditEventReportCollector());
        collectors.put(ReportDomain.BUILDS, new BuildReportCollector());
    }

    @Override
    public ReportRequest translateQuery(Project project, String naturalLanguageQuery) {
        ChatModel model = getChatModel();
        if (model != null) {
            try {
                logger.info("Attempting report translation with AI for query: '{}'", naturalLanguageQuery);
                ReportQueryTranslator translator = new ReportQueryTranslator(model);
                ReportRequest request = translator.translate(naturalLanguageQuery);
                if (request != null && request.getDomain() != null) {
                    request.setEngine("IA (LM Studio)");
                    logger.info("AI translation success: domain={}, filters={}", request.getDomain(), request.getFilters());
                    return request;
                }
            } catch (Exception e) {
                logger.warn("AI translation encountered an issue: {}. Falling back to Semantic NLP Engine.", e.getMessage());
            }
        } else {
            logger.info("No active AI model detected. Using Semantic NLP Engine for query: '{}'", naturalLanguageQuery);
        }

        ReportRequest request = translateHeuristically(naturalLanguageQuery);
        request.setEngine("Motor Semántico");
        return request;
    }

    @Override
    public ReportResult collectData(Project project, ReportRequest request) {
        ReportDataCollector collector = collectors.get(request.getDomain());
        if (collector == null) {
            return new ReportResult(request.getTitle(), request.getDomain(),
                "Dominio de reporte no soportado: " + request.getDomain());
        }

        ReportResult result = collector.collect(project, request);
        result.setEngine(request.getEngine());

        // Populate engine info and filter summaries for UI
        if (!request.getFilters().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            request.getFilters().forEach((k, v) -> {
                if (sb.length() > 0) sb.append(", ");
                sb.append(k).append(": ").append(v);
            });
            result.setFilterSummary(sb.toString());
        }

        return result;
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
        // 1. Check system LiteModel in Administration
        try {
            if (settingService != null && settingService.getAiSetting() != null) {
                ChatModel model = settingService.getAiSetting().getLiteModel();
                if (model != null) {
                    return model;
                }
            }
        } catch (Exception ignored) {}

        // 2. Check AI Users in OneDev
        try {
            if (userService != null) {
                for (User user : userService.query()) {
                    if (user.getType() == User.Type.AI && !user.isDisabled() && user.getAiSetting() != null) {
                        if (user.getAiSetting().getModelSetting() != null) {
                            ChatModel model = user.getAiSetting().getModelSetting().getChatModel();
                            if (model != null) {
                                return model;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Auto-connect to LM Studio bridge on host (172.24.240.1:12340 or localhost:1234)
        try {
            return OpenAiChatModel.builder()
                .baseUrl("http://172.24.240.1:12340/v1")
                .apiKey("lm-studio")
                .modelName("llama-3.2-1b-instruct")
                .timeout(Duration.ofSeconds(12))
                .maxRetries(1)
                .build();
        } catch (Exception e) {
            logger.debug("Direct bridge connection failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Motor Semántico Heurístico de Alta Precisión.
     * Analiza intents con puntuación ponderada y límites de palabras para evitar colisiones
     * (e.g. la palabra 'proyecto' NUNCA activará Pull Requests por 'pr').
     */
    public ReportRequest translateHeuristically(String query) {
        ReportRequest request = new ReportRequest();
        String q = query != null ? query.toLowerCase(Locale.ROOT).trim() : "";
        Map<String, String> filters = new HashMap<>();

        // Formato
        if (q.contains("pdf")) {
            request.setFormat(ReportRequest.ExportFormat.PDF);
        } else if (q.contains("excel") || q.contains("xlsx")) {
            request.setFormat(ReportRequest.ExportFormat.EXCEL);
        } else {
            request.setFormat(ReportRequest.ExportFormat.CSV);
        }

        // Puntuaciones de intención
        int configItemsScore = 0;
        int rtmScore = 0;
        int branchScore = 0;
        int commitScore = 0;
        int issueScore = 0;
        int pullRequestScore = 0;
        int userScore = 0;
        int auditScore = 0;
        int buildScore = 0;

        // --- CONFIG ITEMS (Requisitos, ADRs, BD, Pruebas, Código, IaC) ---
        if (containsAny(q, "elemento de configuraci", "elementos de configuraci", "ci", "cis", "inventario", "artefacto", "item de configuraci")) {
            configItemsScore += 30;
        }
        if (containsAny(q, "requisito", "requisitos", "especifico", "especificos", "especificacion", "especificaciones", "rf-", "requerimiento", "requerimientos")) {
            configItemsScore += 25;
            filters.put("type", "REQUIREMENT");
        }
        if (containsAny(q, "adr", "adrs", "arquitectura", "decision arquitectonica", "decisiones arquitectonicas")) {
            configItemsScore += 25;
            filters.put("type", "ADR");
        }
        if (containsAny(q, "base de datos", "bases de datos", "tabla", "tablas", "modelo de datos", "sql", "erd", "script")) {
            configItemsScore += 25;
            filters.put("type", "DATA_MODEL_ERD");
        }
        if (containsAny(q, "test", "tests", "prueba", "pruebas", "caso de prueba", "especificacion de prueba")) {
            configItemsScore += 25;
            filters.put("type", "TEST_SPEC");
        }
        if (containsAny(q, "codigo fuente", "clase java", "clases java", "archivos java")) {
            configItemsScore += 20;
            if (!filters.containsKey("type")) filters.put("type", "SOURCE_CODE");
        }
        if (containsAny(q, "infraestructura", "iac", "docker", "dockerfile", "buildspec")) {
            configItemsScore += 25;
            filters.put("type", "INFRASTRUCTURE_IAC");
        }

        // --- TRACEABILITY MATRIX ---
        if (containsAny(q, "trazabilidad", "matriz de trazabilidad", "rtm", "cobertura", "sincronizac", "sincronizado", "deriva", "drift", "huerfano", "orphan", "vinculac")) {
            rtmScore += 30;
        }

        // --- BRANCHES ---
        if (containsAny(q, "rama", "ramas", "branch", "branches", "tag", "tags", "etiqueta", "etiquetas")) {
            branchScore += 25;
        }

        // --- COMMITS ---
        if (containsAny(q, "commit", "commits", "historial de cambio", "historial de commit", "ultimos commit", "autor de commit")) {
            commitScore += 25;
        }

        // --- ISSUES ---
        if (containsAny(q, "issue", "issues", "problema", "problemas", "ticket", "tickets", "tarea", "tareas", "bug", "bugs")) {
            issueScore += 25;
        }
        if (q.contains("abierto") || q.contains("open")) filters.put("state", "Open");
        if (q.contains("cerrado") || q.contains("closed")) filters.put("state", "Closed");

        // --- PULL REQUESTS (WORD BOUNDARY ONLY: NEVER match inside 'proyecto'!) ---
        if (containsWordOrPhrase(q, "pull request", "pull requests", "solicitud de extraccion", "solicitudes de extraccion", "pr", "prs")) {
            pullRequestScore += 30;
        }

        // --- USERS ---
        if (containsAny(q, "usuario", "usuarios", "user", "users", "rol", "roles", "miembro", "miembros", "equipo", "permiso", "permisos")) {
            userScore += 25;
        }

        // --- AUDIT EVENTS ---
        if (containsAny(q, "auditor", "auditoria", "auditorias", "evento de auditoria", "eventos de auditoria", "registro de auditoria", "log de seguridad", "seguridad")) {
            auditScore += 25;
        }

        // --- BUILDS ---
        if (containsAny(q, "build", "builds", "compilacion", "compilaciones", "pipeline", "pipelines", "trabajo", "trabajos", "job", "jobs", "ci/cd")) {
            buildScore += 25;
        }

        // Filtro de tiempo para commits o eventos
        if (containsAny(q, "semana", "week")) filters.put("since", "7 days");
        else if (containsAny(q, "mes", "month")) filters.put("since", "30 days");
        else if (containsAny(q, "ayer", "hoy", "today", "yesterday")) filters.put("since", "1 day");

        // Elegir dominio ganador por máxima puntuación
        int maxScore = 0;
        ReportDomain bestDomain = ReportDomain.CONFIG_ITEMS;

        if (configItemsScore > maxScore) { maxScore = configItemsScore; bestDomain = ReportDomain.CONFIG_ITEMS; }
        if (rtmScore > maxScore) { maxScore = rtmScore; bestDomain = ReportDomain.TRACEABILITY_MATRIX; }
        if (branchScore > maxScore) { maxScore = branchScore; bestDomain = ReportDomain.BRANCHES; }
        if (commitScore > maxScore) { maxScore = commitScore; bestDomain = ReportDomain.COMMITS; }
        if (issueScore > maxScore) { maxScore = issueScore; bestDomain = ReportDomain.ISSUES; }
        if (pullRequestScore > maxScore) { maxScore = pullRequestScore; bestDomain = ReportDomain.PULL_REQUESTS; }
        if (userScore > maxScore) { maxScore = userScore; bestDomain = ReportDomain.USERS; }
        if (auditScore > maxScore) { maxScore = auditScore; bestDomain = ReportDomain.AUDIT_EVENTS; }
        if (buildScore > maxScore) { maxScore = buildScore; bestDomain = ReportDomain.BUILDS; }

        if (maxScore == 0) {
            if (q.contains("proyecto") || q.contains("inventario") || q.contains("resumen")) {
                bestDomain = ReportDomain.CONFIG_ITEMS;
            } else {
                bestDomain = ReportDomain.COMMITS;
            }
        }

        request.setDomain(bestDomain);
        request.setFilters(filters);

        // Títulos contextuales inteligentes
        if (bestDomain == ReportDomain.CONFIG_ITEMS && "REQUIREMENT".equals(filters.get("type"))) {
            request.setTitle("Requisitos Específicos del Proyecto");
            request.setDescription("Inventario de requisitos funcionales y especificaciones detectadas en el repositorio.");
        } else if (bestDomain == ReportDomain.CONFIG_ITEMS && filters.containsKey("type")) {
            request.setTitle("Elementos de Configuración (" + filters.get("type") + ")");
            request.setDescription("Filtrado específico de artefactos de configuración del tipo " + filters.get("type") + ".");
        } else if (bestDomain == ReportDomain.TRACEABILITY_MATRIX) {
            request.setTitle("Matriz de Trazabilidad Integral (RTM)");
            request.setDescription("Relación y sincronización entre Requisitos, Código, ADRs, ERD y Tareas.");
        } else {
            request.setTitle("Informe de " + bestDomain.getDisplayName());
            request.setDescription("Informe generado dinámicamente según la consulta solicitada.");
        }

        return request;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private boolean containsWordOrPhrase(String text, String... targets) {
        for (String target : targets) {
            if (target.contains(" ")) {
                if (text.contains(target)) return true;
            } else {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(target) + "\\b", Pattern.CASE_INSENSITIVE);
                if (p.matcher(text).find()) return true;
            }
        }
        return false;
    }
}
