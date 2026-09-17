package io.onedev.server.traceability.matrix;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemClassifier;
import io.onedev.server.traceability.ConfigItemType;

public class TraceabilityMatrixServiceTest {

    private DefaultTraceabilityMatrixService service;
    private ConfigItemClassifier classifier;

    @Before
    public void setUp() {
        classifier = new ConfigItemClassifier();
        // Para pruebas unitarias aisladas de lógica de grafo, métricas y exportación
        service = new DefaultTraceabilityMatrixService(classifier, null, null, null);
    }

    private List<ConfigItem> createSampleItems() {
        List<ConfigItem> items = new ArrayList<>();

        // Requisito 1: Autenticación
        items.add(new ConfigItem(ConfigItemType.REQUIREMENT, "RF-01", "docs/requirements/RF-01-auth.md", "Autenticación de Usuarios", "h1"));
        items.add(new ConfigItem(ConfigItemType.PROJECT_TASK, "#101", "issues/101", "[RF-01] Implementar login con JWT", "h2"));
        items.add(new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:AuthService.java", "src/main/java/com/app/auth/AuthService.java", "AuthService.java", "h3"));
        items.add(new ConfigItem(ConfigItemType.ADR, "ADR-0001", "docs/adr/ADR-0001-auth-jwt.md", "Decisión: Autenticación vía JWT", "h4"));
        items.add(new ConfigItem(ConfigItemType.DATA_MODEL_ERD, "ERD:users.sql", "docs/database/001-users.sql", "Esquema de Usuarios", "h5"));

        // Requisito 2: Exportación de Reportes (parcial, sin ADR)
        items.add(new ConfigItem(ConfigItemType.REQUIREMENT, "RF-02", "docs/requirements/RF-02-reports.md", "Exportación de Reportes", "h6"));
        items.add(new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:ReportExport.java", "src/main/java/com/app/reports/ReportExport.java", "ReportExport.java", "h7"));

        // Requisito 3: Pagos (drift, sin código fuente aún)
        items.add(new ConfigItem(ConfigItemType.REQUIREMENT, "RF-03", "docs/requirements/RF-03-payments.md", "Pasarela de Pagos", "h8"));

        // Código huérfano (no vinculado a ningún requisito)
        items.add(new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:LegacyUtil.java", "src/main/java/com/app/legacy/LegacyUtil.java", "LegacyUtil.java", "h9"));

        return items;
    }

    @Test
    public void testMatrixConstructionAndMetrics() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        assertNotNull(matrix);
        assertEquals(3, matrix.getTotalRequirements());
        assertEquals(1, matrix.getSynchronizedCount()); // RF-01 tiene código y ADR
        assertEquals(1, matrix.getPartialCount());      // RF-02 tiene código pero no ADR
        assertEquals(1, matrix.getDriftCount());        // RF-03 no tiene código
        assertEquals(1, matrix.getOrphanCount());       // LegacyUtil.java

        assertTrue(matrix.getCoveragePercentage() > 30.0);
        assertTrue(matrix.getTotalLinks() > 0);
    }

    @Test
    public void testForwardTraceability() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        TraceabilityRow rf01Row = null;
        for (TraceabilityRow row : matrix.getRows()) {
            if ("RF-01".equals(row.getPrimaryItem().getIdentifier())) {
                rf01Row = row;
                break;
            }
        }

        assertNotNull(rf01Row);
        assertEquals(TraceabilitySyncStatus.SYNCHRONIZED, rf01Row.getStatus());
        assertEquals(1, rf01Row.getTasks().size());
        assertEquals(1, rf01Row.getSourceFiles().size());
        assertEquals(1, rf01Row.getAdrs().size());
        assertEquals(1, rf01Row.getDataModels().size());

        assertEquals("#101", rf01Row.getTasks().get(0).getIdentifier());
        assertEquals("SRC:AuthService.java", rf01Row.getSourceFiles().get(0).getIdentifier());
        assertEquals("ADR-0001", rf01Row.getAdrs().get(0).getIdentifier());
    }

    @Test
    public void testOrphanSourceCodeDetection() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        TraceabilityRow orphanRow = null;
        for (TraceabilityRow row : matrix.getRows()) {
            if ("SRC:LegacyUtil.java".equals(row.getPrimaryItem().getIdentifier())) {
                orphanRow = row;
                break;
            }
        }

        assertNotNull("Debe existir una fila para el código huérfano", orphanRow);
        assertEquals(TraceabilitySyncStatus.DRIFT_UNLINKED, orphanRow.getStatus());
        assertTrue(orphanRow.getNotes().contains("huérfano"));
    }

    @Test
    public void testExportToCsv() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        String csv = service.exportToCsv(matrix);
        assertNotNull(csv);
        assertTrue(csv.startsWith("ID Elemento,Tipo,Titulo,Estado,Tareas / Issues,Codigo Fuente,ADRs"));
        assertTrue(csv.contains("RF-01"));
        assertTrue(csv.contains("Autenticación de Usuarios"));
        assertTrue(csv.contains("LegacyUtil.java"));
    }

    @Test
    public void testExportToMarkdown() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        String md = service.exportToMarkdown(matrix);
        assertNotNull(md);
        assertTrue(md.contains("# Matriz de Trazabilidad de Requisitos (RTM)"));
        assertTrue(md.contains("Métricas Generales de Cobertura"));
        assertTrue(md.contains("RF-01"));
        assertTrue(md.contains("🟢 Sincronizado"));
        assertTrue(md.contains("Código Fuente Huérfano"));
    }

    @Test
    public void testExportToJson() {
        List<ConfigItem> items = createSampleItems();
        TraceabilityMatrix matrix = service.buildMatrixFromItems(1L, "master", items, Collections.emptyMap());

        String json = service.exportToJson(matrix);
        assertNotNull(json);
        assertTrue(json.contains("\"totalRequirements\": 3"));
        assertTrue(json.contains("\"rows\":"));
    }
}