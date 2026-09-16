package io.onedev.server.web.page.admin.auditlog;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Date;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xhtmlrenderer.pdf.ITextRenderer;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.model.support.AuditEventType;
import io.onedev.server.util.GroovyUtils;

public class AuditReportPdfResource {

	public static byte[] generatePdf(List<AuditEvent> events,
			@Nullable Project project, boolean showProjectColumn,
			Date filterFrom, Date filterTo,
			@Nullable User filterActor,
			@Nullable AuditEventType filterType,
			@Nullable AuditEventSeverity filterSeverity,
			String filterText) {

		var bindings = new java.util.HashMap<String, Object>();
		bindings.put("events", events);
		bindings.put("project", project);
		bindings.put("showProjectColumn", showProjectColumn);
		bindings.put("filterFrom", filterFrom);
		bindings.put("filterTo", filterTo);
		bindings.put("filterActor", filterActor);
		bindings.put("filterType", filterType);
		bindings.put("filterSeverity", filterSeverity);
		bindings.put("filterText", filterText);
		bindings.put("barChartBase64", AuditReportChartGenerator.generateBarChartBase64(events, filterFrom, filterTo));
		bindings.put("pieChartBase64", AuditReportChartGenerator.generatePieChartBase64(events));
		bindings.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

		var template = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml">
				<head>
					<style>
						body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; margin: 20px; }
						h1 { color: #333; border-bottom: 2px solid #4a90d9; padding-bottom: 8px; font-size: 18pt; }
						h2 { color: #555; margin-top: 20px; font-size: 13pt; }
						.summary-table { width: 100%%; border-collapse: collapse; margin: 10px 0; }
						.summary-table td { padding: 6px 10px; border: 1px solid #ddd; }
						.summary-table td:first-child { font-weight: bold; width: 180px; background: #f8f9fa; }
						.events-table { width: 100%%; border-collapse: collapse; margin: 10px 0; font-size: 9pt; }
						.events-table th { background: #4a90d9; color: white; padding: 6px 8px; text-align: left; }
						.events-table td { padding: 5px 8px; border: 1px solid #ddd; }
						.events-table tr:nth-child(even) { background: #f8f9fa; }
						.chart-container { text-align: center; margin: 15px 0; }
						.chart-container img { max-width: 100%%; }
						.severity-WARNING { color: #e67e22; font-weight: bold; }
						.severity-ERROR { color: #e74c3c; font-weight: bold; }
						.severity-INFO { color: #3498db; }
						.footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #ddd; font-size: 8pt; color: #999; text-align: center; }
					</style>
				</head>
				<body>
					<h1>Audit Log Report</h1>

					<h2>Summary</h2>
					<table class="summary-table">
						<tr><td>Report Generated</td><td>${generatedAt}</td></tr>
						<% if (project != null) { %>
						<tr><td>Project</td><td>${project.path}</td></tr>
						<% } %>
						<tr><td>Period</td><td>${filterFrom} — ${filterTo}</td></tr>
						<tr><td>Total Events</td><td>${events.size()}</td></tr>
						<% if (filterActor != null) { %>
						<tr><td>Actor Filter</td><td>${filterActor.displayName}</td></tr>
						<% } %>
						<% if (filterType != null) { %>
						<tr><td>Action Filter</td><td>${filterType}</td></tr>
						<% } %>
						<% if (filterSeverity != null) { %>
						<tr><td>Severity Filter</td><td>${filterSeverity}</td></tr>
						<% } %>
						<% if (filterText != null && !filterText.isEmpty()) { %>
						<tr><td>Search Text</td><td>${filterText}</td></tr>
						<% } %>
					</table>

					<% if (barChartBase64 != null && !barChartBase64.isEmpty()) { %>
					<h2>Activity Over Time</h2>
					<div class="chart-container">
						<img src="data:image/png;base64,${barChartBase64}" />
					</div>
					<% } %>

					<% if (pieChartBase64 != null && !pieChartBase64.isEmpty()) { %>
					<h2>Events by Type</h2>
					<div class="chart-container">
						<img src="data:image/png;base64,${pieChartBase64}" />
					</div>
					<% } %>

					<% if (!events.isEmpty()) { %>
					<h2>Event Details</h2>
					<table class="events-table">
						<thead>
							<tr>
								<th>Date &amp; Time</th>
								<th>Severity</th>
								<th>Action</th>
								<th>Changed By</th>
								<th>IP Address</th>
								<% if (showProjectColumn) { %><th>Project</th><% } %>
								<th>Summary</th>
							</tr>
						</thead>
						<tbody>
							<% events.each { event -> %>
							<tr>
								<td>${event.date}</td>
								<td class="severity-${event.eventSeverity}">${event.eventSeverity}</td>
							<td>${event.eventType.name().replace('_', ' ').toLowerCase().capitalize()}</td>
							<td>${event.actorName ?: "System"}</td>
								<td>${event.ipAddress ?: "-"}</td>
								<% if (showProjectColumn) { %><td>${event.projectPath ?: "-"}</td><% } %>
								<td>${event.summary}</td>
							</tr>
							<% } %>
						</tbody>
					</table>
					<% } %>

					<div class="footer">
						Generated by OneDev Audit Log System
					</div>
				</body>
				</html>
				""";

		var html = GroovyUtils.evalTemplate(template, bindings);

		try {
			var os = new ByteArrayOutputStream();
			var renderer = new ITextRenderer();
			renderer.setDocumentFromString(html);
			renderer.layout();
			renderer.createPDF(os);
			return os.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Error generating PDF report", e);
		}
	}

}
