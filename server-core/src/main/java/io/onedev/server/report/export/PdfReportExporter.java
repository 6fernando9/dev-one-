package io.onedev.server.report.export;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.xhtmlrenderer.pdf.ITextRenderer;

import io.onedev.server.report.ReportResult;
import io.onedev.server.util.GroovyUtils;

/**
 * Exporta un ReportResult a PDF usando Flying Saucer + OpenPDF.
 */
public class PdfReportExporter {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static byte[] export(ReportResult result) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("result", result);
        bindings.put("generatedAt", DATE_FMT.format(new Date()));

        String template = """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <style>
                    body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; margin: 20px; }
                    h1 { color: #333; border-bottom: 2px solid #4a90d9; padding-bottom: 8px; font-size: 16pt; }
                    h2 { color: #555; font-size: 12pt; }
                    .meta { color: #777; font-size: 9pt; margin-bottom: 15px; }
                    table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 9pt; }
                    th { background: #4a90d9; color: white; padding: 6px 8px; text-align: left; }
                    td { padding: 5px 8px; border: 1px solid #ddd; }
                    tr:nth-child(even) { background: #f8f9fa; }
                    .footer { margin-top: 20px; padding-top: 8px; border-top: 1px solid #ddd; font-size: 8pt; color: #999; text-align: center; }
                    .summary { background: #f0f4f8; padding: 10px; border-radius: 4px; margin: 10px 0; }
                </style>
            </head>
            <body>
                <h1>${result.title}</h1>
                <div class="meta">
                    Dominio: ${result.domain.displayName} | Generado: ${generatedAt} | Total filas: ${result.rowCount}
                </div>
                <% if (result.description) { %>
                <div class="summary">${result.description}</div>
                <% } %>

                <% if (!result.rows.isEmpty()) { %>
                <table>
                    <thead>
                        <tr>
                            <% result.headers.each { h -> %>
                            <th>${h}</th>
                            <% } %>
                        </tr>
                    </thead>
                    <tbody>
                        <% result.rows.each { row -> %>
                        <tr>
                            <% row.each { cell -> %>
                            <td>${cell ?: '-'}</td>
                            <% } %>
                        </tr>
                        <% } %>
                    </tbody>
                </table>
                <% } else { %>
                <p>No se encontraron datos con los filtros aplicados.</p>
                <% } %>

                <div class="footer">
                    Generado por OneDev - Sistema de Informes Dinámicos con IA
                </div>
            </body>
            </html>
            """;

        String html = GroovyUtils.evalTemplate(template, bindings);

        try {
            var os = new ByteArrayOutputStream();
            var renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage(), e);
        }
    }
}
