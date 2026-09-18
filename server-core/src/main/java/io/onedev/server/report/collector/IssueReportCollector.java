package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.onedev.server.OneDev;
import io.onedev.server.model.Issue;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

public class IssueReportCollector implements ReportDataCollector {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            Dao dao = OneDev.getInstance(Dao.class);
            EntityCriteria<Issue> criteria = EntityCriteria.of(Issue.class);
            criteria.add(org.hibernate.criterion.Restrictions.eq("project", project));

            String stateFilter = request.getFilters().getOrDefault("state", null);
            if (stateFilter != null) {
                criteria.add(org.hibernate.criterion.Restrictions.eq("state", stateFilter));
            }

            List<Issue> issues = dao.query(criteria, 0, request.getMaxResults());

            for (Issue issue : issues) {
                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "number": row.add("#" + issue.getNumber()); break;
                        case "title": row.add(issue.getTitle()); break;
                        case "state": row.add(issue.getState()); break;
                        case "submitter": row.add(issue.getSubmitter() != null ? issue.getSubmitter().getDisplayName() : "-"); break;
                        case "assignees": row.add("-"); break;
                        case "createDate": row.add(DATE_FMT.format(issue.getSubmitDate())); break;
                        case "updateDate": row.add(issue.getLastActivity() != null ? DATE_FMT.format(issue.getLastActivity().getDate()) : "-"); break;
                        case "priority": row.add("-"); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.ISSUES,
                "Error al obtener issues: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.ISSUES, headers, rows);
    }
}
