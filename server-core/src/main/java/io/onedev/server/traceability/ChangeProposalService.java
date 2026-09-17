package io.onedev.server.traceability;

import org.jspecify.annotations.Nullable;

import org.eclipse.jgit.revwalk.RevCommit;

import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;

/**
 * Servicio encargado del Requisito Funcional 3 (RF3):
 * Generación de Cambios Correlacionados con Revisión Humana (Human-in-the-loop).
 * Cuando se detecta un desfase (Drift), crea una rama de propuesta y un Pull Request
 * en OneDev con los borradores de los artefactos impactados y un checklist de revisión.
 */
public interface ChangeProposalService {

    /**
     * Evalúa el análisis de impacto y, si existe Drift, genera automáticamente
     * la rama de propuesta y abre un Pull Request en OneDev hacia la rama destino.
     *
     * @param project Proyecto en el que ocurrió el cambio
     * @param targetBranch Rama que recibió el commit (ej. "master", "main")
     * @param originCommit Commit que introdujo el cambio
     * @param analysis Resultado del análisis de impacto de RF2
     * @return El PullRequest creado o ya existente, o null si no hubo Drift o no aplica
     */
    @Nullable
    PullRequest proposeChanges(Project project, String targetBranch, RevCommit originCommit, ChangeImpactAnalysis analysis);

    /**
     * Construye el nombre de la rama de sincronización con el prefijo acordado.
     *
     * @param commitHash Hash completo o abreviado del commit
     * @return Nombre de la rama (ej. "proposal-sync-a1b2c3d4")
     */
    String buildBranchName(String commitHash);

    /**
     * Genera la plantilla en Markdown por defecto para un elemento impactado si no cuenta con propuesta de IA.
     */
    String generateDefaultTemplate(ImpactedItem item, String shortHash, String commitMessage, String authorName);

    /**
     * Genera el comentario inicial con checklist de revisión humana para el Pull Request.
     */
    String buildInitialReviewChecklist(String targetBranch);

    /**
     * Genera la descripción Markdown detallada para el Pull Request de propuesta.
     */
    String buildPullRequestDescription(String targetBranch, RevCommit originCommit, ChangeImpactAnalysis analysis);

}