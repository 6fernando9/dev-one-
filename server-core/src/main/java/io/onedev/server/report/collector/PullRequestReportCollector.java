package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

public class PullRequestReportCollector implements ReportDataCollector {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            Dao dao = OneDev.getInstance(Dao.class);
            EntityCriteria<PullRequest> criteria = EntityCriteria.of(PullRequest.class);
            criteria.add(org.hibernate.criterion.Restrictions.eq("targetProject", project));

            List<PullRequest> prs = dao.query(criteria, 0, request.getMaxResults());

            for (PullRequest pr : prs) {
                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "number": row.add("#" + pr.getNumber()); break;
                        case "title": row.add(pr.getTitle()); break;
                        case "status": row.add(pr.getStatus().toString()); break;
                        case "submitter": row.add(pr.getSubmitter() != null ? pr.getSubmitter().getDisplayName() : "-"); break;
                        case "targetBranch": row.add(pr.getTargetBranch()); break;
                        case "sourceBranch": row.add(pr.getSourceBranch()); break;
                        case "createDate": row.add(DATE_FMT.format(pr.getSubmitDate())); break;
                        case "updateDate": row.add(pr.getLastActivity() != null ? DATE_FMT.format(pr.getLastActivity().getDate()) : "-"); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.PULL_REQUESTS,
                "Error al obtener pull requests: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.PULL_REQUESTS, headers, rows);
    }
}
