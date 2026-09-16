package io.onedev.server.traceability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.onedev.server.service.SettingService;

/**
 * Implementación predeterminada del servicio de análisis de impacto de cambios.
 * Soporta ejecución asistida por IA (compatible con IAs locales como Ollama y en la nube)
 * y cuenta con un motor heurístico de respaldo cuando la IA no está disponible.
 */
@Singleton
public class DefaultImpactAnalysisService implements ImpactAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultImpactAnalysisService.class);

    private final ConfigItemClassifier classifier;
    private final SettingService settingService;

    @Inject
    public DefaultImpactAnalysisService(ConfigItemClassifier classifier, @Nullable SettingService settingService) {
        this.classifier = classifier;
        this.settingService = settingService;
    }

    public DefaultImpactAnalysisService(ConfigItemClassifier classifier) {
        this(classifier, null);
    }

    public DefaultImpactAnalysisService() {
        this(new ConfigItemClassifier(), null);
    }

    @Override
    public ChangeImpactAnalysis analyze(String commitHash, String commitMessage, String authorName,
                                        Collection<String> changedFiles, @Nullable String diffContent) {
        List<ConfigItem> directlyChanged = classifier.classifyPaths(changedFiles);
        ChangeTypeCategory category = detectCategory(commitMessage, changedFiles);

        ChatModel model = getChatModel();
        if (model != null) {
            try {
                ChangeImpactAnalysis aiAnalysis = analyzeWithAi(model, commitHash, commitMessage, authorName, directlyChanged, diffContent);
                if (aiAnalysis != null) {
                    return aiAnalysis;
                }
            } catch (Exception e) {
                logger.warn("Error analizando commit con IA (se usará motor heurístico de respaldo): {}", e.getMessage());
            }
        }

        return analyzeHeuristically(commitHash, commitMessage, authorName, category, directlyChanged);
    }

    @Nullable
    private ChatModel getChatModel() {
        if (settingService != null && settingService.getAiSetting() != null) {
            return settingService.getAiSetting().getLiteModel();
        }
        return null;
    }

    private ChangeImpactAnalysis analyzeWithAi(ChatModel model, String commitHash, String commitMessage,
                                               String authorName, List<ConfigItem> directlyChanged,
                                               @Nullable String diffContent) {
        String systemPrompt = """
            Eres un arquitecto de software y experto en SCM y trazabilidad de cambios.
            Analiza el cambio introducido por un commit en el repositorio y determina qué otros elementos de configuración se ven impactados.
            Los 7 Elementos de Configuración posibles son:
            - SOURCE_CODE (Código Fuente)
            - ARCHITECTURE_WIKI (Documentación de Arquitectura y Wikis)
            - DATA_MODEL_ERD (Modelos de datos, esquemas SQL y diagramas ERD)
            - REQUIREMENT (Especificaciones de Requisitos Funcionales RTM)
            - PROJECT_TASK (Tareas y tickets del plan de proyecto)
            - INFRASTRUCTURE_IAC (Infraestructura como código: Docker, Terraform, CI/CD)
            - ADR (Decisiones Técnicas de Arquitectura)

            Responde ÚNICAMENTE con un objeto JSON válido con la siguiente estructura:
            {
              "category": "FEATURE|FIX|REFACTOR|DOCUMENTATION|DATA_MODEL|INFRASTRUCTURE|CHORE",
              "summary": "Resumen conciso en una frase de qué hace el cambio",
              "impactedItems": [
                {
                  "targetType": "NOMBRE_DEL_TIPO_ENUM",
                  "suggestedPath": "ruta/sugerida/para/actualizar.md",
                  "reason": "Por qué este elemento se ve afectado por el cambio",
                  "suggestedAction": "Acción específica recomendada que debe realizarse",
                  "proposedContent": "Borrador de texto o diagrama sugerido (opcional)"
                }
              ]
            }
            No incluyas texto explicativo antes ni después del JSON.
            """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Commit: ").append(commitHash).append("\n");
        userPrompt.append("Autor: ").append(authorName).append("\n");
        userPrompt.append("Mensaje: ").append(commitMessage).append("\n");
        userPrompt.append("Archivos modificados:\n");
        for (ConfigItem item : directlyChanged) {
            userPrompt.append("- [").append(item.getType()).append("] ").append(item.getPath()).append("\n");
        }
        if (diffContent != null && !diffContent.trim().isEmpty()) {
            String truncatedDiff = diffContent.length() > 3000 ? diffContent.substring(0, 3000) + "\n...[truncado]" : diffContent;
            userPrompt.append("\nDiff:\n").append(truncatedDiff);
        }

        var response = model.chat(new SystemMessage(systemPrompt), new UserMessage(userPrompt.toString()));
        String text = response.aiMessage().text();
        return parseAiResponse(text, commitHash, commitMessage, authorName, directlyChanged);
    }

    public ChangeImpactAnalysis parseAiResponse(String rawText, String commitHash, String commitMessage,
                                                String authorName, List<ConfigItem> directlyChanged) {
        String cleanJson = cleanMarkdownJson(rawText);
        JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();

        String categoryStr = root.has("category") ? root.get("category").getAsString() : "FEATURE";
        ChangeTypeCategory category;
        try {
            category = ChangeTypeCategory.valueOf(categoryStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            List<String> paths = new ArrayList<String>();
            for (ConfigItem ci : directlyChanged) {
                paths.add(ci.getPath());
            }
            category = detectCategory(commitMessage, paths);
        }

        String summary = root.has("summary") ? root.get("summary").getAsString() : commitMessage;
        List<ImpactedItem> impactedItems = new ArrayList<ImpactedItem>();

        if (root.has("impactedItems") && root.get("impactedItems").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("impactedItems");
            for (JsonElement el : array) {
                if (el.isJsonObject()) {
                    JsonObject itemObj = el.getAsJsonObject();
                    String typeStr = itemObj.has("targetType") ? itemObj.get("targetType").getAsString() : "ARCHITECTURE_WIKI";
                    ConfigItemType targetType;
                    try {
                        targetType = ConfigItemType.valueOf(typeStr.toUpperCase(Locale.ROOT));
                    } catch (Exception e) {
                        targetType = ConfigItemType.ARCHITECTURE_WIKI;
                    }
                    String path = itemObj.has("suggestedPath") ? itemObj.get("suggestedPath").getAsString() : "docs/architecture/update.md";
                    String reason = itemObj.has("reason") ? itemObj.get("reason").getAsString() : "Impacto detectado por IA";
                    String action = itemObj.has("suggestedAction") ? itemObj.get("suggestedAction").getAsString() : "Revisar y actualizar";
                    String proposedContent = itemObj.has("proposedContent") && !itemObj.get("proposedContent").isJsonNull() 
                        ? itemObj.get("proposedContent").getAsString() : null;

                    impactedItems.add(new ImpactedItem(targetType, path, reason, action, proposedContent));
                }
            }
        }

        return new ChangeImpactAnalysis(commitHash, commitMessage, authorName, category, summary, directlyChanged, impactedItems, true);
    }

    private ChangeImpactAnalysis analyzeHeuristically(String commitHash, String commitMessage, String authorName,
                                                      ChangeTypeCategory category, List<ConfigItem> directlyChanged) {
        List<ImpactedItem> impacted = new ArrayList<ImpactedItem>();
        String summary = "Cambio (" + category.getDisplayName() + "): " + commitMessage;

        boolean hasSource = false;
        boolean hasDataModel = false;
        boolean hasInfra = false;
        boolean hasRequirement = false;
        boolean hasAdr = false;
        boolean hasWiki = false;

        for (ConfigItem item : directlyChanged) {
            if (item.getType() == ConfigItemType.SOURCE_CODE) hasSource = true;
            if (item.getType() == ConfigItemType.DATA_MODEL_ERD) hasDataModel = true;
            if (item.getType() == ConfigItemType.INFRASTRUCTURE_IAC) hasInfra = true;
            if (item.getType() == ConfigItemType.REQUIREMENT) hasRequirement = true;
            if (item.getType() == ConfigItemType.ADR) hasAdr = true;
            if (item.getType() == ConfigItemType.ARCHITECTURE_WIKI) hasWiki = true;
        }

        if (hasDataModel && !hasWiki) {
            impacted.add(new ImpactedItem(
                ConfigItemType.DATA_MODEL_ERD,
                "docs/database/erd.md",
                "Se modificó el esquema de base de datos o migraciones sin actualizar la documentación del ERD",
                "Actualizar el diagrama de Entidad-Relación (ERD) con las nuevas tablas/columnas"
            ));
        }

        if (hasSource && category == ChangeTypeCategory.FEATURE) {
            if (!hasRequirement) {
                impacted.add(new ImpactedItem(
                    ConfigItemType.REQUIREMENT,
                    "docs/requirements/RTM.md",
                    "Se introdujo nueva funcionalidad en código sin vincular su especificación funcional",
                    "Vincular el commit con la Matriz de Requisitos (RTM) o agregar especificación en docs/requirements/"
                ));
            }
            if (!hasAdr) {
                impacted.add(new ImpactedItem(
                    ConfigItemType.ADR,
                    "docs/adr/ADR-next.md",
                    "Se implementó una nueva característica que podría involucrar decisiones de arquitectura",
                    "Evaluar si se requiere redactar un nuevo Architecture Decision Record (ADR)"
                ));
            }
        }

        if (hasInfra && !hasWiki) {
            impacted.add(new ImpactedItem(
                ConfigItemType.ARCHITECTURE_WIKI,
                "docs/architecture/infrastructure.md",
                "Se modificaron archivos de infraestructura (Docker, CI/CD o IaC)",
                "Documentar cambios en la arquitectura de despliegue en la Wiki"
            ));
        }

        return new ChangeImpactAnalysis(commitHash, commitMessage, authorName, category, summary, directlyChanged, impacted, false);
    }

    @Override
    public ChangeTypeCategory detectCategory(String commitMessage, Collection<String> changedFiles) {
        String msg = commitMessage.toLowerCase(Locale.ROOT).trim();

        if (msg.startsWith("feat") || msg.contains("feature") || msg.contains("implement") || msg.contains("nueva")) {
            return ChangeTypeCategory.FEATURE;
        }
        if (msg.startsWith("fix") || msg.contains("bug") || msg.contains("error") || msg.contains("corregir") || msg.contains("patch")) {
            return ChangeTypeCategory.FIX;
        }
        if (msg.startsWith("refactor") || msg.contains("clean") || msg.contains("reestructurar")) {
            return ChangeTypeCategory.REFACTOR;
        }
        if (msg.startsWith("docs") || msg.contains("documentation") || msg.contains("wiki") || msg.contains("documentar")) {
            return ChangeTypeCategory.DOCUMENTATION;
        }

        boolean allData = !changedFiles.isEmpty();
        for (String f : changedFiles) {
            if (!(f.endsWith(".sql") || f.contains("migration") || f.endsWith(".prisma") || f.endsWith(".erd"))) {
                allData = false;
                break;
            }
        }
        if (allData) {
            return ChangeTypeCategory.DATA_MODEL;
        }

        boolean allInfra = !changedFiles.isEmpty();
        for (String f : changedFiles) {
            if (!(f.contains("Dockerfile") || f.contains("docker-compose") || f.endsWith(".tf") || f.contains("k8s"))) {
                allInfra = false;
                break;
            }
        }
        if (allInfra) {
            return ChangeTypeCategory.INFRASTRUCTURE;
        }

        return ChangeTypeCategory.FEATURE;
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