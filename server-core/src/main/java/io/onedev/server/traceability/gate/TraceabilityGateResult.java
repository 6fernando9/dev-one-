package io.onedev.server.traceability.gate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Resultado estructurado de la evaluación del Gate de Despliegue y Detección de Deriva (RF5).
 * Encapsula el veredicto (PASSED / BLOCKED / WARNING), métricas de cumplimiento,
 * causas de bloqueo y advertencia detallada de consecuencias y riesgos operacionales.
 */
public class TraceabilityGateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private GateStatus status;
    private boolean passed;
    private boolean strictMode = true;
    private int minCoverageThreshold;
    private double actualCoverage;
    private int maxAllowedDrift;
    private int actualDriftCount;
    private int orphanSourceCount;
    private int pendingSyncProposalsCount;

    private List<String> blockingReasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> pendingProposalBranches = new ArrayList<>();
    private List<String> riskConsequences = new ArrayList<>();
    private Date evaluatedAt = new Date();
    private String summaryReport;

    public TraceabilityGateResult() {
        this.status = GateStatus.PASSED;
        this.passed = true;
    }

    public GateStatus getStatus() {
        return status;
    }

    public void setStatus(GateStatus status) {
        this.status = status;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public int getMinCoverageThreshold() {
        return minCoverageThreshold;
    }

    public void setMinCoverageThreshold(int minCoverageThreshold) {
        this.minCoverageThreshold = minCoverageThreshold;
    }

    public double getActualCoverage() {
        return actualCoverage;
    }

    public void setActualCoverage(double actualCoverage) {
        this.actualCoverage = actualCoverage;
    }

    public int getMaxAllowedDrift() {
        return maxAllowedDrift;
    }

    public void setMaxAllowedDrift(int maxAllowedDrift) {
        this.maxAllowedDrift = maxAllowedDrift;
    }

    public int getActualDriftCount() {
        return actualDriftCount;
    }

    public void setActualDriftCount(int actualDriftCount) {
        this.actualDriftCount = actualDriftCount;
    }

    public int getOrphanSourceCount() {
        return orphanSourceCount;
    }

    public void setOrphanSourceCount(int orphanSourceCount) {
        this.orphanSourceCount = orphanSourceCount;
    }

    public int getPendingSyncProposalsCount() {
        return pendingSyncProposalsCount;
    }

    public void setPendingSyncProposalsCount(int pendingSyncProposalsCount) {
        this.pendingSyncProposalsCount = pendingSyncProposalsCount;
    }

    public List<String> getBlockingReasons() {
        return blockingReasons;
    }

    public void setBlockingReasons(List<String> blockingReasons) {
        this.blockingReasons = blockingReasons;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getPendingProposalBranches() {
        return pendingProposalBranches;
    }

    public void setPendingProposalBranches(List<String> pendingProposalBranches) {
        this.pendingProposalBranches = pendingProposalBranches;
    }

    public List<String> getRiskConsequences() {
        return riskConsequences;
    }

    public void setRiskConsequences(List<String> riskConsequences) {
        this.riskConsequences = riskConsequences;
    }

    public Date getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Date evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getSummaryReport() {
        if (summaryReport == null) {
            summaryReport = buildFormattedReport();
        }
        return summaryReport;
    }

    public void setSummaryReport(String summaryReport) {
        this.summaryReport = summaryReport;
    }

    public void addBlockingReason(String reason) {
        this.blockingReasons.add(reason);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void addPendingProposalBranch(String branch) {
        this.pendingProposalBranches.add(branch);
        this.pendingSyncProposalsCount = this.pendingProposalBranches.size();
    }

    public void addRiskConsequence(String consequence) {
        this.riskConsequences.add(consequence);
    }

    /**
     * Construye un informe formateado en Markdown apto para logs de CI/CD, Pull Requests
     * o descripción del Build en OneDev.
     */
    public String buildFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        if (passed && status == GateStatus.PASSED) {
            sb.append(" [DEPLOYMENT GATE: APROBADO] - Calidad y Trazabilidad Verificadas\n");
        } else if (status == GateStatus.WARNING) {
            sb.append(" [DEPLOYMENT GATE: ADVERTENCIA] - Desfases detectados (Modo no bloqueante)\n");
        } else {
            sb.append(" [DEPLOYMENT BLOCKED] - PUERTA DE TRAZABILIDAD Y CALIDAD RECHAZADA\n");
        }
        sb.append("================================================================================\n");
        sb.append(String.format(Locale.US, " * Cobertura de Trazabilidad: %.1f%% (Umbral requerido: %d%%)\n", actualCoverage, minCoverageThreshold));
        sb.append(String.format(Locale.US, " * Elementos con Desfase (Drift): %d (Máximo permitido: %d)\n", actualDriftCount, maxAllowedDrift));
        sb.append(String.format(Locale.US, " * Código Fuente Huérfano: %d archivos\n", orphanSourceCount));
        sb.append(String.format(Locale.US, " * Propuestas de Sincronización Pendientes (RF3): %d\n", pendingSyncProposalsCount));
        sb.append("--------------------------------------------------------------------------------\n");

        if (!blockingReasons.isEmpty()) {
            sb.append("MOTIVOS DE BLOQUEO DEL DESPLIEGUE:\n");
            for (String reason : blockingReasons) {
                sb.append("  [x] ").append(reason).append("\n");
            }
            sb.append("--------------------------------------------------------------------------------\n");
        }

        if (!pendingProposalBranches.isEmpty()) {
            sb.append("PROPUESTAS AUTOMÁTICAS PENDIENTES DE REVISIÓN HUMANA:\n");
            for (String branch : pendingProposalBranches) {
                sb.append("  -> Rama: ").append(branch).append("\n");
            }
            sb.append("--------------------------------------------------------------------------------\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("ADVERTENCIAS:\n");
            for (String w : warnings) {
                sb.append("  (!) ").append(w).append("\n");
            }
            sb.append("--------------------------------------------------------------------------------\n");
        }

        if (!riskConsequences.isEmpty()) {
            sb.append("CONSECUENCIAS Y RIESGOS DE DESPLEGAR CON DERIVA:\n");
            sb.append("  Si se fuerza o anula este bloqueo, el sistema incurre en los siguientes riesgos:\n");
            for (String risk : riskConsequences) {
                sb.append("  * ").append(risk).append("\n");
            }
            sb.append("--------------------------------------------------------------------------------\n");
        }

        sb.append("ACCIÓN REQUERIDA:\n");
        if (passed) {
            sb.append("  El sistema está debidamente sincronizado. Se autoriza la promoción a despliegue.\n");
        } else {
            sb.append("  Revise y fusione las propuestas en las ramas 'proposal-sync-*', o documente los\n");
            sb.append("  elementos desfasados en la Matriz de Trazabilidad antes de continuar.\n");
        }
        sb.append("================================================================================\n");

        return sb.toString();
    }
}
