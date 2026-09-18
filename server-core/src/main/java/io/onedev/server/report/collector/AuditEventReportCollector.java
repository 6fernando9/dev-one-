package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

public class AuditEventReportCollector implements ReportDataCollector {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            Dao dao = OneDev.getInstance(Dao.class);
            EntityCriteria<AuditEvent> criteria = EntityCriteria.of(AuditEvent.class);
            criteria.addOrder(org.hibernate.criterion.Order.desc("date"));

            List<AuditEvent> events = dao.query(criteria, 0, request.getMaxResults());

            for (AuditEvent event : events) {
                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "date": row.add(DATE_FMT.format(event.getDate())); break;
                        case "severity": row.add(event.getEventSeverity() != null ? event.getEventSeverity().name() : "-"); break;
                        case "action": row.add(event.getEventType() != null ? event.getEventType().name() : "-"); break;
                        case "actor": row.add(event.getActorName() != null ? event.getActorName() : "Sistema"); break;
                        case "ipAddress": row.add(event.getIpAddress() != null ? event.getIpAddress() : "-"); break;
                        case "project": row.add(event.getProjectPath() != null ? event.getProjectPath() : "-"); break;
                        case "summary": row.add(event.getSummary() != null ? event.getSummary() : "-"); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.AUDIT_EVENTS,
                "Error al obtener eventos de auditoría: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.AUDIT_EVENTS, headers, rows);
    }
}
