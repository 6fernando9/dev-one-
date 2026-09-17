package io.onedev.server.traceability;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ConfigItemClassifierTest {

    private ConfigItemClassifier classifier;

    @Before
    public void setUp() {
        classifier = new ConfigItemClassifier();
    }

    @Test
    public void testClassifyAdr() {
        ConfigItem item = classifier.classifyPath("docs/adr/ADR-0001-record-architecture-decisions.md");
        assertEquals(ConfigItemType.ADR, item.getType());
        assertTrue(item.getIdentifier().contains("ADR-0001"));

        ConfigItem item2 = classifier.classifyPath("adr/ADR-002-database-choice.md");
        assertEquals(ConfigItemType.ADR, item2.getType());
        assertTrue(item2.getIdentifier().contains("ADR-002"));
    }

    @Test
    public void testClassifyRequirement() {
        ConfigItem item = classifier.classifyPath("docs/requirements/RF-01-user-authentication.md");
        assertEquals(ConfigItemType.REQUIREMENT, item.getType());
        assertTrue(item.getIdentifier().contains("RF-01"));

        ConfigItem catalog = classifier.classifyPath("docs/requirements/requirements.json");
        assertEquals(ConfigItemType.REQUIREMENT, catalog.getType());
        assertEquals("RTM-CATALOG", catalog.getIdentifier());
    }

    @Test
    public void testClassifyArchitectureWiki() {
        ConfigItem item = classifier.classifyPath("docs/architecture/c4-component-model.md");
        assertEquals(ConfigItemType.ARCHITECTURE_WIKI, item.getType());

        ConfigItem wiki = classifier.classifyPath("wiki/Deployment-Guide.md");
        assertEquals(ConfigItemType.ARCHITECTURE_WIKI, wiki.getType());
    }

    @Test
    public void testClassifyDataModelErd() {
        ConfigItem sql = classifier.classifyPath("db/migrations/20260915_create_users.sql");
        assertEquals(ConfigItemType.DATA_MODEL_ERD, sql.getType());

        ConfigItem prisma = classifier.classifyPath("prisma/schema.prisma");
        assertEquals(ConfigItemType.DATA_MODEL_ERD, prisma.getType());

        ConfigItem erd = classifier.classifyPath("docs/database/database-erd.md");
        assertEquals(ConfigItemType.DATA_MODEL_ERD, erd.getType());
    }

    @Test
    public void testClassifyInfrastructureIac() {
        ConfigItem dockerfile = classifier.classifyPath("Dockerfile");
        assertEquals(ConfigItemType.INFRASTRUCTURE_IAC, dockerfile.getType());

        ConfigItem compose = classifier.classifyPath("docker-compose.prod.yml");
        assertEquals(ConfigItemType.INFRASTRUCTURE_IAC, compose.getType());

        ConfigItem terraform = classifier.classifyPath("terraform/main.tf");
        assertEquals(ConfigItemType.INFRASTRUCTURE_IAC, terraform.getType());

        ConfigItem buildspec = classifier.classifyPath(".onedev-buildspec.yml");
        assertEquals(ConfigItemType.INFRASTRUCTURE_IAC, buildspec.getType());
    }

    @Test
    public void testClassifySourceCode() {
        ConfigItem java = classifier.classifyPath("server-core/src/main/java/io/onedev/server/model/Project.java");
        assertEquals(ConfigItemType.SOURCE_CODE, java.getType());

        ConfigItem ts = classifier.classifyPath("frontend/src/components/Navbar.tsx");
        assertEquals(ConfigItemType.SOURCE_CODE, ts.getType());

        ConfigItem py = classifier.classifyPath("scripts/data_sync.py");
        assertEquals(ConfigItemType.SOURCE_CODE, py.getType());
    }

    @Test
    public void testClassifyIssue() {
        // Tarea normal de proyecto
        ConfigItem task = classifier.classifyIssue(101L, "Corregir padding en barra lateral", Collections.emptyList());
        assertEquals(ConfigItemType.PROJECT_TASK, task.getType());
        assertEquals("#101", task.getIdentifier());

        // Requisito detectado por título
        ConfigItem reqByTitle = classifier.classifyIssue(102L, "[RF-03] Sistema de reportes PDF", Collections.emptyList());
        assertEquals(ConfigItemType.REQUIREMENT, reqByTitle.getType());
        assertEquals("RF-03", reqByTitle.getIdentifier());

        // Requisito detectado por etiqueta
        ConfigItem reqByLabel = classifier.classifyIssue(103L, "Auditoría de accesos", Arrays.asList("Requirement", "Backend"));
        assertEquals(ConfigItemType.REQUIREMENT, reqByLabel.getType());
    }

    @Test
    public void testBatchClassification() {
        List<String> files = Arrays.asList(
            "src/main/java/com/app/AuthService.java",
            "docs/adr/ADR-0005-oauth2-flow.md",
            "docs/requirements/RF-02-sso.md",
            "db/schema.sql",
            "k8s/deployment.yaml"
        );

        List<ConfigItem> items = classifier.classifyPaths(files);
        assertEquals(5, items.size());

        assertTrue(items.stream().anyMatch(i -> i.getType() == ConfigItemType.SOURCE_CODE));
        assertTrue(items.stream().anyMatch(i -> i.getType() == ConfigItemType.ADR));
        assertTrue(items.stream().anyMatch(i -> i.getType() == ConfigItemType.REQUIREMENT));
        assertTrue(items.stream().anyMatch(i -> i.getType() == ConfigItemType.DATA_MODEL_ERD));
        assertTrue(items.stream().anyMatch(i -> i.getType() == ConfigItemType.INFRASTRUCTURE_IAC));
    }
}