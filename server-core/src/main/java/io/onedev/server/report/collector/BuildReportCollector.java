package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.Build;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

public class BuildReportCollector implements ReportDataCollector {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            Dao dao = OneDev.getInstance(Dao.class);
            EntityCriteria<Build> criteria = EntityCriteria.of(Build.class);
            criteria.add(org.hibernate.criterion.Restrictions.eq("project", project));
            criteria.addOrder(org.hibernate.criterion.Order.desc("submitDate"));

            List<Build> builds = dao.query(criteria, 0, request.getMaxResults());

            for (Build build : builds) {
                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "number": row.add("#" + build.getNumber()); break;
                        case "jobName": row.add(build.getJobName()); break;
                        case "status": row.add(build.getStatus() != null ? build.getStatus().toString() : "-"); break;
                        case "submitDate": row.add(DATE_FMT.format(build.getSubmitDate())); break;
                        case "finishDate": row.add(build.getFinishDate() != null ? DATE_FMT.format(build.getFinishDate()) : "En progreso"); break;
                        case "refName": row.add(build.getRefName() != null ? build.getRefName() : "-"); break;
                        case "version": row.add(build.getVersion() != null ? build.getVersion() : "-"); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.BUILDS,
                "Error al obtener builds: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.BUILDS, headers, rows);
    }
}
