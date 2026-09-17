package io.onedev.server.traceability.gate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

import io.onedev.server.buildspec.step.CheckTraceabilityDriftStep;
import io.onedev.server.git.service.RefFacade;
import io.onedev.server.model.Project;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemType;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;
import io.onedev.server.traceability.matrix.TraceabilityRow;
import io.onedev.server.traceability.matrix.TraceabilitySyncStatus;

/**
 * Pruebas unitarias para el Requisito Funcional 5 (RF5):
 * Detección de Deriva en CI/CD y Puerta de Bloqueo de Despliegues (Deployment Blocking Gate).
 */
public class TraceabilityGateServiceTest {

    private TraceabilityMatrixService matrixService;
    private DefaultTraceabilityGateService gateService;
    private List<RefFacade> mockBranchRefs;
    private Project project;

    @Before
    public void setUp() {
        matrixService = mock(TraceabilityMatrixService.class);
        gateService = new DefaultTraceabilityGateService(matrixService);
        mockBranchRefs = new ArrayList<>();

        project = new Project() {
            private static final long serialVersionUID = 1L;

            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getName() {
                return "test-project";
            }

            @Override
            public String getPath() {
                return "test-project";
            }

            @Override
            public List<RefFacade> getBranchRefs() {
                return mockBranchRefs;
            }
        };
    }

    private TraceabilityMatrix createMockMatrix(int synchronizedCount, int partialCount, int driftReqCount, int orphanCount) {
        List<TraceabilityRow> rows = new ArrayList<>();

        for (int i = 1; i <= synchronizedCount; i++) {
            ConfigItem req = new ConfigItem(ConfigItemType.REQUIREMENT, "RF-0" + i, "docs/requirements/RF-0" + i + ".md", "Requisito " + i);
            TraceabilityRow row = new TraceabilityRow(
                req,
                Collections.emptyList(),
                Collections.singletonList(new ConfigItem(ConfigItemType.SOURCE_CODE, "src/Feature" + i + ".java", "src/Feature" + i + ".java", "Code " + i)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                TraceabilitySyncStatus.SYNCHRONIZED,
                "Sincronizado"
            );
            rows.add(row);
        }

        for (int i = 1; i <= partialCount; i++) {
            ConfigItem req = new ConfigItem(ConfigItemType.REQUIREMENT, "RF-P" + i, "docs/requirements/RF-P" + i + ".md", "Parcial " + i);
            TraceabilityRow row = new TraceabilityRow(
                req,
                Collections.emptyList(),
                Collections.singletonList(new ConfigItem(ConfigItemType.SOURCE_CODE, "src/Partial" + i + ".java", "src/Partial" + i + ".java", "Code Partial " + i)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                TraceabilitySyncStatus.PARTIAL,
                "Parcial sin ADR"
            );
            rows.add(row);
        }

        for (int i = 1; i <= driftReqCount; i++) {
            ConfigItem req = new ConfigItem(ConfigItemType.REQUIREMENT, "RF-D" + i, "docs/requirements/RF-D" + i + ".md", "Drift " + i);
            TraceabilityRow row = new TraceabilityRow(
                req,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                TraceabilitySyncStatus.DRIFT_UNLINKED,
                "Desfasado sin código"
            );
            rows.add(row);
        }

        for (int i = 1; i <= orphanCount; i++) {
            ConfigItem orphan = new ConfigItem(ConfigItemType.SOURCE_CODE, "src/Orphan" + i + ".java", "src/Orphan" + i + ".java", "Orphan " + i);
            TraceabilityRow row = new TraceabilityRow(
                orphan,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                TraceabilitySyncStatus.DRIFT_UNLINKED,
                "Código huérfano"
            );
            rows.add(row);
        }

        return new TraceabilityMatrix(1L, "master", rows, Collections.emptyList());
    }

    @Test
    public void testGatePassesWhenFullySynchronized() {
        TraceabilityMatrix matrix = createMockMatrix(5, 0, 0, 0);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, true);

        assertTrue("El Gate debe autorizar el despliegue cuando el sistema está sincronizado", result.isPassed());
        assertEquals(GateStatus.PASSED, result.getStatus());
        assertTrue(result.getBlockingReasons().isEmpty());
        assertEquals(100.0, result.getActualCoverage(), 0.01);
        assertEquals(0, result.getActualDriftCount());
        assertTrue(result.getSummaryReport().contains("APROBADO"));
    }

    @Test
    public void testGateBlocksWhenCoverageBelowThreshold() {
        TraceabilityMatrix matrix = createMockMatrix(3, 2, 0, 0);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, true);

        assertFalse("El Gate debe bloquear si la cobertura está por debajo del umbral", result.isPassed());
        assertEquals(GateStatus.BLOCKED, result.getStatus());
        assertFalse(result.getBlockingReasons().isEmpty());
        assertTrue(result.getBlockingReasons().get(0).contains("Cobertura de trazabilidad insuficiente"));
        assertTrue(result.getSummaryReport().contains("PUERTA DE TRAZABILIDAD Y CALIDAD RECHAZADA"));
    }

    @Test
    public void testGateBlocksWhenDriftCountExceedsAllowed() {
        TraceabilityMatrix matrix = createMockMatrix(4, 0, 0, 2);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, true);

        assertFalse("El Gate debe bloquear si hay elementos con deriva no mitigados", result.isPassed());
        assertEquals(GateStatus.BLOCKED, result.getStatus());
        assertEquals(2, result.getOrphanSourceCount());

        boolean foundDriftReason = false;
        for (String reason : result.getBlockingReasons()) {
            if (reason.contains("huérfanos") || reason.contains("deriva")) {
                foundDriftReason = true;
                break;
            }
        }
        assertTrue("Debe registrar motivo de bloqueo por elementos desfasados", foundDriftReason);
    }

    @Test
    public void testGateBlocksOnPendingSyncProposals() {
        TraceabilityMatrix matrix = createMockMatrix(5, 0, 0, 0);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        Ref ref = mock(Ref.class);
        when(ref.getName()).thenReturn("refs/heads/proposal-sync-a1b2c3d");
        RefFacade proposalRef = new RefFacade(ref, null, null);
        mockBranchRefs.add(proposalRef);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, true);

        assertFalse("El Gate debe bloquear si hay propuestas automáticas de sincronización pendientes", result.isPassed());
        assertEquals(GateStatus.BLOCKED, result.getStatus());
        assertEquals(1, result.getPendingSyncProposalsCount());
        assertEquals("proposal-sync-a1b2c3d", result.getPendingProposalBranches().get(0));

        boolean foundProposalReason = false;
        for (String reason : result.getBlockingReasons()) {
            if (reason.contains("proposal-sync-a1b2c3d")) {
                foundProposalReason = true;
                break;
            }
        }
        assertTrue("El reporte de bloqueo debe citar la rama pendiente específica", foundProposalReason);
    }

    @Test
    public void testGatePermissiveWarningMode() {
        TraceabilityMatrix matrix = createMockMatrix(2, 3, 1, 1);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, false);

        assertTrue("En modo permisivo no debe abortar (passed = true)", result.isPassed());
        assertEquals(GateStatus.WARNING, result.getStatus());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("modo permisivo"));
    }

    @Test
    public void testRiskConsequencesAndDetailedReport() {
        TraceabilityMatrix matrix = createMockMatrix(2, 3, 1, 1);
        when(matrixService.buildMatrix(eq(project), any())).thenReturn(matrix);

        TraceabilityGateResult result = gateService.checkGate(project, "master", 80, 0, true, true);

        assertNotNull(result.getRiskConsequences());
        assertFalse("Debe detallar consecuencias operacionales y arquitectónicas al usuario", result.getRiskConsequences().isEmpty());

        String report = result.getSummaryReport();
        assertTrue(report.contains("CONSECUENCIAS Y RIESGOS DE DESPLEGAR CON DERIVA"));
        assertTrue(report.contains("Inconsistencia Arquitectónica"));
        assertTrue(report.contains("Pérdida de Trazabilidad en Auditorías"));
        assertTrue(report.contains("Riesgo de Regresión Operativa"));
    }

    @Test
    public void testStepConfigurationDefaults() {
        CheckTraceabilityDriftStep step = new CheckTraceabilityDriftStep();
        assertEquals(80, step.getMinCoverage());
        assertEquals(0, step.getMaxAllowedDrift());
        assertTrue(step.isFailOnPendingSyncProposals());
        assertTrue(step.isFailBuild());
        assertTrue(step.isUpdateBuildDescription());

        // Modificación de configuración
        step.setMinCoverage(90);
        step.setMaxAllowedDrift(2);
        step.setFailBuild(false);
        assertEquals(90, step.getMinCoverage());
        assertEquals(2, step.getMaxAllowedDrift());
        assertFalse(step.isFailBuild());
    }

}
