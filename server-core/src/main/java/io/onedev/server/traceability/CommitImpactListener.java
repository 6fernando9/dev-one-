package io.onedev.server.traceability;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.server.event.Listen;
import io.onedev.server.event.project.RefUpdated;
import io.onedev.server.git.GitUtils;
import io.onedev.server.model.Project;
import io.onedev.server.service.ProjectService;

/**
 * Listener reactivo de eventos de repositorio SCM.
 * Intercepta cada git push (RefUpdated), extrae el diff de código y dispara el
 * análisis de impacto asistido por IA (RF2) y la generación de propuestas (RF3).
 */
@Singleton
public class CommitImpactListener {

    private static final Logger logger = LoggerFactory.getLogger(CommitImpactListener.class);

    private final ImpactAnalysisService impactAnalysisService;
    private final ProjectService projectService;
    private final ChangeProposalService changeProposalService;

    @Inject
    public CommitImpactListener(ImpactAnalysisService impactAnalysisService,
                                ProjectService projectService,
                                ChangeProposalService changeProposalService) {
        this.impactAnalysisService = impactAnalysisService;
        this.projectService = projectService;
        this.changeProposalService = changeProposalService;
    }

    @Listen
    public void on(RefUpdated event) {
        if (!event.getRefName().startsWith(Constants.R_HEADS)) {
            return;
        }

        if (event.getNewCommitId().equals(ObjectId.zeroId())) {
            return; // Rama eliminada
        }

        String branchName = GitUtils.ref2branch(event.getRefName());
        if (branchName != null && branchName.startsWith("proposal-sync-")) {
            return; // Evitar procesar ramas generadas por el propio sistema
        }

        Project project = event.getProject();
        ObjectId newCommitId = event.getNewCommitId();
        ObjectId oldCommitId = event.getOldCommitId();

        try (Repository repo = projectService.getRepository(project.getId())) {
            RevCommit revCommit = project.getRevCommit(newCommitId, false);
            if (revCommit == null) {
                return;
            }

            String commitHash = newCommitId.name();
            String commitMessage = revCommit.getFullMessage();
            String authorName = revCommit.getAuthorIdent() != null ? revCommit.getAuthorIdent().getName() : "Desconocido";

            List<String> changedFiles = new ArrayList<String>();
            String diffContent = null;

            if (!oldCommitId.equals(ObjectId.zeroId())) {
                List<DiffEntry> diffs = GitUtils.diff(repo, oldCommitId, newCommitId);
                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath() != null && !diff.getNewPath().equals("/dev/null")) {
                        changedFiles.add(diff.getNewPath());
                    } else if (diff.getOldPath() != null) {
                        changedFiles.add(diff.getOldPath());
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GitUtils.diff(repo, oldCommitId, newCommitId, null, baos);
                diffContent = baos.toString("UTF-8");
            }

            ChangeImpactAnalysis analysis = impactAnalysisService.analyze(
                commitHash, commitMessage, authorName, changedFiles, diffContent
            );

            logger.info("Análisis de impacto completado para commit {}: Categoría={}, Drift={}, Elementos Impactados={}",
                commitHash.substring(0, Math.min(8, commitHash.length())),
                analysis.getCategory(),
                analysis.hasDrift(),
                analysis.getImpactedItems().size());

            // RF3: Si se detecta desfase (Drift), generar automáticamente la propuesta de sincronización
            if (analysis.hasDrift() && branchName != null) {
                logger.info("Detectado Drift en rama '{}' tras commit {}. Disparando generación de propuesta de cambio (RF3)...",
                    branchName, commitHash.substring(0, Math.min(8, commitHash.length())));
                changeProposalService.proposeChanges(project, branchName, revCommit, analysis);
            }

        } catch (Exception e) {
            logger.error("Error al interceptar y procesar el commit {}: {}", newCommitId.name(), e.getMessage(), e);
        }
    }
}