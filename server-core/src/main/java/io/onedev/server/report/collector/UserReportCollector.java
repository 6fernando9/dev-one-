package io.onedev.server.report.collector;

import java.util.ArrayList;
import java.util.List;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;

public class UserReportCollector implements ReportDataCollector {

    @Override
    public ReportResult collect(Project project, ReportRequest request) {
        List<String> headers = request.getEffectiveColumns();
        List<List<String>> rows = new ArrayList<>();

        try {
            Dao dao = OneDev.getInstance(Dao.class);
            List<User> users = dao.query(User.class);

            for (User user : users) {
                if (user.isSystem()) continue;

                List<String> row = new ArrayList<>();
                for (String col : headers) {
                    switch (col) {
                        case "name": row.add(user.getName()); break;
                        case "fullName": row.add(user.getFullName() != null ? user.getFullName() : "-"); break;
                        case "type": row.add(user.getType().name()); break;
                        case "email": row.add("-"); break;
                        default: row.add("-");
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            return new ReportResult(request.getTitle(), ReportDomain.USERS,
                "Error al obtener usuarios: " + e.getMessage());
        }

        return new ReportResult(request.getTitle(), request.getDescription(),
            ReportDomain.USERS, headers, rows);
    }
}
