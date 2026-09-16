package io.onedev.server.traceability.matrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.onedev.server.git.GitUtils;
import io.onedev.server.git.service.GitService;
import io.onedev.server.model.Issue;
import io.onedev.server.model.Project;
import io.onedev.server.service.IssueService;
import io.onedev.server.service.ProjectService;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemClassifier;
import io.onedev.server.traceability.ConfigItemType;

/**
 * Implementación predeterminada del servicio de Matriz de Trazabilidad Dinámica y Bidireccional (RF4).
 */
@Singleton
public class DefaultTraceabilityMatrixService implements TraceabilityMatrixService, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DefaultTraceabilityMatrixService.class);

    private final ConfigItemClassifier classifier;
    private final ProjectService projectService;
    private final GitService gitService;
    private final IssueService issueService;

    @Inject
    public DefaultTraceabilityMatrixService(ConfigItemClassifier classifier,
                                            ProjectService projectService,
                                            GitService gitService,
                                            IssueService issueService) {
        this.classifier = classifier;
        this.projectService = projectService;
        this.gitService = gitService;
        this.issueService = issueService;
    }

    @Override
    public TraceabilityMatrix buildMatrix(Project project, @Nullable String revision) {
        String branch = revision != null ? revision : (project.getDefaultBranch() != null ? project.getDefaultBranch() : "master");

        List<ConfigItem> allItems = new ArrayList<>();
        Map<String, List<String>> commitToChangedFiles = new HashMap<>();
        Map<String, String> commitMessages = new HashMap<>();

        // 1. Escanear árbol Git del repositorio en la revisión especificada
        try (Repository repo = projectService.getRepository(project.getId())) {
            ObjectId revId = gitService.resolve(project, branch, false);
            if (revId == null) {
                revId = gitService.resolve(project, Constants.R_HEADS + branch, false);
            }

            if (revId != null) {
                try (RevWalk revWalk = new RevWalk(repo)) {
                    RevCommit commit = revWalk.parseCommit(revId);
                    try (TreeWalk treeWalk = new TreeWalk(repo)) {
                        treeWalk.addTree(commit.getTree());
                        treeWalk.setRecursive(true);
                        while (treeWalk.next()) {
                            String path = treeWalk.getPathString();
                            ObjectId blobId = treeWalk.getObjectId(0);
                            ConfigItem item = classifier.classifyPath(path, blobId.name());
                            allItems.add(item);
                        }
                    }

                    // Analizar commits recientes (hasta 50) para rastreo por mensajes
                    revWalk.markStart(commit);
                    int count = 0;
                    for (RevCommit rev : revWalk) {
                        if (++count > 50) break;
                        String fullMsg = rev.getFullMessage();
                        commitMessages.put(rev.name(), fullMsg);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo escanear el repositorio Git para trazabilidad en proyecto {}: {}", project.getName(), e.getMessage());
        }

        // 2. Escanear Issues de OneDev para tareas de proyecto
        try {
            List<Issue> issues = issueService.queryAfter(project.getId(), 0L, 500);
            for (Issue issue : issues) {
                ConfigItem issueItem = classifier.classifyIssue(issue.getNumber(), issue.getTitle(), Collections.emptyList());
                allItems.add(issueItem);
            }
        } catch (Exception e) {
            logger.warn("No se pudieron cargar issues para trazabilidad en proyecto {}: {}", project.getName(), e.getMessage());
        }

        return buildMatrixFromItems(project.getId(), branch, allItems, commitMessages);
    }

    /**
     * Construye la matriz y los enlaces a partir de los ConfigItems recopilados.
     */
    public TraceabilityMatrix buildMatrixFromItems(Long projectId, String revision, List<ConfigItem> allItems, Map<String, String> commitMessages) {
        List<ConfigItem> requirements = new ArrayList<>();
        List<ConfigItem> tasks = new ArrayList<>();
        List<ConfigItem> sourceFiles = new ArrayList<>();
        List<ConfigItem> adrs = new ArrayList<>();
        List<ConfigItem> dataModels = new ArrayList<>();
        List<ConfigItem> architectureDocs = new ArrayList<>();
        List<ConfigItem> infrastructure = new ArrayList<>();

        for (ConfigItem item : allItems) {
            switch (item.getType()) {
                case REQUIREMENT:
                    requirements.add(item);
                    break;
                case PROJECT_TASK:
                    tasks.add(item);
                    break;
                case SOURCE_CODE:
                    sourceFiles.add(item);
                    break;
                case ADR:
                    adrs.add(item);
                    break;
                case DATA_MODEL_ERD:
                    dataModels.add(item);
                    break;
                case ARCHITECTURE_WIKI:
                    architectureDocs.add(item);
                    break;
                case INFRASTRUCTURE_IAC:
                    infrastructure.add(item);
                    break;
            }
        }

        List<TraceabilityRow> rows = new ArrayList<>();
        List<TraceabilityLink> links = new ArrayList<>();
        Set<String> linkedSourcePaths = new HashSet<>();

        // Construir fila por cada Requisito Funcional (Trazabilidad Hacia Adelante)
        for (ConfigItem req : requirements) {
            String reqId = req.getIdentifier().toUpperCase(Locale.ROOT);
            String reqIdLower = reqId.toLowerCase(Locale.ROOT);
            String reqNum = reqIdLower.replace("rf-", "").replace("req:", "").trim();

            Set<String> keywords = new HashSet<>();
            keywords.add(reqIdLower);
            if (!reqNum.isEmpty()) {
                keywords.add("rf" + reqNum);
                keywords.add("rf-" + reqNum);
            }

            // Extraer palabras clave del nombre de archivo (ej. RF-01-auth.md -> "auth")
            String fileName = req.getPath();
            int slash = fileName.lastIndexOf('/');
            if (slash >= 0) fileName = fileName.substring(slash + 1);
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) fileName = fileName.substring(0, dot);
            for (String token : fileName.split("[-_\\s\\.]+")) {
                token = token.trim().toLowerCase(Locale.ROOT);
                if (token.length() >= 3 && !token.equals("docs") && !token.equals("requirements") && !token.equals("requisito") && !token.equals("rtm")) {
                    keywords.add(token);
                }
            }

            // Extraer palabras clave del título del requisito
            if (req.getTitle() != null) {
                for (String token : req.getTitle().split("[-_\\s\\.]+")) {
                    token = token.trim().toLowerCase(Locale.ROOT);
                    if (token.length() >= 4 && !token.equals("requisito") && !token.equals("funcional") && !token.equals("especificacion") && !token.equals("para")) {
                        keywords.add(token);
                        if (token.startsWith("usuario")) {
                            keywords.add("user");
                            keywords.add("users");
                        }
                        if (token.startsWith("autentica")) {
                            keywords.add("auth");
                            keywords.add("login");
                        }
                    }
                }
            }

            List<ConfigItem> linkedTasks = new ArrayList<>();
            List<ConfigItem> linkedSources = new ArrayList<>();
            List<ConfigItem> linkedAdrs = new ArrayList<>();
            List<ConfigItem> linkedModels = new ArrayList<>();
            List<ConfigItem> linkedArch = new ArrayList<>();
            List<ConfigItem> linkedInfra = new ArrayList<>();

            // 1. Vincular Tareas (Issues)
            for (ConfigItem task : tasks) {
                String taskText = (task.getIdentifier() + " " + task.getTitle() + " " + task.getPath()).toLowerCase(Locale.ROOT);
                if (matchesAnyKeyword(taskText, keywords)) {
                    linkedTasks.add(task);
                    links.add(new TraceabilityLink(task, req, TraceabilityLinkType.SPECIFIES, "Tarea vinculada con " + reqId));
                }
            }

            // 2. Vincular ADRs
            for (ConfigItem adr : adrs) {
                String adrText = (adr.getIdentifier() + " " + adr.getTitle() + " " + adr.getPath()).toLowerCase(Locale.ROOT);
                if (matchesAnyKeyword(adrText, keywords)) {
                    linkedAdrs.add(adr);
                    links.add(new TraceabilityLink(adr, req, TraceabilityLinkType.DECIDES, "Decisión técnica para " + reqId));
                }
            }

            // 3. Vincular Modelos de Datos (ERD/SQL)
            for (ConfigItem dm : dataModels) {
                String dmText = (dm.getIdentifier() + " " + dm.getTitle() + " " + dm.getPath()).toLowerCase(Locale.ROOT);
                if (matchesAnyKeyword(dmText, keywords)) {
                    linkedModels.add(dm);
                    links.add(new TraceabilityLink(dm, req, TraceabilityLinkType.MODELS, "Esquema de datos para " + reqId));
                }
            }

            // 4. Vincular Arquitectura / Wiki
            for (ConfigItem arch : architectureDocs) {
                String archText = (arch.getIdentifier() + " " + arch.getTitle() + " " + arch.getPath()).toLowerCase(Locale.ROOT);
                if (matchesAnyKeyword(archText, keywords)) {
                    linkedArch.add(arch);
                    links.add(new TraceabilityLink(arch, req, TraceabilityLinkType.RELATED_TO, "Documento de arquitectura para " + reqId));
                }
            }

            // 5. Vincular Código Fuente
            for (ConfigItem src : sourceFiles) {
                String srcText = (src.getIdentifier() + " " + src.getPath()).toLowerCase(Locale.ROOT);
                boolean matches = matchesAnyKeyword(srcText, keywords);

                // También revisar mensajes de commits recientes
                if (!matches && commitMessages != null) {
                    for (String msg : commitMessages.values()) {
                        String lowerMsg = msg.toLowerCase(Locale.ROOT);
                        if (matchesAnyKeyword(lowerMsg, keywords) && lowerMsg.contains(src.getIdentifier().toLowerCase(Locale.ROOT))) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    linkedSources.add(src);
                    linkedSourcePaths.add(src.getPath());
                    links.add(new TraceabilityLink(src, req, TraceabilityLinkType.IMPLEMENTS, "Código que implementa " + reqId));
                }
            }

            // 6. Vincular Infraestructura
            for (ConfigItem infra : infrastructure) {
                String infraText = (infra.getIdentifier() + " " + infra.getTitle() + " " + infra.getPath()).toLowerCase(Locale.ROOT);
                if (matchesAnyKeyword(infraText, keywords)) {
                    linkedInfra.add(infra);
                    links.add(new TraceabilityLink(infra, req, TraceabilityLinkType.DEPLOYS, "Infraestructura para " + reqId));
                }
            }

            // Calcular estado de sincronización de la fila
            TraceabilitySyncStatus status;
            StringBuilder notes = new StringBuilder();

            if (!linkedSources.isEmpty()) {
                if (!linkedAdrs.isEmpty() || !linkedArch.isEmpty()) {
                    status = TraceabilitySyncStatus.SYNCHRONIZED;
                    notes.append("Cobertura completa.");
                } else {
                    status = TraceabilitySyncStatus.PARTIAL;
                    notes.append("Implementado en código; carece de ADR o documentación técnica formal.");
                }
            } else {
                status = TraceabilitySyncStatus.DRIFT_UNLINKED;
                notes.append("Requisito sin código fuente vinculado (no implementado o desincronizado).");
            }

            rows.add(new TraceabilityRow(req, linkedTasks, linkedSources, linkedAdrs, linkedModels, linkedArch, linkedInfra, status, notes.toString()));
        }

        // Sección Inferior: Identificar Código Huérfano (sin ningún requisito asociado)
        for (ConfigItem src : sourceFiles) {
            if (!linkedSourcePaths.contains(src.getPath())) {
                rows.add(new TraceabilityRow(
                    src,
                    Collections.emptyList(),
                    Collections.singletonList(src),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    TraceabilitySyncStatus.DRIFT_UNLINKED,
                    "Código fuente huérfano (no vinculado a ningún requisito funcional o tarea)"
                ));
            }
        }

        return new TraceabilityMatrix(projectId, revision, rows, links);
    }

    @Override
    public List<ConfigItem> getForwardTrace(Project project, String itemIdentifier, @Nullable String revision) {
        TraceabilityMatrix matrix = buildMatrix(project, revision);
        for (TraceabilityRow row : matrix.getRows()) {
            if (row.getPrimaryItem().getIdentifier().equalsIgnoreCase(itemIdentifier)) {
                List<ConfigItem> forwardItems = new ArrayList<>();
                forwardItems.addAll(row.getTasks());
                forwardItems.addAll(row.getSourceFiles());
                forwardItems.addAll(row.getAdrs());
                forwardItems.addAll(row.getDataModels());
                forwardItems.addAll(row.getArchitectureDocs());
                forwardItems.addAll(row.getInfrastructure());
                return forwardItems;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<ConfigItem> getBackwardTrace(Project project, String itemIdentifierOrPath, @Nullable String revision) {
        TraceabilityMatrix matrix = buildMatrix(project, revision);
        List<ConfigItem> origins = new ArrayList<>();

        for (TraceabilityRow row : matrix.getRows()) {
            boolean found = false;
            for (ConfigItem src : row.getSourceFiles()) {
                if (src.getIdentifier().equalsIgnoreCase(itemIdentifierOrPath) || src.getPath().equalsIgnoreCase(itemIdentifierOrPath)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (ConfigItem task : row.getTasks()) {
                    if (task.getIdentifier().equalsIgnoreCase(itemIdentifierOrPath)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                for (ConfigItem dm : row.getDataModels()) {
                    if (dm.getIdentifier().equalsIgnoreCase(itemIdentifierOrPath) || dm.getPath().equalsIgnoreCase(itemIdentifierOrPath)) {
                        found = true;
                        break;
                    }
                }
            }

            if (found && row.getPrimaryItem().getType() == ConfigItemType.REQUIREMENT) {
                origins.add(row.getPrimaryItem());
                origins.addAll(row.getAdrs());
            }
        }

        return origins;
    }

    @Override
    public String exportToCsv(TraceabilityMatrix matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID Elemento,Tipo,Titulo,Estado,Tareas / Issues,Codigo Fuente,ADRs,Modelos de Datos,Arquitectura,Infraestructura,Notas\n");

        for (TraceabilityRow row : matrix.getRows()) {
            ConfigItem primary = row.getPrimaryItem();
            sb.append("\"").append(escapeCsv(primary.getIdentifier())).append("\",");
            sb.append("\"").append(escapeCsv(primary.getType().getDisplayName())).append("\",");
            sb.append("\"").append(escapeCsv(primary.getTitle())).append("\",");
            sb.append("\"").append(escapeCsv(row.getStatus().getDisplayName())).append("\",");
            sb.append("\"").append(escapeCsv(joinIdentifiers(row.getTasks()))).append("\",");
            sb.append("\"").append(escapeCsv(joinPaths(row.getSourceFiles()))).append("\",");
            sb.append("\"").append(escapeCsv(joinIdentifiers(row.getAdrs()))).append("\",");
            sb.append("\"").append(escapeCsv(joinPaths(row.getDataModels()))).append("\",");
            sb.append("\"").append(escapeCsv(joinPaths(row.getArchitectureDocs()))).append("\",");
            sb.append("\"").append(escapeCsv(joinPaths(row.getInfrastructure()))).append("\",");
            sb.append("\"").append(escapeCsv(row.getNotes())).append("\"\n");
        }

        return sb.toString();
    }

    @Override
    public String exportToMarkdown(TraceabilityMatrix matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Matriz de Trazabilidad de Requisitos (RTM)\n\n");
        sb.append("**Proyecto ID:** `").append(matrix.getProjectId()).append("` | ");
        sb.append("**Revisión:** `").append(matrix.getRevision()).append("` | ");
        sb.append("**Generado:** `").append(matrix.getCalculatedAt()).append("`\n\n");

        sb.append("## Métricas Generales de Cobertura\n\n");
        sb.append("| Métrica | Valor |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| **Total Requisitos Funcionales** | ").append(matrix.getTotalRequirements()).append(" |\n");
        sb.append("| **Sincronizados (Completos)** | 🟢 ").append(matrix.getSynchronizedCount()).append(" |\n");
        sb.append("| **Parciales (Sin ADR/Doc)** | 🟡 ").append(matrix.getPartialCount()).append(" |\n");
        sb.append("| **Con Desfase (Drift)** | 🔴 ").append(matrix.getDriftCount()).append(" |\n");
        sb.append("| **Código Huérfano** | ⚠️ ").append(matrix.getOrphanCount()).append(" |\n");
        sb.append("| **% Cobertura** | **").append(String.format(Locale.US, "%.1f%%", matrix.getCoveragePercentage())).append("** |\n");
        sb.append("| **Total Enlaces Bidireccionales** | ").append(matrix.getTotalLinks()).append(" |\n\n");

        sb.append("## Matriz Bidireccional de Requisitos\n\n");
        sb.append("| Requisito | Estado | Tareas / Issues | Código Fuente | ADRs | Modelo Datos / Wiki |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");

        for (TraceabilityRow row : matrix.getRows()) {
            if (row.getPrimaryItem().getType() == ConfigItemType.REQUIREMENT) {
                sb.append("| `").append(row.getPrimaryItem().getIdentifier()).append("` - ").append(row.getPrimaryItem().getTitle()).append(" | ");
                sb.append(row.getStatus() == TraceabilitySyncStatus.SYNCHRONIZED ? "🟢 Sincronizado" :
                          (row.getStatus() == TraceabilitySyncStatus.PARTIAL ? "🟡 Parcial" : "🔴 Drift")).append(" | ");
                sb.append(joinIdentifiers(row.getTasks()).isEmpty() ? "-" : joinIdentifiers(row.getTasks())).append(" | ");
                sb.append(joinPaths(row.getSourceFiles()).isEmpty() ? "-" : joinPaths(row.getSourceFiles())).append(" | ");
                sb.append(joinIdentifiers(row.getAdrs()).isEmpty() ? "-" : joinIdentifiers(row.getAdrs())).append(" | ");
                sb.append(joinPaths(row.getDataModels()).isEmpty() && joinPaths(row.getArchitectureDocs()).isEmpty() ? "-" :
                          (joinPaths(row.getDataModels()) + " " + joinPaths(row.getArchitectureDocs())).trim()).append(" |\n");
            }
        }

        if (matrix.getOrphanCount() > 0) {
            sb.append("\n## Código Fuente Huérfano (Desfase Detectado)\n\n");
            sb.append("| Archivo Huérfano | Diagnóstico |\n");
            sb.append("| :--- | :--- |\n");
            for (TraceabilityRow row : matrix.getRows()) {
                if (row.getPrimaryItem().getType() != ConfigItemType.REQUIREMENT) {
                    sb.append("| `").append(row.getPrimaryItem().getPath()).append("` | ").append(row.getNotes()).append(" |\n");
                }
            }
        }

        return sb.toString();
    }

    @Override
    public String exportToJson(TraceabilityMatrix matrix) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(matrix);
    }

    private boolean matchesAnyKeyword(String targetText, Set<String> keywords) {
        if (targetText == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (targetText.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }

    private String joinIdentifiers(Collection<ConfigItem> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ConfigItem item : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getIdentifier());
        }
        return sb.toString();
    }

    private String joinPaths(Collection<ConfigItem> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ConfigItem item : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getPath());
        }
        return sb.toString();
    }
}