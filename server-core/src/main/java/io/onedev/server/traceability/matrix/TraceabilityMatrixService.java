package io.onedev.server.traceability.matrix;

import java.util.List;
import org.jspecify.annotations.Nullable;

import io.onedev.server.model.Project;
import io.onedev.server.traceability.ConfigItem;

/**
 * Servicio encargado del Requisito Funcional 4 (RF4):
 * Construcción de la Matriz de Trazabilidad Dinámica y Bidireccional,
 * análisis de relaciones (Forward y Backward Traceability) y exportación de informes.
 */
public interface TraceabilityMatrixService {

    /**
     * Construye la matriz de trazabilidad completa para un proyecto en una revisión dada (rama o commit).
     *
     * @param project Proyecto sobre el que se genera la matriz
     * @param revision Rama o commit (null para la rama por defecto)
     * @return Matriz consolidada con métricas y filas
     */
    TraceabilityMatrix buildMatrix(Project project, @Nullable String revision);

    /**
     * Trazabilidad hacia adelante (Forward Traceability):
     * Dado un Requisito o ADR, descubre todos los elementos que lo implementan o sustentan.
     *
     * @param project Proyecto a consultar
     * @param itemIdentifier Identificador del elemento (ej. "RF-01", "ADR-0001")
     * @param revision Rama o commit
     * @return Lista de ConfigItems dependientes
     */
    List<ConfigItem> getForwardTrace(Project project, String itemIdentifier, @Nullable String revision);

    /**
     * Trazabilidad hacia atrás (Backward Traceability):
     * Dado un archivo de código fuente, migración o tarea, descubre el Requisito o ADR que lo originó.
     *
     * @param project Proyecto a consultar
     * @param itemIdentifierOrPath ID o ruta del archivo (ej. "src/main/Auth.java", "#42")
     * @param revision Rama o commit
     * @return Lista de ConfigItems de origen
     */
    List<ConfigItem> getBackwardTrace(Project project, String itemIdentifierOrPath, @Nullable String revision);

    /**
     * Exporta la matriz a formato CSV para análisis en hojas de cálculo o auditoría.
     */
    String exportToCsv(TraceabilityMatrix matrix);

    /**
     * Exporta la matriz a formato Markdown estructurado con tablas y métricas.
     */
    String exportToMarkdown(TraceabilityMatrix matrix);

    /**
     * Exporta la matriz a formato JSON completo.
     */
    String exportToJson(TraceabilityMatrix matrix);

}