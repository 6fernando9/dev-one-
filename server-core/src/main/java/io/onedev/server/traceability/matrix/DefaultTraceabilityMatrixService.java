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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * Implementación predeterminada del servicio de Matriz de Trazabilidad Dinámica y Bidireccional (RF4)
 * e Inventario de Elementos de Configuración (RF1).
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
        Map<String, String> commitMessages = new HashMap<>();
        Map<String, String> requirementContents = new HashMap<>();

        // 1. Escanear árbol Git del repositorio en la revisión especificada
        if (projectService != null) {
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
                                if (item.getType() == ConfigItemType.REQUIREMENT) {
                                    try {
                                        byte[] bytes = repo.open(blobId).getBytes();
                                        String docText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                        requirementContents.put(item.getIdentifier(), docText);
                                        requirementContents.put(item.getPath(), docText);
                                    } catch (Exception ignored) {}
                                }
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
        }

        // 2. Escanear Issues de OneDev para tareas de proyecto
        if (issueService != null) {
            try {
                List<Issue> issues = issueService.queryAfter(project.getId(), 0L, 500);
                for (Issue issue : issues) {
                    ConfigItem issueItem = classifier.classifyIssue(issue.getNumber(), issue.getTitle(), Collections.emptyList());
                    allItems.add(issueItem);
                }
            } catch (Exception e) {
                logger.warn("No se pudieron cargar issues para trazabilidad en proyecto {}: {}", project.getName(), e.getMessage());
            }
        }

        return buildMatrixFromItems(project.getId(), branch, allItems, commitMessages, requirementContents);
    }

    /**
     * Construye la matriz y los enlaces a partir de los ConfigItems recopilados.
     */
    public TraceabilityMatrix buildMatrixFromItems(Long projectId, String revision, List<ConfigItem> allItems, Map<String, String> commitMessages) {
        return buildMatrixFromItems(projectId, revision, allItems, commitMessages, Collections.emptyMap());
    }

    /**
     * Construye la matriz y los enlaces considerando contenidos explícitos de requisitos.
     */
    public TraceabilityMatrix buildMatrixFromItems(Long projectId, String revision, List<ConfigItem> allItems, Map<String, String> commitMessages, Map<String, String> requirementContents) {
        List<ConfigItem> requirements = new ArrayList<>();
        List<ConfigItem> tasks = new ArrayList<>();
        List<ConfigItem> sourceFiles = new ArrayList<>();
        List<ConfigItem> testSpecs = new ArrayList<>();
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
                case TEST_SPEC:
                    testSpecs.add(item);
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
            Integer reqNum = extractNumber(reqId);
            if (reqNum == null) {
                reqNum = extractNumber(req.getPath());
            }

            String reqDoc = requirementContents != null ? requirementContents.get(req.getIdentifier()) : null;
            if (reqDoc == null && requirementContents != null) {
                reqDoc = requirementContents.get(req.getPath());
            }
            String reqDocLower = reqDoc != null ? reqDoc.toLowerCase(Locale.ROOT) : "";

            Set<String> keywords = new HashSet<>();
            keywords.add(reqIdLower);
            if (reqNum != null) {
                keywords.add("rf" + reqNum);
                keywords.add("rf-" + reqNum);
                keywords.add("rf-0" + reqNum);
                keywords.add("rf-00" + reqNum);
                keywords.add("adr-" + reqNum);
                keywords.add("adr-0" + reqNum);
                keywords.add("adr-00" + reqNum);
            }

            // Extraer palabras clave del nombre de archivo (ej. RF-01-Autenticacion.md -> "autenticacion")
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
                    }
                }
            }

            // Expansión de sinónimos y conceptos del dominio de carpintería y negocio
            expandDomainKeywords(keywords, reqNum);

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

            // 2. Vincular ADRs (Por número correlacionado o coincidencia semántica)
            for (ConfigItem adr : adrs) {
                Integer adrNum = extractNumber(adr.getIdentifier());
                if (adrNum == null) adrNum = extractNumber(adr.getPath());

                boolean matches = false;
                if (reqNum != null && adrNum != null && reqNum.equals(adrNum)) {
                    matches = true;
                } else {
                    String adrText = (adr.getIdentifier() + " " + adr.getTitle() + " " + adr.getPath()).toLowerCase(Locale.ROOT);
                    matches = matchesAnyKeyword(adrText, keywords);
                }

                if (!matches && !reqDocLower.isEmpty()) {
                    String adrIdLower = adr.getIdentifier().toLowerCase(Locale.ROOT);
                    String adrPathLower = adr.getPath().toLowerCase(Locale.ROOT);
                    if (reqDocLower.contains(adrPathLower) || reqDocLower.contains(adrIdLower)) {
                        matches = true;
                    }
                }

                if (matches) {
                    linkedAdrs.add(adr);
                    links.add(new TraceabilityLink(adr, req, TraceabilityLinkType.DECIDES, "Decisión técnica para " + reqId));
                }
            }

            // 3. Vincular Modelos de Datos (ERD/SQL)
            for (ConfigItem dm : dataModels) {
                String dmText = (dm.getIdentifier() + " " + dm.getTitle() + " " + dm.getPath()).toLowerCase(Locale.ROOT);
                boolean matches = matchesAnyKeyword(dmText, keywords);

                // Si es el script principal de base de datos del proyecto, respalda los requisitos de negocio
                if (!matches && (dm.getPath().toLowerCase(Locale.ROOT).contains("scriptbasededatos") || dm.getPath().toLowerCase(Locale.ROOT).contains("schema.sql"))) {
                    matches = true;
                }

                if (!matches && !reqDocLower.isEmpty()) {
                    String dmIdLower = dm.getIdentifier().toLowerCase(Locale.ROOT);
                    String dmPathLower = dm.getPath().toLowerCase(Locale.ROOT);
                    if (reqDocLower.contains(dmPathLower) || reqDocLower.contains(dmIdLower)) {
                        matches = true;
                    }
                }

                if (matches) {
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

            // 5. Vincular Código Fuente (Clases, controladores y módulos)
            for (ConfigItem src : sourceFiles) {
                String srcText = (src.getIdentifier() + " " + src.getPath()).toLowerCase(Locale.ROOT);
                boolean matches = matchesAnyKeyword(srcText, keywords);

                if (!matches && !reqDocLower.isEmpty()) {
                    String srcIdLower = src.getIdentifier().toLowerCase(Locale.ROOT);
                    String srcPathLower = src.getPath().toLowerCase(Locale.ROOT);
                    String cleanIdLower = srcIdLower.startsWith("src:") ? srcIdLower.substring(4) : srcIdLower;
                    String cleanClassLower = cleanIdLower.endsWith(".java") ? cleanIdLower.substring(0, cleanIdLower.length() - 5) : cleanIdLower;
                    String srcTitleLower = src.getTitle() != null ? src.getTitle().toLowerCase(Locale.ROOT) : "";
                    if (reqDocLower.contains(srcPathLower) || reqDocLower.contains(cleanIdLower) || 
                        (!srcTitleLower.isEmpty() && reqDocLower.contains(srcTitleLower)) || 
                        (cleanClassLower.length() >= 4 && reqDocLower.contains(cleanClassLower))) {
                        matches = true;
                    }
                }

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

            // Determinar estado de sincronización de la fila
            TraceabilitySyncStatus status;
            String note;

            if (linkedSources.isEmpty()) {
                status = TraceabilitySyncStatus.DRIFT_UNLINKED;
                note = "Requisito sin código fuente vinculado (no implementado o desincronizado).";
            } else if (linkedAdrs.isEmpty() && linkedModels.isEmpty()) {
                status = TraceabilitySyncStatus.PARTIAL;
                note = "Implementación parcial: código presente pero sin ADR o modelo de datos formal.";
            } else {
                status = TraceabilitySyncStatus.SYNCHRONIZED;
                note = "Totalmente sincronizado con especificación, código fuente y arquitectura.";
            }

            TraceabilityRow row = new TraceabilityRow(
                req,
                linkedTasks,
                linkedSources,
                linkedAdrs,
                linkedModels,
                linkedArch,
                linkedInfra,
                status,
                note
            );
            rows.add(row);
        }

        // Trazabilidad Hacia Atrás: Detección de Código Huérfano (Drift)
        for (ConfigItem src : sourceFiles) {
            if (!linkedSourcePaths.contains(src.getPath())) {
                TraceabilityRow orphanRow = new TraceabilityRow(
                    src,
                    Collections.emptyList(),
                    Collections.singletonList(src),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    TraceabilitySyncStatus.DRIFT_UNLINKED,
                    "Código fuente huérfano (no vinculado a ningún requisito funcional o tarea)"
                );
                rows.add(orphanRow);
            }
        }

        return new TraceabilityMatrix(projectId, revision, rows, links, allItems);
    }

    private void expandDomainKeywords(Set<String> keywords, @Nullable Integer reqNum) {
        boolean hasAuth = false;
        boolean hasCatalog = false;
        boolean hasOrders = false;
        boolean hasSales = false;
        boolean hasReports = false;

        for (String kw : keywords) {
            if (kw.contains("autentica") || kw.contains("auth") || kw.contains("login") || kw.contains("seguridad") || (reqNum != null && reqNum == 1)) {
                hasAuth = true;
            }
            if (kw.contains("catalogo") || kw.contains("inventario") || (reqNum != null && reqNum == 2)) {
                hasCatalog = true;
            }
            if (kw.contains("cotizacion") || kw.contains("pedido") || (reqNum != null && reqNum == 3)) {
                hasOrders = true;
            }
            if (kw.contains("venta") || kw.contains("pago") || (reqNum != null && reqNum == 4)) {
                hasSales = true;
            }
            if (kw.contains("reporte") || kw.contains("informe") || (reqNum != null && reqNum == 5)) {
                hasReports = true;
            }
        }

        if (hasAuth) {
            keywords.add("usuario");
            keywords.add("permiso");
            keywords.add("rol");
            keywords.add("token");
            keywords.add("sesion");
            keywords.add("seguridad");
            keywords.add("auth");
            keywords.add("login");
        }
        if (hasCatalog) {
            keywords.add("producto");
            keywords.add("insumo");
            keywords.add("tipo");
            keywords.add("catalogo");
            keywords.add("inventario");
        }
        if (hasOrders) {
            keywords.add("cotizacion");
            keywords.add("pedido");
            keywords.add("detallecotizacion");
            keywords.add("detallepedido");
            keywords.add("cliente");
            keywords.add("carpintero");
        }
        if (hasSales) {
            keywords.add("venta");
            keywords.add("pago");
            keywords.add("ventaui");
        }
        if (hasReports) {
            keywords.add("reporte");
            keywords.add("report");
        }
    }

    private Integer extractNumber(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)(?:rf|adr|req)?[-_\\s]*0*([1-9][0-9]*)").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean matchesAnyKeyword(String target, Set<String> keywords) {
        if (target == null || keywords == null || keywords.isEmpty()) return false;
        String lower = target.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (kw.length() >= 3 && lower.contains(kw)) {
                return true;
            }
        }
        return false;
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
                for (ConfigItem adr : row.getAdrs()) {
                    if (adr.getIdentifier().equalsIgnoreCase(itemIdentifierOrPath) || adr.getPath().equalsIgnoreCase(itemIdentifierOrPath)) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                origins.add(row.getPrimaryItem());
            }
        }
        return origins;
    }

    private List<TraceabilityRow> getOrphanSources(TraceabilityMatrix matrix) {
        return matrix.getRows().stream()
            .filter(r -> r.getPrimaryItem().getType() != ConfigItemType.REQUIREMENT)
            .collect(Collectors.toList());
    }

    @Override
    public String exportToCsv(TraceabilityMatrix matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID Elemento,Tipo,Titulo,Estado,Tareas / Issues,Codigo Fuente,ADRs,Modelos / ERD,Diagnostico\n");

        for (TraceabilityRow row : matrix.getRows()) {
            ConfigItem pi = row.getPrimaryItem();
            sb.append(escapeCsv(pi.getIdentifier())).append(",");
            sb.append(escapeCsv(pi.getType().getDisplayName())).append(",");
            sb.append(escapeCsv(pi.getTitle())).append(",");
            sb.append(escapeCsv(row.getStatus().getDisplayName())).append(",");

            String tasks = row.getTasks().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining("; "));
            sb.append(escapeCsv(tasks)).append(",");

            String sources = row.getSourceFiles().stream().map(ConfigItem::getPath).collect(Collectors.joining("; "));
            sb.append(escapeCsv(sources)).append(",");

            String adrs = row.getAdrs().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining("; "));
            sb.append(escapeCsv(adrs)).append(",");

            String models = row.getDataModels().stream().map(ConfigItem::getPath).collect(Collectors.joining("; "));
            sb.append(escapeCsv(models)).append(",");

            sb.append(escapeCsv(row.getNotes())).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String exportToMarkdown(TraceabilityMatrix matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Matriz de Trazabilidad de Requisitos (RTM)\n\n");
        sb.append("**Proyecto ID:** ").append(matrix.getProjectId()).append(" | ");
        sb.append("**Rama / Revisión:** `").append(matrix.getRevision()).append("` | ");
        sb.append("**Generado:** ").append(matrix.getCalculatedAt()).append("\n\n");

        sb.append("## Métricas Generales de Cobertura\n\n");
        sb.append("- **Total Requisitos:** ").append(matrix.getTotalRequirements()).append("\n");
        sb.append("- **Cobertura Sincronizada:** ").append(String.format(Locale.US, "%.1f%%", matrix.getCoveragePercentage())).append("\n");
        sb.append("- **Requisitos Sincronizados:** ").append(matrix.getSynchronizedCount()).append("\n");
        sb.append("- **Requisitos Parciales:** ").append(matrix.getPartialCount()).append("\n");
        sb.append("- **Desfases (Drift):** ").append(matrix.getDriftCount()).append("\n");
        sb.append("- **Archivos Huérfanos:** ").append(matrix.getOrphanCount()).append("\n\n");

        sb.append("## Matriz Bidireccional de Requisitos\n\n");
        sb.append("| ID Requisito | Título / Especificación | Estado | Tareas (Issues) | Código Fuente | ADR / Decisión | Modelo / BD | Diagnóstico |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");

        for (TraceabilityRow row : matrix.getRows()) {
            if (row.getPrimaryItem().getType() == ConfigItemType.REQUIREMENT) {
                ConfigItem req = row.getPrimaryItem();
                String statusIcon = row.getStatus() == TraceabilitySyncStatus.SYNCHRONIZED ? "🟢 Sincronizado" :
                    (row.getStatus() == TraceabilitySyncStatus.PARTIAL ? "🟡 Parcial" : "🔴 Desfase");

                String tasks = row.getTasks().isEmpty() ? "-" :
                    row.getTasks().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining(", "));
                String sources = row.getSourceFiles().isEmpty() ? "-" :
                    row.getSourceFiles().stream().map(ConfigItem::getPath).collect(Collectors.joining("<br>"));
                String adrs = row.getAdrs().isEmpty() ? "-" :
                    row.getAdrs().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining(", "));
                String models = row.getDataModels().isEmpty() ? "-" :
                    row.getDataModels().stream().map(ConfigItem::getPath).collect(Collectors.joining("<br>"));

                sb.append("| ").append(req.getIdentifier())
                  .append(" | ").append(req.getTitle())
                  .append(" | ").append(statusIcon)
                  .append(" | ").append(tasks)
                  .append(" | ").append(sources)
                  .append(" | ").append(adrs)
                  .append(" | ").append(models)
                  .append(" | ").append(row.getNotes()).append(" |\n");
            }
        }

        List<TraceabilityRow> orphans = getOrphanSources(matrix);
        if (!orphans.isEmpty()) {
            sb.append("\n## Código Fuente Huérfano (Desfase Detectado)\n\n");
            sb.append("| Archivo de Código | Diagnóstico de Trazabilidad |\n");
            sb.append("|---|---|\n");
            for (TraceabilityRow orphan : orphans) {
                sb.append("| `").append(orphan.getPrimaryItem().getPath()).append("` | ").append(orphan.getNotes()).append(" |\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String exportToJson(TraceabilityMatrix matrix) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(matrix);
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
