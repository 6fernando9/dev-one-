package io.onedev.server.report;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private static final Gson GSON = new GsonBuilder().create();

    private final ChatModel model;

    public ReportQueryTranslator(ChatModel model) {
        this.model = model;
    }

    /**
     * Traduce una petición en lenguaje natural a un ReportRequest.
     */
    public ReportRequest translate(String naturalLanguage) {
        if (naturalLanguage.length() > MAX_QUERY_LENGTH) {
            naturalLanguage = naturalLanguage.substring(0, MAX_QUERY_LENGTH);
        }

        String domainList = Stream.of(ReportDomain.values())
            .map(d -> "  - " + d.name() + " (" + d.getDisplayName() + "): columnas=" + String.join(", ", d.getAvailableColumns()))
            .collect(Collectors.joining("\n"));

        String systemPrompt = """
            Eres un traductor de consultas de informes. El usuario solicita un informe en lenguaje natural \
            y tú debes responder ÚNICAMENTE con un JSON válido con la siguiente estructura:

            {
              "domain": "<DOMINIO>",
              "filters": { "<clave>": "<valor>" },
              "groupBy": "<campo_opcional_o_null>",
              "columns": ["col1", "col2"],
              "format": "CSV|PDF|EXCEL",
              "title": "<título del informe>",
              "description": "<descripción breve>",
              "maxResults": 500
            }

            DOMINIOS DISPONIBLES y sus columnas:
            """ + domainList + """

            REGLAS:
            1. "domain" DEBE ser uno de los valores de DOMINIO listados arriba (ej: BRANCHES, COMMITS, ISSUES...).
            2. "filters" es un mapa de filtros. Claves comunes: "since" (ej: "7 days ago"), "author", "state" (ej: "Open"), "status".
            3. "columns" es una lista de columnas del dominio. Si el usuario no especifica, usa todas las columnas del dominio.
            4. "format" por defecto es "CSV". Si el usuario pide PDF o Excel, cámbialo.
            5. "title" debe ser un título descriptivo breve del informe.
            6. Responde SOLO el JSON, sin texto adicional, sin bloques de código markdown.
            """;

        var response = model.chat(new SystemMessage(systemPrompt), new UserMessage(naturalLanguage));
        String text = response.aiMessage().text().trim();

        return parseResponse(text, naturalLanguage);
    }

    /**
     * Parsea la respuesta JSON de la IA y la valida contra los dominios conocidos.
     */
    private ReportRequest parseResponse(String rawText, String originalQuery) {
        ReportRequest request = new ReportRequest();

        try {
            String cleanJson = cleanMarkdownJson(rawText);
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
            if (root.has("title")) {
                request.setTitle(root.get("title").getAsString());
            } else {
                request.setTitle("Informe de " + request.getDomain().getDisplayName());
            }

            // Parse description
            if (root.has("description") && !root.get("description").isJsonNull()) {
                request.setDescription(root.get("description").getAsString());
            }

            // Parse maxResults
            if (root.has("maxResults")) {
                request.setMaxResults(root.get("maxResults").getAsInt());
            }

        } catch (Exception e) {
            logger.warn("Error parseando respuesta de IA para reporte, usando heurística: {}", e.getMessage());
            request.setDomain(inferDomain(originalQuery));
            request.setTitle("Informe de " + request.getDomain().getDisplayName());
        }

        return request;
    }

    /**
     * Infiere el dominio heurísticamente si la IA no responde correctamente.
     */
    private ReportDomain inferDomain(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        if (q.contains("rama") || q.contains("branch")) return ReportDomain.BRANCHES;
        if (q.contains("commit")) return ReportDomain.COMMITS;
        if (q.contains("issue") || q.contains("problema") || q.contains("tarea")) return ReportDomain.ISSUES;
        if (q.contains("pull request") || q.contains("pr") || q.contains("solicitud")) return ReportDomain.PULL_REQUESTS;
        if (q.contains("trazabilidad") || q.contains("rtm") || q.contains("matriz")) return ReportDomain.TRACEABILITY_MATRIX;
        if (q.contains("elemento") || q.contains("configuraci")) return ReportDomain.CONFIG_ITEMS;
        if (q.contains("usuario") || q.contains("user") || q.contains("rol")) return ReportDomain.USERS;
        if (q.contains("auditor") || q.contains("evento") || q.contains("log")) return ReportDomain.AUDIT_EVENTS;
        if (q.contains("build") || q.contains("compilaci")) return ReportDomain.BUILDS;
        return ReportDomain.COMMITS;
    }

    private String cleanMarkdownJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}
