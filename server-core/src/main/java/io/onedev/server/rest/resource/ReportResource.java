package io.onedev.server.rest.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.shiro.authz.UnauthorizedException;
import org.jspecify.annotations.Nullable;

import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.report.ReportService;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.ProjectService;

/**
 * Endpoints REST para consulta y exportación de Informes Dinámicos con Lenguaje Natural (RF6).
 */
@Path("/report")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class ReportResource {

    private final ReportService reportService;
    private final ProjectService projectService;

    @Inject
    public ReportResource(ReportService reportService, ProjectService projectService) {
        this.reportService = reportService;
        this.projectService = projectService;
    }

    @Api(order=100, description="Execute natural language report query and return JSON result")
    @Path("/query/{projectId}")
    @POST
    public Response queryReport(@PathParam("projectId") Long projectId, String naturalLanguageQuery) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canAccessProject(project)) {
            throw new UnauthorizedException();
        }

        ReportRequest request = reportService.translateQuery(project, naturalLanguageQuery);
        ReportResult result = reportService.collectData(project, request);
        return Response.ok(result).build();
    }

    @Api(order=200, description="Export report as downloadable file (CSV or PDF)")
    @Path("/export/{projectId}")
    @GET
    public Response exportReport(
            @PathParam("projectId") Long projectId,
            @QueryParam("query") @Nullable String query,
            @QueryParam("domain") @Nullable String domain,
            @QueryParam("format") @DefaultValue("csv") String format) {

        Project project = projectService.load(projectId);
        if (!SecurityUtils.canAccessProject(project)) {
            throw new UnauthorizedException();
        }

        ReportRequest request;
        if (query != null && !query.trim().isEmpty()) {
            request = reportService.translateQuery(project, query.trim());
        } else {
            request = new ReportRequest();
            if (domain != null) {
                request.setDomain(ReportDomain.fromString(domain));
            } else {
                request.setDomain(ReportDomain.COMMITS);
            }
            request.setTitle("Informe de " + request.getDomain().getDisplayName());
        }

        if ("pdf".equalsIgnoreCase(format)) {
            request.setFormat(ReportRequest.ExportFormat.PDF);
        } else {
            request.setFormat(ReportRequest.ExportFormat.CSV);
        }

        ReportResult result = reportService.collectData(project, request);
        byte[] bytes = reportService.exportReport(result, request);

        String ext = request.getFormat() == ReportRequest.ExportFormat.PDF ? "pdf" : "csv";
        String mimeType = request.getFormat() == ReportRequest.ExportFormat.PDF ? "application/pdf" : "text/csv; charset=UTF-8";
        String filename = "informe-" + project.getName() + "-" + request.getDomain().name().toLowerCase() + "." + ext;

        return Response.ok(bytes, mimeType)
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }
}
