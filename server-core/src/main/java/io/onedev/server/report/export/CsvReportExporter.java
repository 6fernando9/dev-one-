package io.onedev.server.report.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import io.onedev.server.report.ReportResult;

/**
 * Exporta un ReportResult a CSV usando Apache Commons CSV.
 */
public class CsvReportExporter {

    public static byte[] export(ReportResult result) {
        var os = new ByteArrayOutputStream();

        try (var printer = new CSVPrinter(new OutputStreamWriter(os, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            // BOM for Excel UTF-8 compatibility
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            // Headers
            printer.printRecord(result.getHeaders());

            // Rows
            for (var row : result.getRows()) {
                printer.printRecord(row);
            }
            printer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error generando CSV", e);
        }

        return os.toByteArray();
    }
}
