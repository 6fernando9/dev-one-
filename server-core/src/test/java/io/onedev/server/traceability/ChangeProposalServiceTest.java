package io.onedev.server.traceability;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ChangeProposalServiceTest {

    private DefaultChangeProposalService service;

    @Before
    public void setUp() {
        // Para pruebas unitarias de lógica de formateo, plantillas y guardas,
        // no se requiere inicializar servicios remotos de base de datos.
        service = new DefaultChangeProposalService(null, null, null, null);
    }

    @Test
    public void testBuildBranchName() {
        assertEquals("proposal-sync-a1b2c3d4", service.buildBranchName("a1b2c3d4e5f67890"));
        assertEquals("proposal-sync-12345", service.buildBranchName("12345"));
    }

    @Test
    public void testBuildInitialReviewChecklist() {
        String checklist = service.buildInitialReviewChecklist("master");
        assertNotNull(checklist);
        assertTrue(checklist.contains("Checklist de Revisión Humana"));
        assertTrue(checklist.contains("Human-in-the-Loop"));
        assertTrue(checklist.contains("- [ ] **1. Contenido técnico verificado:**"));
        assertTrue(checklist.contains("- [ ] **2. Ajustes y refinamiento:**"));
        assertTrue(checklist.contains("- [ ] **3. Aprobación y fusión (*Merge*):**"));
        assertTrue(checklist.contains("`master`"));
    }

    @Test
    public void testGenerateDefaultAdrTemplate() {
        ImpactedItem item = new ImpactedItem(
            ConfigItemType.ADR,
            "docs/adr/ADR-0002.md",
            "Se introdujo nueva autenticación biométrica",
            "Evaluar redacción de nuevo ADR"
        );

        String template = service.generateDefaultTemplate(item, "a1b2c3d4", "feat: biometric login", "Alice Dev");
        assertNotNull(template);
        assertTrue(template.contains("# ADR: Propuesta de Decisión Técnica (Commit a1b2c3d4)"));
        assertTrue(template.contains("Propuesto (*Proposed*)"));
        assertTrue(template.contains("## Contexto"));
        assertTrue(template.contains("## Decisión Propuesta"));
        assertTrue(template.contains("## Consecuencias"));
        assertTrue(template.contains("Alice Dev"));
        assertTrue(template.contains("Se introdujo nueva autenticación biométrica"));
    }

    @Test
    public void testGenerateDefaultRequirementTemplate() {
        ImpactedItem item = new ImpactedItem(
            ConfigItemType.REQUIREMENT,
            "docs/requirements/RTM.md",
            "Falta vincular funcionalidad con catálogo de requisitos",
            "Vincular con matriz de requisitos"
        );

        String template = service.generateDefaultTemplate(item, "f9e8d7c6", "feat: export reports", "Bob Dev");
        assertNotNull(template);
        assertTrue(template.contains("# Especificación de Requisito: Sincronización con Commit f9e8d7c6"));
        assertTrue(template.contains("## Metadatos de Trazabilidad"));
        assertTrue(template.contains("## Especificación Funcional"));
        assertTrue(template.contains("### Criterios de Aceptación (DoD)"));
        assertTrue(template.contains("- [ ]"));
    }

    @Test
    public void testGenerateDefaultDataModelTemplate() {
        ImpactedItem item = new ImpactedItem(
            ConfigItemType.DATA_MODEL_ERD,
            "docs/database/erd.md",
            "Migración SQL 002 alteró tabla usuarios",
            "Actualizar ERD"
        );

        String template = service.generateDefaultTemplate(item, "12345678", "db: add user avatar column", "Carol Dev");
        assertNotNull(template);
        assertTrue(template.contains("# Modelo de Datos (ERD): Actualización para Commit 12345678"));
        assertTrue(template.contains("```mermaid"));
        assertTrue(template.contains("erDiagram"));
    }

    @Test
    public void testGenerateDefaultArchitectureTemplate() {
        ImpactedItem item = new ImpactedItem(
            ConfigItemType.ARCHITECTURE_WIKI,
            "docs/architecture/infrastructure.md",
            "Dockerfile modificado con nuevo agente runner",
            "Actualizar wiki de despliegue"
        );

        String template = service.generateDefaultTemplate(item, "87654321", "infra: update runner container", "David Dev");
        assertNotNull(template);
        assertTrue(template.contains("# Guía de Arquitectura / Wiki: Impacto del Commit 87654321"));
        assertTrue(template.contains("## Impacto en Componentes y Despliegue"));
    }

    @Test
    public void testGuardClausesWithoutDrift() {
        assertNull(service.proposeChanges(null, "master", null, null));

        ConfigItem ci = new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:App.java", "src/App.java", "App.java", "hash1");
        ChangeImpactAnalysis noDriftAnalysis = new ChangeImpactAnalysis(
            "hash1", "fix: typo in comment", "Alice", ChangeTypeCategory.FIX,
            "Arreglo de comentario", Collections.singletonList(ci), Collections.emptyList(), false
        );

        assertNull(service.proposeChanges(null, "master", null, noDriftAnalysis));
    }

    @Test
    public void testGuardClausesRecursiveProposalBranch() {
        ConfigItem ci = new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:App.java", "src/App.java", "App.java", "hash1");
        ImpactedItem impacted = new ImpactedItem(ConfigItemType.ADR, "docs/adr/ADR-01.md", "Falta ADR", "Crear ADR");
        ChangeImpactAnalysis driftAnalysis = new ChangeImpactAnalysis(
            "hash1", "feat: new feature", "Alice", ChangeTypeCategory.FEATURE,
            "Nueva feature", Collections.singletonList(ci), Collections.singletonList(impacted), false
        );

        // Si la rama es una rama de propuesta generada por el bot, debe abortar para no ciclarse
        assertNull(service.proposeChanges(null, "proposal-sync-a1b2c3d4", null, driftAnalysis));
    }
}