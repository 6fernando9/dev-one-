package io.onedev.server.traceability.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.server.git.GitUtils;
import io.onedev.server.git.service.RefFacade;
import io.onedev.server.model.Project;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;

/**
 * Implementación por defecto del servicio de control de despliegues y detección de deriva (RF5).
 */
@Singleton
public class DefaultTraceabilityGateService implements TraceabilityGateService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTraceabilityGateService.class);

    private final TraceabilityMatrixService matrixService;

    @Inject
    public DefaultTraceabilityGateService(TraceabilityMatrixService matrixService) {
        this.matrixService = matrixService;
    }

    @Override
    public TraceabilityGateResult checkGate(
            Project project,
            @Nullable String revision,
            int minCoverage,
            int maxAllowedDrift,
            boolean failOnPendingSyncProposals,
            boolean strictMode) {

        TraceabilityGateResult result = new TraceabilityGateResult();
        result.setStrictMode(strictMode);
        result.setMinCoverageThreshold(minCoverage);
        result.setMaxAllowedDrift(maxAllowedDrift);

        // 1. Construir la matriz de trazabilidad para la revisión
        TraceabilityMatrix matrix = matrixService.buildMatrix(project, revision);
        result.setActualCoverage(matrix.getCoveragePercentage());
        result.setActualDriftCount(matrix.getDriftCount());
        result.setOrphanSourceCount(matrix.getOrphanCount());

        // 2. Verificar cobertura mínima
        if (matrix.getCoveragePercentage() < minCoverage) {
            result.addBlockingReason(String.format(Locale.US,
                "Cobertura de trazabilidad insuficiente: %.1f%% (Mínimo requerido: %d%%).",
                matrix.getCoveragePercentage(), minCoverage));
        }

        // 3. Verificar cantidad máxima de elementos con desfase
        if (matrix.getDriftCount() > maxAllowedDrift) {
            result.addBlockingReason(String.format(Locale.US,
                "Se detectaron %d elemento(s) con deriva o desincronización (Máximo permitido: %d).",
                matrix.getDriftCount(), maxAllowedDrift));
        }

        // 4. Verificar código fuente huérfano
        if (matrix.getOrphanCount() > maxAllowedDrift) {
            result.addBlockingReason(String.format(Locale.US,
                "Se detectaron %d archivo(s) de código fuente huérfanos sin requisito funcional asociado.",
                matrix.getOrphanCount()));
        }

        // 5. Verificar propuestas de sincronización pendientes de RF3 (ramas 'proposal-sync-*')
        List<String> pendingBranches = findPendingProposalBranches(project);
        for (String branch : pendingBranches) {
            result.addPendingProposalBranch(branch);
        }

        if (failOnPendingSyncProposals && !pendingBranches.isEmpty()) {
            result.addBlockingReason(String.format(Locale.US,
                "Existen %d propuesta(s) automáticas de sincronización de RF3 pendientes de revisión humana (%s). " +
                "Deben ser revisadas, aprobadas y fusionadas (o descartadas) antes del pase a producción.",
                pendingBranches.size(), String.join(", ", pendingBranches)));
        }

        // 6. Consecuencias y riesgos de desplegar con deriva
        result.addRiskConsequence("Inconsistencia Arquitectónica: Despliegue de código sin respaldo formal en especificaciones de negocio ni ADRs aprobados.");
        result.addRiskConsequence("Pérdida de Trazabilidad en Auditorías: Imposibilidad de justificar los cambios ante auditorías de cumplimiento o rastreo de causas raíz ante fallos.");
        result.addRiskConsequence("Riesgo de Regresión Operativa: Esquemas de datos (ERD/SQL) o infraestructura (IaC/Docker) desfasados pueden generar fallos en caliente.");
        result.addRiskConsequence("Acumulación de Deuda Técnica: Omitir la revisión de propuestas automáticas causa una divergencia progresiva irreversible entre el código y la documentación.");

        // 7. Determinar veredicto final
        if (!result.getBlockingReasons().isEmpty()) {
            if (strictMode) {
                result.setStatus(GateStatus.BLOCKED);
                result.setPassed(false);
            } else {
                result.setStatus(GateStatus.WARNING);
                result.setPassed(true);
                result.addWarning("El despliegue presenta desfases pero fue autorizado bajo modo permisivo (no estricto).");
            }
        } else {
            result.setStatus(GateStatus.PASSED);
            result.setPassed(true);
        }

        // 8. Generar reporte consolidado
        result.setSummaryReport(result.buildFormattedReport());

        return result;
    }

    @Override
    public TraceabilityGateResult checkGate(
            Project project,
            @Nullable String revision,
            int minCoverage,
            int maxAllowedDrift,
            boolean failOnPendingSyncProposals) {
        return checkGate(project, revision, minCoverage, maxAllowedDrift, failOnPendingSyncProposals, true);
    }

    private List<String> findPendingProposalBranches(@Nullable Project project) {
        List<String> pending = new ArrayList<>();
        if (project != null) {
            try {
                List<RefFacade> branchRefs = project.getBranchRefs();
                if (branchRefs != null) {
                    for (RefFacade ref : branchRefs) {
                        String branch = GitUtils.ref2branch(ref.getName());
                        if (branch != null && branch.startsWith("proposal-sync-")) {
                            pending.add(branch);
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("No se pudieron leer las ramas del proyecto: {}", e.getMessage());
            }
        }
        return pending;
    }
}
