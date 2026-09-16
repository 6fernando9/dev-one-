package io.onedev.server.traceability.gate;

import org.jspecify.annotations.Nullable;

import io.onedev.server.model.Project;

/**
 * Servicio central del Requisito Funcional 5 (RF5):
 * Detección de Deriva (Drift Detection) en pipelines de CI/CD y Puerta de Bloqueo de Despliegues (Deployment Blocking Gate).
 */
public interface TraceabilityGateService {

    /**
     * Evalúa las políticas de calidad y trazabilidad para el proyecto y revisión especificados.
     *
     * @param project Proyecto evaluado
     * @param revision Commit SHA o nombre de rama (o null para la rama por defecto)
     * @param minCoverage Umbral mínimo de cobertura de trazabilidad requerido (ej. 80%)
     * @param maxAllowedDrift Cantidad máxima de elementos en deriva permitidos (ej. 0)
     * @param failOnPendingSyncProposals Si true, bloquea si existen ramas 'proposal-sync-*' sin fusionar
     * @param strictMode Si true, marca el estado como BLOCKED y passed=false ante fallos; si false, como WARNING
     * @return Objeto TraceabilityGateResult con el veredicto, razones y reporte consolidado
     */
    TraceabilityGateResult checkGate(
        Project project,
        @Nullable String revision,
        int minCoverage,
        int maxAllowedDrift,
        boolean failOnPendingSyncProposals,
        boolean strictMode
    );

    /**
     * Versión abreviada con modo estricto habilitado por defecto.
     */
    TraceabilityGateResult checkGate(
        Project project,
        @Nullable String revision,
        int minCoverage,
        int maxAllowedDrift,
        boolean failOnPendingSyncProposals
    );

}
