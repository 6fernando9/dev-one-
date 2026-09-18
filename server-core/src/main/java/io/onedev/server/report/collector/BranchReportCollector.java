package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.service.ProjectService;

public class BranchReportCollector implements ReportDataCollector {

    private static final Logger logger = LoggerFactory.getLogger(BranchReportCollector.class);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        ProjectService projectService = OneDev.getInstance(ProjectService.class);
        try (Repository repo = projectService.getRepository(project.getId())) {
            for (Ref ref : repo.getRefDatabase().getRefsByPrefix("refs/heads/")) {
                String branchName = ref.getName().replace("refs/heads/", "");
                ObjectId commitId = ref.getObjectId();

                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "name":
                            row.add(branchName);
                            break;
                        case "lastCommitHash":
                            row.add(commitId != null ? commitId.abbreviate(8).name() : "-");
                            break;
                        case "lastCommitAuthor":
                        case "lastCommitDate":
                        case "lastCommitMessage":
                            if (commitId != null) {
                                try (RevWalk walk = new RevWalk(repo)) {
                                    RevCommit commit = walk.parseCommit(commitId);
                                    if (col.equals("lastCommitAuthor")) {
                                        row.add(commit.getAuthorIdent().getName());
                                    } else if (col.equals("lastCommitDate")) {
                                        row.add(DATE_FMT.format(new Date(commit.getCommitTime() * 1000L)));
                                    } else {
                                        row.add(commit.getShortMessage());
                                    }
                                }
                            } else {
                                row.add("-");
                            }
                            break;
                        default:
                            row.add("-");
                    }
                }
                rows.add(row);

                if (rows.size() >= request.getMaxResults()) break;
            }
        } catch (Exception e) {
            logger.error("Error collecting branch data", e);
            return new ReportResult(request.getTitle(), ReportDomain.BRANCHES,
                "Error al obtener datos de ramas: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.BRANCHES, headers, rows);
    }
}
