package io.onedev.server.traceability;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ImpactAnalysisServiceTest {

    private DefaultImpactAnalysisService service;

    @Before
    public void setUp() {
        service = new DefaultImpactAnalysisService();
    }

    @Test
    public void testDetectCategory() {
        assertEquals(ChangeTypeCategory.FEATURE, 
            service.detectCategory("feat: add biometric authentication", Collections.emptyList()));
        assertEquals(ChangeTypeCategory.FEATURE, 
            service.detectCategory("Implement user profile export", Collections.emptyList()));

        assertEquals(ChangeTypeCategory.FIX, 
            service.detectCategory("fix: resolve null pointer in login", Collections.emptyList()));
        assertEquals(ChangeTypeCategory.FIX, 
            service.detectCategory("Corregir error en cálculo de impuestos", Collections.emptyList()));

        assertEquals(ChangeTypeCategory.REFACTOR, 
            service.detectCategory("refactor: extract helper methods", Collections.emptyList()));

        assertEquals(ChangeTypeCategory.DOCUMENTATION, 
            service.detectCategory("docs: update architecture overview", Collections.emptyList()));

        assertEquals(ChangeTypeCategory.DATA_MODEL, 
            service.detectCategory("update user table", Collections.singletonList("db/migrations/001.sql")));

        assertEquals(ChangeTypeCategory.INFRASTRUCTURE, 
            service.detectCategory("update container config", Collections.singletonList("Dockerfile")));
    }

    @Test
    public void testHeuristicImpactForFeatureWithoutDocs() {
        List<String> files = Collections.singletonList("src/main/java/com/app/AuthService.java");
        ChangeImpactAnalysis analysis = service.analyze(
            "abc12345", "feat: add 2FA authentication", "Developer", files, null
        );

        assertEquals("abc12345", analysis.getCommitHash());
        assertEquals(ChangeTypeCategory.FEATURE, analysis.getCategory());
        assertFalse(analysis.isAiGenerated());
        assertTrue(analysis.hasDrift());

        // Debe detectar impacto en Requisitos y ADR
        assertTrue(analysis.getImpactedItems().stream()
            .anyMatch(i -> i.getTargetType() == ConfigItemType.REQUIREMENT));
        assertTrue(analysis.getImpactedItems().stream()
            .anyMatch(i -> i.getTargetType() == ConfigItemType.ADR));
    }

    @Test
    public void testHeuristicImpactForDataModelChange() {
        List<String> files = Collections.singletonList("db/migrations/V1__add_fingerprint_to_users.sql");
        ChangeImpactAnalysis analysis = service.analyze(
            "def67890", "migration: add fingerprint column", "DBA", files, null
        );

        assertTrue(analysis.hasDrift());
        assertTrue(analysis.getImpactedItems().stream()
            .anyMatch(i -> i.getTargetType() == ConfigItemType.DATA_MODEL_ERD));
    }

    @Test
    public void testParseAiResponseWithMarkdownFence() {
        String aiJson = """
            ```json
            {
              "category": "FEATURE",
              "summary": "Implementación de login biométrico para móviles",
              "impactedItems": [
                {
                  "targetType": "DATA_MODEL_ERD",
                  "suggestedPath": "docs/database/erd.md",
                  "reason": "Se agregó la columna fingerprint_hash en la tabla usuario",
                  "suggestedAction": "Agregar campo fingerprint_hash en diagrama ERD"
                },
                {
                  "targetType": "ADR",
                  "suggestedPath": "docs/adr/ADR-0004-biometria.md",
                  "reason": "Decisión de arquitectura sobre algoritmo de hashing biométrico",
                  "suggestedAction": "Documentar ADR para el nuevo algoritmo"
                }
              ]
            }
            ```
            """;

        ConfigItemClassifier classifier = new ConfigItemClassifier();
        List<ConfigItem> changed = classifier.classifyPaths(Collections.singletonList("src/AuthService.java"));

        ChangeImpactAnalysis analysis = service.parseAiResponse(
            aiJson, "11223344", "feat: biometrics", "Alice", changed
        );

        assertTrue(analysis.isAiGenerated());
        assertEquals(ChangeTypeCategory.FEATURE, analysis.getCategory());
        assertEquals("Implementación de login biométrico para móviles", analysis.getSummary());
        assertEquals(2, analysis.getImpactedItems().size());
        assertTrue(analysis.hasDrift());

        ImpactedItem erd = analysis.getImpactedItems().get(0);
        assertEquals(ConfigItemType.DATA_MODEL_ERD, erd.getTargetType());
        assertEquals("docs/database/erd.md", erd.getSuggestedPath());

        ImpactedItem adr = analysis.getImpactedItems().get(1);
        assertEquals(ConfigItemType.ADR, adr.getTargetType());
        assertEquals("docs/adr/ADR-0004-biometria.md", adr.getSuggestedPath());
    }

    @Test
    public void testNoDriftWhenAlreadySynchronized() {
        // Commit que modificó código Y TAMBIÉN la documentación correlacionada
        List<String> files = Arrays.asList(
            "src/main/java/com/app/AuthService.java",
            "docs/requirements/RTM.md",
            "docs/adr/ADR-next.md"
        );

        ChangeImpactAnalysis analysis = service.analyze(
            "aabbccdd", "feat: complete feature with docs", "Bob", files, null
        );

        // Como los elementos impactados (RTM y ADR) están incluidos en el mismo commit, NO hay drift
        assertFalse(analysis.hasDrift());
    }
}