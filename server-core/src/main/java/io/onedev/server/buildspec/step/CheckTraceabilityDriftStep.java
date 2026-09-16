package io.onedev.server.buildspec.step;

import java.io.File;

import javax.validation.constraints.NotNull;

import io.onedev.commons.utils.TaskLogger;
import io.onedev.k8shelper.ServerStepResult;
import io.onedev.server.OneDev;
import io.onedev.server.annotation.Editable;
import io.onedev.server.event.ListenerRegistry;
import io.onedev.server.event.project.build.BuildUpdated;
import io.onedev.server.model.Build;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.TransactionService;
import io.onedev.server.service.BuildService;
import io.onedev.server.traceability.gate.GateStatus;
import io.onedev.server.traceability.gate.TraceabilityGateResult;
import io.onedev.server.traceability.gate.TraceabilityGateService;

/**
 * Step de CI/CD nativo en OneDev para la Detección de Deriva (Drift) y Bloqueo de Despliegues (RF5).
 * Analiza la matriz de trazabilidad y aborta el pipeline si la cobertura es deficiente,
 * si existe código o modelos huérfanos, o si hay propuestas automáticas pendientes de revisión humana.
 */
@Editable(order=270, name="Check Traceability Drift & Deployment Gate",
        description="Verifica la matriz de trazabilidad y bloquea el despliegue si existe deriva o cobertura insuficiente.")
public class CheckTraceabilityDriftStep extends ServerSideStep {

    private static final long serialVersionUID = 1L;

    private int minCoverage = 80;

    private int maxAllowedDrift = 0;

    private boolean failOnPendingSyncProposals = true;

    private boolean failBuild = true;

    private boolean updateBuildDescription = true;

    @Editable(order=100, name="Minimum Coverage (%)", description="Porcentaje mínimo requerido de requisitos completamente sincronizados.")
    @NotNull
    public int getMinCoverage() {
        return minCoverage;
    }

    public void setMinCoverage(int minCoverage) {
        this.minCoverage = minCoverage;
    }

    @Editable(order=110, name="Max Allowed Drift Count", description="Cantidad máxima de elementos con desfase permitidos antes de bloquear.")
    @NotNull
    public int getMaxAllowedDrift() {
        return maxAllowedDrift;
    }

    public void setMaxAllowedDrift(int maxAllowedDrift) {
        this.maxAllowedDrift = maxAllowedDrift;
    }

    @Editable(order=120, name="Block On Pending Sync Proposals", description="Bloquear si existen ramas 'proposal-sync-*' pendientes de revisión humana de RF3.")
    public boolean isFailOnPendingSyncProposals() {
        return failOnPendingSyncProposals;
    }

    public void setFailOnPendingSyncProposals(boolean failOnPendingSyncProposals) {
        this.failOnPendingSyncProposals = failOnPendingSyncProposals;
    }

    @Editable(order=130, name="Fail Build If Blocked", description="Si está habilitado, la compilación/pipeline fallará inmediatamente ante cualquier bloqueo de calidad.")
    public boolean isFailBuild() {
        return failBuild;
    }

    public void setFailBuild(boolean failBuild) {
        this.failBuild = failBuild;
    }

    @Editable(order=140, name="Update Build Description", description="Publicar el veredicto y métricas del Gate en la descripción del Build de OneDev.")
    public boolean isUpdateBuildDescription() {
        return updateBuildDescription;
    }

    public void setUpdateBuildDescription(boolean updateBuildDescription) {
        this.updateBuildDescription = updateBuildDescription;
    }

    @Override
    public ServerStepResult run(Long buildId, File inputDir, TaskLogger logger) {
        return OneDev.getInstance(TransactionService.class).call(() -> {
            Build build = OneDev.getInstance(BuildService.class).load(buildId);
            Project project = build.getProject();
            String commitHash = build.getCommitHash();

            TraceabilityGateService gateService = OneDev.getInstance(TraceabilityGateService.class);
            TraceabilityGateResult result = gateService.checkGate(
                    project, commitHash, minCoverage, maxAllowedDrift, failOnPendingSyncProposals, failBuild);

            // Emitir reporte a TaskLogger con formato ANSI
            if (result.isPassed() && result.getStatus() == GateStatus.PASSED) {
                logger.success(result.getSummaryReport());
            } else if (result.getStatus() == GateStatus.WARNING) {
                logger.warning(result.getSummaryReport());
            } else {
                logger.error(result.getSummaryReport());
            }

            // Actualizar la descripción de la compilación si está configurado
            if (updateBuildDescription) {
                String header = result.isPassed()
                        ? "### 🛡️ Traceability Gate: APROBADO PARA DESPLIEGUE\n\n"
                        : "### 🚨 Traceability Gate: BLOQUEADO (DESPLIEGUE DENEGADO)\n\n";
                String currentDesc = build.getDescription() != null ? build.getDescription() + "\n\n" : "";
                build.setDescription(currentDesc + header + "```text\n" + result.getSummaryReport() + "\n```");
                OneDev.getInstance(ListenerRegistry.class).post(new BuildUpdated(build));
            }

            if (!result.isPassed() && failBuild) {
                return new ServerStepResult(false);
            } else {
                return new ServerStepResult(true);
            }
        });
    }

}
