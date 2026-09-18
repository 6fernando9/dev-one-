package io.onedev.server.report.collector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
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

public class CommitReportCollector implements ReportDataCollector {

    private static final Logger logger = LoggerFactory.getLogger(CommitReportCollector.class);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();
        Map<String, String> filters = request.getFilters();

        ProjectService projectService = OneDev.getInstance(ProjectService.class);
        try (Repository repo = projectService.getRepository(project.getId())) {
            ObjectId headId = repo.resolve(Constants.HEAD);
            if (headId == null) {
                return new ReportResult(request.getTitle(), ReportDomain.COMMITS,
                    "No se encontró HEAD en el repositorio");
            }

            String authorFilter = filters.getOrDefault("author", null);
            long sinceMs = 0;
            String sinceStr = filters.getOrDefault("since", null);
            if (sinceStr != null) {
                sinceMs = parseSinceFilter(sinceStr);
            }

            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(headId));
                int count = 0;
                for (RevCommit commit : walk) {
                    long commitTime = commit.getCommitTime() * 1000L;
                    if (sinceMs > 0 && commitTime < sinceMs) break;

                    String author = commit.getAuthorIdent().getName();
                    if (authorFilter != null && !author.toLowerCase().contains(authorFilter.toLowerCase())) {
                        continue;
                    }

                    List<String> row = new ArrayList<>();
                    for (String col : headers) {
                        switch (col) {
                            case "hash": row.add(commit.abbreviate(8).name()); break;
                            case "author": row.add(author); break;
                            case "date": row.add(DATE_FMT.format(new Date(commitTime))); break;
                            case "message": row.add(commit.getShortMessage()); break;
                            case "filesChanged": row.add(String.valueOf(commit.getParentCount() > 0 ? "~" : "init")); break;
                            default: row.add("-");
                        }
                    }
                    rows.add(row);

                    if (++count >= request.getMaxResults()) break;
                }
            }
        } catch (Exception e) {
            logger.error("Error collecting commit data", e);
            return new ReportResult(request.getTitle(), ReportDomain.COMMITS,
                "Error al obtener datos de commits: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.COMMITS, headers, rows);
    }

    private long parseSinceFilter(String since) {
        long now = System.currentTimeMillis();
        String s = since.toLowerCase().trim();
        if (s.contains("day")) {
            int n = extractNumber(s, 7);
            return now - (n * 24L * 60 * 60 * 1000);
        } else if (s.contains("week")) {
            int n = extractNumber(s, 1);
            return now - (n * 7L * 24 * 60 * 60 * 1000);
        } else if (s.contains("month")) {
            int n = extractNumber(s, 1);
            return now - (n * 30L * 24 * 60 * 60 * 1000);
        }
        return 0;
    }

    private int extractNumber(String s, int defaultVal) {
        try {
            String num = s.replaceAll("[^0-9]", "");
            return num.isEmpty() ? defaultVal : Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
