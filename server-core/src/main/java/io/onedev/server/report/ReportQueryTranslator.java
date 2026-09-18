package io.onedev.server.report;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Traduce una consulta en lenguaje natural a un ReportRequest JSON estructurado
 * usando el modelo de IA (LM Studio / OpenAI compatible).
 */
public class ReportQueryTranslator {

    private static final Logger logger = LoggerFactory.getLogger(ReportQueryTranslator.class);
    private static final int MAX_QUERY_LENGTH = 512;

    private final ChatModel model;

    public ReportQueryTranslator(ChatModel model) {
        this.model = model;
    }

    /**
     * Traduce una petición en lenguaje natural a un ReportRequest estructurado.
     */
    public ReportRequest translate(String naturalLanguage) {
        if (naturalLanguage.length() > MAX_QUERY_LENGTH) {
            naturalLanguage = naturalLanguage.substring(0, MAX_QUERY_LENGTH);
        }

        String domainList = Stream.of(ReportDomain.values())
            .map(d -> "  - " + d.name() + " (" + d.getRawDisplayName() + "): columnas=" + String.join(", ", d.getAvailableColumns()))
            .collect(Collectors.joining("\n"));

        String systemPrompt = """
            Eres un asistente especializado en clasificar consultas de informes para una plataforma de software/DevOps.
            Tu labor es responder UNICAMENTE con un objeto JSON valido con la siguiente estructura:

            {
              "domain": "<DOMINIO>",
              "filters": { "clave": "valor" },
              "columns": ["col1", "col2"],
              "format": "CSV|PDF",
              "title": "<titulo descriptivo>",
              "description": "<resumen de la consulta>",
              "maxResults": 500
            }

            DOMINIOS DISPONIBLES:
            """ + domainList + """

            REGLAS IMPORTANTES DE CLASIFICACION:
            1. "CONFIG_ITEMS": Usar para consultas sobre elementos de configuracion, requisitos funcionales (RF), especificaciones, ADRs, tablas o bases de datos (ERD), pruebas/tests, codigo fuente o infraestructura (IaC).
               - Si el usuario pide requisitos o requerimientos: poner filter {"type": "REQUIREMENT"}.
               - Si pide ADRs o decisiones arquitectonicas: poner filter {"type": "ADR"}.
               - Si pide base de datos, tablas o modelos: poner filter {"type": "DATA_MODEL_ERD"}.
               - Si pide pruebas o tests: poner filter {"type": "TEST_SPEC"}.
               - Si pide codigo o clases: poner filter {"type": "SOURCE_CODE"}.
               - Si pide infraestructura, docker o iac: poner filter {"type": "INFRASTRUCTURE_IAC"}.
            2. "TRACEABILITY_MATRIX": Usar si el usuario pide matriz de trazabilidad, RTM, sincronizacion, cobertura de requisitos o deriva (drift).
            3. "BRANCHES": Usar si el usuario pide ramas del repositorio o git branches.
            4. "COMMITS": Usar si pide commits, cambios recientes, historial o contribuciones por autor/fecha.
            5. "ISSUES": Usar si pide problemas, tickets, tareas abiertas/cerradas o bugs.
            6. "PULL_REQUESTS": Usar UNICAMENTE cuando explicitamente pida pull requests o solicitudes de extraccion (NO confundir con la palabra "proyecto").
            7. "USERS": Usar si pide lista de usuarios, cuentas o roles.
            8. "AUDIT_EVENTS": Usar si pide registros o eventos de auditoria o seguridad.
            9. "BUILDS": Usar si pide compilaciones, builds o pipelines de CI/CD.

            Responde EXCLUSIVAMENTE con el bloque JSON. Sin comentarios, sin markdown adicional fuera del bloque.
            """;

        try {
            var response = model.chat(new SystemMessage(systemPrompt), new UserMessage(naturalLanguage));
            String text = response.aiMessage().text();
            if (text != null) {
                text = text.trim();
                logger.info("IA response for report translation: {}", text);
                return parseResponse(text, naturalLanguage);
            }
        } catch (Exception e) {
            logger.warn("Error calling AI model: {}", e.getMessage());
        }

        ReportRequest fallback = new ReportRequest();
        fallback.setDomain(inferDomain(naturalLanguage));
        fallback.setTitle("Informe de " + fallback.getDomain().getDisplayName());
        return fallback;
    }

    /**
     * Parsea la respuesta JSON de la IA y la valida contra los dominios conocidos.
     */
    public ReportRequest parseResponse(String rawText, String originalQuery) {
        ReportRequest request = new ReportRequest();

        try {
            String cleanJson = extractJson(rawText);
            JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();

            // Parse domain
            if (root.has("domain")) {
                request.setDomain(ReportDomain.fromString(root.get("domain").getAsString()));
            } else {
                request.setDomain(inferDomain(originalQuery));
            }

            // Parse filters
            if (root.has("filters") && root.get("filters").isJsonObject()) {
                JsonObject filtersObj = root.getAsJsonObject("filters");
                var filters = new java.util.HashMap<String, String>();
                for (var entry : filtersObj.entrySet()) {
                    if (!entry.getValue().isJsonNull()) {
                        filters.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                request.setFilters(filters);
            }

            // Parse groupBy
            if (root.has("groupBy") && !root.get("groupBy").isJsonNull()) {
                request.setGroupBy(root.get("groupBy").getAsString());
            }

            // Parse columns
            if (root.has("columns") && root.get("columns").isJsonArray()) {
                var columns = new java.util.ArrayList<String>();
                for (JsonElement el : root.getAsJsonArray("columns")) {
                    columns.add(el.getAsString());
                }
                if (!columns.isEmpty()) {
                    request.setColumns(columns);
                }
            }

            // Parse format
            if (root.has("format")) {
                try {
                    request.setFormat(ReportRequest.ExportFormat.valueOf(
                        root.get("format").getAsString().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    request.setFormat(ReportRequest.ExportFormat.CSV);
                }
            }

            // Parse title
            if (root.has("title") && !root.get("title").isJsonNull()) {
                request.setTitle(root.get("title").getAsString());
            } else {
                request.setTitle("Informe de " + request.getDomain().getDisplayName());
            }

            // Parse description
            if (root.has("description") && !root.get("description").isJsonNull()) {
                request.setDescription(root.get("description").getAsString());
            }

            // Parse maxResults
            if (root.has("maxResults") && !root.get("maxResults").isJsonNull()) {
                request.setMaxResults(root.get("maxResults").getAsInt());
            }

        } catch (Exception e) {
            logger.warn("Error parsing AI JSON response: {}. Fallback to NLP heuristics.", e.getMessage());
            request.setDomain(inferDomain(originalQuery));
            request.setTitle("Informe de " + request.getDomain().getDisplayName());
        }

        return request;
    }

    /**
     * Infiere el dominio de forma segura sin colisiones por substrings como 'pr' dentro de 'proyecto'.
     */
    public ReportDomain inferDomain(String query) {
        String q = query.toLowerCase(Locale.ROOT);

        if (q.contains("elemento") || q.contains("configuraci") || q.contains("requisito") || q.contains("ci") || q.contains("artefacto")) {
            return ReportDomain.CONFIG_ITEMS;
        }
        if (q.contains("trazabilidad") || q.contains("rtm") || q.contains("matriz") || q.contains("cobertura")) {
            return ReportDomain.TRACEABILITY_MATRIX;
        }
        if (q.contains("rama") || q.contains("branch")) {
            return ReportDomain.BRANCHES;
        }
        if (q.contains("commit") || q.contains("cambio")) {
            return ReportDomain.COMMITS;
        }
        if (q.contains("issue") || q.contains("problema") || q.contains("tarea") || q.contains("ticket")) {
            return ReportDomain.ISSUES;
        }
        // ONLY match PR if full phrase or word boundary '\bprs?\b'
        Pattern prPattern = Pattern.compile("\\b(pull\\s*request|prs?|solicitud(es)?\\s+de\\s+extracci[oó]n)\\b", Pattern.CASE_INSENSITIVE);
        if (prPattern.matcher(q).find()) {
            return ReportDomain.PULL_REQUESTS;
        }
        if (q.contains("usuario") || q.contains("user") || q.contains("rol")) {
            return ReportDomain.USERS;
        }
        if (q.contains("auditor") || q.contains("evento") || q.contains("log")) {
            return ReportDomain.AUDIT_EVENTS;
        }
        if (q.contains("build") || q.contains("compilaci") || q.contains("pipeline") || q.contains("job")) {
            return ReportDomain.BUILDS;
        }

        return ReportDomain.CONFIG_ITEMS;
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }
}
