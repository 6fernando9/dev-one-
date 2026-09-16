package io.onedev.server.traceability;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.server.git.BlobContent;
import io.onedev.server.git.BlobEdits;
import io.onedev.server.git.GitUtils;
import io.onedev.server.git.service.GitService;
import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;
import io.onedev.server.model.PullRequestComment;
import io.onedev.server.service.PullRequestCommentService;
import io.onedev.server.service.PullRequestService;
import io.onedev.server.service.UserService;
import io.onedev.server.util.ProjectAndBranch;

/**
 * Implementación predeterminada de ChangeProposalService para RF3.
 * Genera automáticamente ramas de propuesta "proposal-sync-<hash>" y Pull Requests
 * en OneDev con plantillas Markdown enriquecidas y checklist de revisión humana.
 */
@Singleton
public class DefaultChangeProposalService implements ChangeProposalService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultChangeProposalService.class);

    private final GitService gitService;
    private final PullRequestService pullRequestService;
    private final PullRequestCommentService pullRequestCommentService;
    private final UserService userService;

    @Inject
    public DefaultChangeProposalService(GitService gitService,
                                        PullRequestService pullRequestService,
                                        PullRequestCommentService pullRequestCommentService,
                                        UserService userService) {
        this.gitService = gitService;
        this.pullRequestService = pullRequestService;
        this.pullRequestCommentService = pullRequestCommentService;
        this.userService = userService;
    }

    @Override
    public String buildBranchName(String commitHash) {
        String shortHash = commitHash.length() > 8 ? commitHash.substring(0, 8) : commitHash;
        return "proposal-sync-" + shortHash;
    }

    @Override
    public String buildInitialReviewChecklist(String targetBranch) {
        return "### 📋 Checklist de Revisión Humana (*Human-in-the-Loop*)\n\n" +
               "Por favor, como revisor técnico o arquitecto, marca las casillas conforme revises los artefactos propuestos:\n\n" +
               "- [ ] **1. Contenido técnico verificado:** Revisar los archivos generados en esta propuesta y confirmar que corresponden fielmente a la intención del commit origen.\n" +
               "- [ ] **2. Ajustes y refinamiento:** Editar o completar cualquier sección técnica pendiente directamente en este PR o mediante Git.\n" +
               "- [ ] **3. Aprobación y fusión (*Merge*):** Una vez validado, aprobar este Pull Request y fusionarlo a la rama destino (`" + targetBranch + "`) para sincronizar la trazabilidad.\n";
    }

    @Override
    public String buildPullRequestDescription(String targetBranch, RevCommit originCommit, ChangeImpactAnalysis analysis) {
        String shortHash = GitUtils.abbreviateSHA(originCommit.name());
        String authorName = originCommit.getAuthorIdent() != null ? originCommit.getAuthorIdent().getName() : "Desconocido";

        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 Propuesta Automática de Sincronización de Trazabilidad\n\n");
        sb.append("El Sistema de Trazabilidad ha detectado un **desfase (*Drift*)** en los elementos de configuración tras el commit `")
          .append(shortHash).append("` en la rama `").append(targetBranch).append("`.\n\n");

        sb.append("### 📌 Información del Cambio Origen\n");
        sb.append("- **Commit:** `").append(shortHash).append("` - ").append(originCommit.getShortMessage()).append("\n");
        sb.append("- **Autor:** ").append(authorName).append("\n");
        sb.append("- **Categoría:** ").append(analysis.getCategory().getDisplayName()).append("\n");
        sb.append("- **Resumen:** ").append(analysis.getSummary()).append("\n\n");

        sb.append("### ⚠️ Elementos Desfasados Identificados\n");
        sb.append("| Tipo de Elemento | Ruta Propuesta | Justificación del Impacto | Acción Sugerida |\n");
        sb.append("| :--- | :--- | :--- | :--- |\n");
        for (ImpactedItem item : analysis.getImpactedItems()) {
            sb.append("| `").append(item.getTargetType().name()).append("` | ")
              .append(item.getSuggestedPath() != null ? item.getSuggestedPath() : "N/A").append(" | ")
              .append(item.getReason()).append(" | ")
              .append(item.getSuggestedAction()).append(" |\n");
        }
        sb.append("\n");

        sb.append("### 🛠️ Guía de Revisión (*Human-in-the-Loop*)\n");
        sb.append("Esta propuesta incluye borradores para los elementos que requieren actualización correlacionada. Como revisor humano, puedes:\n");
        sb.append("1. Examinar las diferencias en la pestaña **Files changed** de este Pull Request.\n");
        sb.append("2. Modificar o afinar el contenido de los archivos propuestos según la realidad del diseño.\n");
        sb.append("3. Aprobar y hacer **Merge** del Pull Request cuando la documentación y los modelos queden sincronizados.\n");

        return sb.toString();
    }

    @Override
    public String generateDefaultTemplate(ImpactedItem item, String shortHash, String commitMessage, String authorName) {
        ConfigItemType type = item.getTargetType();
        switch (type) {
            case ADR:
                return "# ADR: Propuesta de Decisión Técnica (Commit " + shortHash + ")\n\n" +
                       "## Estado\n" +
                       "Propuesto (*Proposed*)\n\n" +
                       "## Contexto\n" +
                       "Se introdujeron modificaciones en el commit `" + shortHash + "` (\"" + commitMessage + "\") por " + authorName + ".\n" +
                       "**Justificación de Trazabilidad:** " + item.getReason() + "\n\n" +
                       "## Decisión Propuesta\n" +
                       "[Redactar aquí la decisión de diseño, patrón o arquitectura adoptada]\n\n" +
                       "## Consecuencias\n" +
                       "- **Positivas:** [Beneficios técnicos del cambio implementado]\n" +
                       "- **Negativas / Riesgos:** [Compromisos asumidos o consideraciones de mantenimiento]\n\n" +
                       "---\n" +
                       "*Borrador generado automáticamente por el Sistema de Trazabilidad para revisión humana.*";

            case REQUIREMENT:
                return "# Especificación de Requisito: Sincronización con Commit " + shortHash + "\n\n" +
                       "## Metadatos de Trazabilidad\n" +
                       "- **Commit Origen:** `" + shortHash + "` - " + commitMessage + "\n" +
                       "- **Autor del Cambio:** " + authorName + "\n" +
                       "- **Motivo de Sincronización:** " + item.getReason() + "\n" +
                       "- **Acción Recomendada:** " + item.getSuggestedAction() + "\n\n" +
                       "## Especificación Funcional\n" +
                       "### Descripción del Comportamiento\n" +
                       "[Detallar los casos de uso o flujos de usuario soportados por esta implementación]\n\n" +
                       "### Criterios de Aceptación (DoD)\n" +
                       "- [ ] Funcionalidad verificada con pruebas automatizadas.\n" +
                       "- [ ] Documentación técnica actualizada.\n\n" +
                       "---\n" +
                       "*Borrador generado automáticamente por el Sistema de Trazabilidad para revisión humana.*";

            case DATA_MODEL_ERD:
                return "# Modelo de Datos (ERD): Actualización para Commit " + shortHash + "\n\n" +
                       "## Origen del Cambio\n" +
                       "Se detectaron cambios en esquemas de base de datos en el commit `" + shortHash + "`.\n" +
                       "**Motivo:** " + item.getReason() + "\n\n" +
                       "## Diagrama de Entidad-Relación Sugerido (Mermaid)\n" +
                       "```mermaid\n" +
                       "erDiagram\n" +
                       "    %% Actualizar o incorporar las entidades modificadas en este commit\n" +
                       "    TABLA_MODIFICADA {\n" +
                       "        bigint id PK\n" +
                       "        string nombre\n" +
                       "        timestamp created_at\n" +
                       "    }\n" +
                       "```\n\n" +
                       "## Resumen de Cambios en Esquema\n" +
                       "- [Describir nuevas tablas, columnas modificadas o restricciones añadidas]\n\n" +
                       "---\n" +
                       "*Borrador generado automáticamente por el Sistema de Trazabilidad para revisión humana.*";

            case ARCHITECTURE_WIKI:
                return "# Guía de Arquitectura / Wiki: Impacto del Commit " + shortHash + "\n\n" +
                       "## Resumen del Cambio\n" +
                       "- **Commit:** `" + shortHash + "` - " + commitMessage + "\n" +
                       "- **Autor:** " + authorName + "\n" +
                       "- **Justificación:** " + item.getReason() + "\n\n" +
                       "## Impacto en Componentes y Despliegue\n" +
                       "[Documentar el impacto en la arquitectura de software o infraestructura]\n\n" +
                       "---\n" +
                       "*Borrador generado automáticamente por el Sistema de Trazabilidad para revisión humana.*";

            default:
                return "# Actualización de " + type.getDisplayName() + "\n\n" +
                       "- **Commit Origen:** `" + shortHash + "` - " + commitMessage + "\n" +
                       "- **Motivo:** " + item.getReason() + "\n" +
                       "- **Acción Sugerida:** " + item.getSuggestedAction() + "\n\n" +
                       "---\n" +
                       "*Borrador generado automáticamente por el Sistema de Trazabilidad para revisión humana.*";
        }
    }

    @Nullable
    @Override
    public PullRequest proposeChanges(Project project, String targetBranch, RevCommit originCommit, ChangeImpactAnalysis analysis) {
        if (analysis == null || !analysis.hasDrift()) {
            return null;
        }

        if (targetBranch == null || targetBranch.startsWith("proposal-sync-")) {
            return null; // Evitar ciclos recursivos sobre ramas de propuesta
        }

        if (analysis.getImpactedItems().isEmpty()) {
            return null;
        }

        String shortHash = GitUtils.abbreviateSHA(originCommit.name());
        String branchName = buildBranchName(originCommit.name());
        String authorName = originCommit.getAuthorIdent() != null ? originCommit.getAuthorIdent().getName() : "Desconocido";

        ProjectAndBranch target = new ProjectAndBranch(project, targetBranch);
        ProjectAndBranch source = new ProjectAndBranch(project, branchName);

        try {
            // 1. Verificar si ya existe un PR abierto o efectivo
            PullRequest existingPR = pullRequestService.findOpen(target, source);
            if (existingPR != null) {
                logger.info("Ya existe un Pull Request abierto para {} -> {}: #{}",
                    source.getBranch(), target.getBranch(), existingPR.getNumber());
                return existingPR;
            }

            // 2. Resolver o crear la rama de sincronización
            ObjectId headId = gitService.resolve(project, "refs/heads/" + branchName, false);
            if (headId == null) {
                headId = gitService.createBranch(project, branchName, originCommit.name());
                logger.info("Creada rama de propuesta '{}' basada en commit {}", branchName, shortHash);
            }

            // 3. Preparar los archivos propuestos
            BlobEdits blobEdits = new BlobEdits();
            for (ImpactedItem item : analysis.getImpactedItems()) {
                String path = item.getSuggestedPath();
                if (path == null || path.trim().isEmpty()) {
                    continue;
                }
                String content = item.getProposedContent();
                if (content == null || content.trim().isEmpty()) {
                    content = generateDefaultTemplate(item, shortHash, originCommit.getShortMessage(), authorName);
                }
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                blobEdits.getNewBlobs().put(path, new BlobContent(bytes, FileMode.REGULAR_FILE.getBits()));
            }

            if (blobEdits.getNewBlobs().isEmpty()) {
                logger.warn("No se generaron blobs para la propuesta de trazabilidad en commit {}", shortHash);
                return null;
            }

            // 4. Realizar commit en la rama de propuesta firmado como el usuario del sistema
            String commitMessage = "[Traceability Proposal] Sincronizar elementos correlacionados para commit " + shortHash;
            ObjectId newCommitId = gitService.commit(
                project,
                blobEdits,
                "refs/heads/" + branchName,
                headId,
                headId,
                userService.getSystem().asPerson(),
                commitMessage,
                false
            );

            logger.info("Commit de propuesta creado: {} en rama {}", newCommitId.name(), branchName);

            // 5. Crear y abrir el Pull Request en OneDev
            PullRequest pullRequest = new PullRequest();
            pullRequest.setTarget(target);
            pullRequest.setSource(source);
            pullRequest.setSubmitter(userService.getSystem());
            pullRequest.setTitle("[Propuesta de Trazabilidad] " + analysis.getSummary());
            pullRequest.setDescription(buildPullRequestDescription(targetBranch, originCommit, analysis));

            pullRequestService.open(pullRequest);
            logger.info("Pull Request de propuesta creado exitosamente: #{} - {}", pullRequest.getNumber(), pullRequest.getTitle());

            // 6. Añadir comentario inicial con checklist de revisión humana
            try {
                PullRequestComment comment = new PullRequestComment();
                comment.setRequest(pullRequest);
                comment.setUser(userService.getSystem());
                comment.setContent(buildInitialReviewChecklist(targetBranch));
                comment.setDate(new Date());
                pullRequestCommentService.create(comment, Collections.emptyList());
                logger.info("Comentario inicial con checklist de revisión humana agregado al PR #{}", pullRequest.getNumber());
            } catch (Exception ce) {
                logger.warn("No se pudo agregar el comentario inicial de checklist al PR #{}: {}", pullRequest.getNumber(), ce.getMessage());
            }

            return pullRequest;

        } catch (Exception e) {
            logger.error("Error al generar propuesta de cambios para commit {}: {}", shortHash, e.getMessage(), e);
            return null;
        }
    }
}