package io.onedev.server.traceability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Clasifica automáticamente los archivos del repositorio e Issues de OneDev
 * en los 7 Elementos de Configuración (ConfigItems) según su estándar.
 */
@Singleton
public class ConfigItemClassifier {

    private static final Pattern ADR_PATTERN = Pattern.compile("^(?:docs/)?adr/ADR-?([0-9A-Za-z_-]+)\\.md$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADR_FALLBACK_PATTERN = Pattern.compile("^(?:docs/)?(?:adr|decisions)/(.+)\\.md$", Pattern.CASE_INSENSITIVE);

    private static final Pattern REQ_PATTERN = Pattern.compile("^(?:docs/)?requirements/RF-?([0-9A-Za-z_-]+)\\.md$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQ_CATALOG_PATTERN = Pattern.compile("^(?:docs/)?(?:requirements/)?requirements?\\.(json|ya?ml|md)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ARCH_PATTERN = Pattern.compile("^(?:docs/)?(?:architecture|arch)/.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_PATTERN = Pattern.compile("^wiki/.*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ERD_FOLDER_PATTERN = Pattern.compile("^(?:docs/)?database/.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERD_FILE_PATTERN = Pattern.compile("^(?:.*/)?(?:schema|database|db|migration|migrations)/.*\\.(sql|prisma|erd|dbm)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERD_EXT_PATTERN = Pattern.compile(".*\\.(erd|prisma|dbm)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERD_SQL_PATTERN = Pattern.compile(".*\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERD_MD_PATTERN = Pattern.compile(".*erd\\.md$", Pattern.CASE_INSENSITIVE);

    private static final Pattern IAC_PATTERN = Pattern.compile(
        "^(?:.*/)?(?:Dockerfile.*|docker-compose.*|\\.onedev-buildspec.*|.*\\.tf|.*\\.tfvars|k8s/.*|helm/.*|ansible/.*|deploy/.*)$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TEST_PATTERN = Pattern.compile(
        "^(?:src/test/.*|.*(?:Test|Tests|TestCase)\\.java|.*\\.(?:spec|test)\\.[jt]sx?|tests?/.*)$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SOURCE_CODE_PATTERN = Pattern.compile(
        "^(?:src/.*|.*\\.(java|js|jsx|ts|tsx|py|go|rs|c|cpp|h|hpp|cs|rb|php|kt|scala|swift|vue|html|css|scss|graphql|proto))$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RF_IN_TITLE_PATTERN = Pattern.compile("\\[?(RF-[0-9A-Za-z_-]+)\\]?", Pattern.CASE_INSENSITIVE);

    /**
     * Clasifica un archivo según su ruta y genera el ConfigItem correspondiente.
     */
    public ConfigItem classifyPath(String rawPath, @Nullable String hash) {
        String normalizedPath = normalizePath(rawPath);
        String fileName = getFileName(normalizedPath);

        // 1. ADR (Decisiones Técnicas)
        Matcher adrMatcher = ADR_PATTERN.matcher(normalizedPath);
        if (adrMatcher.matches()) {
            String idSuffix = adrMatcher.group(1);
            String id = "ADR-" + idSuffix.toUpperCase(Locale.ROOT);
            String title = "Decisión Técnica: " + formatTitle(idSuffix);
            return new ConfigItem(ConfigItemType.ADR, id, normalizedPath, title, hash);
        }
        Matcher adrFallback = ADR_FALLBACK_PATTERN.matcher(normalizedPath);
        if (adrFallback.matches()) {
            String name = adrFallback.group(1);
            String id = "ADR-" + name.toUpperCase(Locale.ROOT);
            return new ConfigItem(ConfigItemType.ADR, id, normalizedPath, "Decisión Técnica: " + formatTitle(name), hash);
        }

        // 2. Requisitos Funcionales (RTM)
        Matcher reqMatcher = REQ_PATTERN.matcher(normalizedPath);
        if (reqMatcher.matches()) {
            String idSuffix = reqMatcher.group(1);
            String id = "RF-" + idSuffix.toUpperCase(Locale.ROOT);
            String title = "Requisito Funcional: " + formatTitle(idSuffix);
            return new ConfigItem(ConfigItemType.REQUIREMENT, id, normalizedPath, title, hash);
        }
        if (REQ_CATALOG_PATTERN.matcher(normalizedPath).matches()) {
            return new ConfigItem(ConfigItemType.REQUIREMENT, "RTM-CATALOG", normalizedPath, "Catálogo de Requisitos del Sistema", hash);
        }
        if (normalizedPath.startsWith("docs/requirements/") || normalizedPath.startsWith("requirements/")) {
            String id = stripExtension(fileName).toUpperCase(Locale.ROOT);
            if (!id.startsWith("RF-") && !id.startsWith("RTM")) {
                id = "REQ:" + id;
            }
            return new ConfigItem(ConfigItemType.REQUIREMENT, id, normalizedPath, "Requisito: " + formatTitle(stripExtension(fileName)), hash);
        }

        // 3. Modelos de Datos (ERD / Esquemas / SQL)
        if (ERD_FOLDER_PATTERN.matcher(normalizedPath).matches() 
                || ERD_FILE_PATTERN.matcher(normalizedPath).matches()
                || ERD_EXT_PATTERN.matcher(normalizedPath).matches()
                || ERD_SQL_PATTERN.matcher(normalizedPath).matches()
                || ERD_MD_PATTERN.matcher(normalizedPath).matches()) {
            String id = "ERD:" + stripExtension(fileName);
            String title = "Modelo de Datos: " + formatTitle(stripExtension(fileName));
            return new ConfigItem(ConfigItemType.DATA_MODEL_ERD, id, normalizedPath, title, hash);
        }

        // 4. Infraestructura como Código (IaC)
        if (IAC_PATTERN.matcher(normalizedPath).matches()) {
            String id = "IAC:" + fileName;
            String title = "Infraestructura: " + fileName;
            return new ConfigItem(ConfigItemType.INFRASTRUCTURE_IAC, id, normalizedPath, title, hash);
        }

        // 5. Pruebas y Tests (TEST_SPEC)
        if (TEST_PATTERN.matcher(normalizedPath).matches()) {
            String id = "TEST:" + stripExtension(fileName);
            String title = "Prueba: " + formatTitle(stripExtension(fileName));
            return new ConfigItem(ConfigItemType.TEST_SPEC, id, normalizedPath, title, hash);
        }

        // 6. Documentación de Arquitectura (Wiki)
        if (ARCH_PATTERN.matcher(normalizedPath).matches() || WIKI_PATTERN.matcher(normalizedPath).matches()) {
            String id = "WIKI:" + stripExtension(fileName);
            String title = "Arquitectura: " + formatTitle(stripExtension(fileName));
            return new ConfigItem(ConfigItemType.ARCHITECTURE_WIKI, id, normalizedPath, title, hash);
        }

        // 7. Código Fuente
        if (SOURCE_CODE_PATTERN.matcher(normalizedPath).matches()) {
            String id = "SRC:" + stripExtension(fileName);
            String title = "Código Fuente: " + fileName;
            return new ConfigItem(ConfigItemType.SOURCE_CODE, id, normalizedPath, title, hash);
        }

        // Fallback: Si es documento, tratar como wiki/arquitectura, de lo contrario recurso de código
        if (normalizedPath.endsWith(".md") || normalizedPath.endsWith(".txt") || normalizedPath.endsWith(".pdf") || normalizedPath.endsWith(".png") || normalizedPath.endsWith(".jpg")) {
            return new ConfigItem(ConfigItemType.ARCHITECTURE_WIKI, "DOC:" + stripExtension(fileName), normalizedPath, "Documento: " + fileName, hash);
        }

        return new ConfigItem(ConfigItemType.SOURCE_CODE, "SRC:" + fileName, normalizedPath, "Recurso: " + fileName, hash);
    }

    public ConfigItem classifyPath(String rawPath) {
        return classifyPath(rawPath, null);
    }

    /**
     * Clasifica un Issue de OneDev. Si contiene mención o etiqueta de Requisito,
     * se asocia a REQUIREMENT (soporte híbrido); de lo contrario es PROJECT_TASK.
     */
    public ConfigItem classifyIssue(Long issueNumber, String issueTitle, @Nullable Collection<String> labels) {
        boolean isRequirement = false;
        String rfId = null;

        if (labels != null) {
            for (String label : labels) {
                if ("requirement".equalsIgnoreCase(label) || "requisito".equalsIgnoreCase(label) || "rtm".equalsIgnoreCase(label)) {
                    isRequirement = true;
                    break;
                }
            }
        }

        Matcher m = RF_IN_TITLE_PATTERN.matcher(issueTitle);
        if (m.find()) {
            isRequirement = true;
            rfId = m.group(1).toUpperCase(Locale.ROOT);
        }

        String path = "issue:#" + issueNumber;

        if (isRequirement) {
            String id = rfId != null ? rfId : "RF-ISSUE-" + issueNumber;
            return new ConfigItem(ConfigItemType.REQUIREMENT, id, path, issueTitle, null);
        } else {
            String id = "#" + issueNumber;
            return new ConfigItem(ConfigItemType.PROJECT_TASK, id, path, issueTitle, null);
        }
    }

    /**
     * Clasifica un lote de rutas de archivos.
     */
    public List<ConfigItem> classifyPaths(Collection<String> paths) {
        Set<ConfigItem> items = new LinkedHashSet<ConfigItem>();
        for (String path : paths) {
            if (path != null && !path.trim().isEmpty()) {
                items.add(classifyPath(path));
            }
        }
        return new ArrayList<ConfigItem>(items);
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("^/+", "").trim();
    }

    private String getFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String formatTitle(String raw) {
        return raw.replace('-', ' ').replace('_', ' ').trim();
    }
}
