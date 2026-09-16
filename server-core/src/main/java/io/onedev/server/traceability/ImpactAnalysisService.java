package io.onedev.server.traceability;

import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * Servicio encargado del análisis de impacto de cambios en el código sobre los
 * 7 Elementos de Configuración mediante Inteligencia Artificial (con fallback heurístico).
 */
public interface ImpactAnalysisService {

    /**
     * Analiza un commit dado su hash, mensaje, autor, archivos modificados y diff opcional.
     */
    ChangeImpactAnalysis analyze(String commitHash, String commitMessage, String authorName, 
                                Collection<String> changedFiles, @Nullable String diffContent);

    /**
     * Detecta la categoría semántica del cambio (FEATURE, FIX, REFACTOR, etc.).
     */
    ChangeTypeCategory detectCategory(String commitMessage, Collection<String> changedFiles);
}